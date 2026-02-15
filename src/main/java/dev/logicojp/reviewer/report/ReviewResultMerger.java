package dev.logicojp.reviewer.report;

import dev.logicojp.reviewer.agent.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Merges multiple review results from the same agent (multi-pass reviews)
/// into a single consolidated `ReviewResult`.
///
/// When an agent performs multiple review passes, each pass may discover
/// overlapping findings. This merger consolidates identical findings and
/// emits a deduplicated per-agent report.
public final class ReviewResultMerger {

    private static final Logger logger = LoggerFactory.getLogger(ReviewResultMerger.class);
    private static final Pattern FINDING_HEADER = Pattern.compile("(?m)^###\\s+(\\d+)\\.\\s+(.+?)\\s*$");
    private static final Pattern TABLE_ROW_TEMPLATE = Pattern.compile(
        "(?m)^\\|\\s*\\*\\*%s\\*\\*\\s*\\|\\s*(.*?)\\s*\\|\\s*$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Map<String, Pattern> TABLE_VALUE_PATTERNS = new ConcurrentHashMap<>();

    private ReviewResultMerger() {
        // utility class
    }

    /// Merges a flat list of review results (potentially multiple per agent)
    /// into a list with exactly one result per agent.
    ///
    /// Results are grouped by agent name. If an agent has only one result,
    /// it is returned as-is. If an agent has multiple results, the successful
    /// ones are aggregated into unique findings.
    ///
    /// @param results flat list of all review results (may contain duplicates per agent)
    /// @return merged list with one result per agent (order preserved)
    public static List<ReviewResult> mergeByAgent(List<ReviewResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        // Group results by agent name, preserving insertion order
        Map<String, List<ReviewResult>> byAgent = new LinkedHashMap<>();
        for (ReviewResult result : results) {
            String agentName = result.agentConfig() != null
                ? result.agentConfig().name()
                : "__unknown__";
            byAgent.computeIfAbsent(agentName, _ -> new ArrayList<>()).add(result);
        }

        List<ReviewResult> merged = new ArrayList<>(byAgent.size());
        for (var entry : byAgent.entrySet()) {
            List<ReviewResult> agentResults = entry.getValue();
            if (agentResults.size() == 1) {
                merged.add(agentResults.getFirst());
            } else {
                merged.add(mergeAgentResults(agentResults));
            }
        }

        return merged;
    }

    /// Merges multiple results from the same agent into a single result.
    private static ReviewResult mergeAgentResults(List<ReviewResult> agentResults) {
        // Use the config from the first result
        AgentConfig config = agentResults.getFirst().agentConfig();
        String repository = agentResults.getFirst().repository();

        List<ReviewResult> successful = agentResults.stream()
            .filter(ReviewResult::isSuccess)
            .toList();

        if (successful.isEmpty()) {
            // All passes failed — return the last failure
            logger.warn("Agent {}: all {} passes failed", config.name(), agentResults.size());
            return agentResults.getLast();
        }

        logger.info("Agent {}: merging {} successful pass(es) out of {} total",
            config.name(), successful.size(), agentResults.size());

        var contentBuilder = new StringBuilder();
        Map<String, AggregatedFinding> aggregatedFindings = new LinkedHashMap<>();
        Set<String> fallbackPassContents = new LinkedHashSet<>();

        for (int i = 0; i < successful.size(); i++) {
            ReviewResult result = successful.get(i);
            int passNumber = i + 1;
            String content = result.content();
            if (content == null || content.isBlank()) {
                continue;
            }

            List<FindingBlock> blocks = extractFindingBlocks(content);
            if (blocks.isEmpty()) {
                String normalized = normalizeText(content);
                if (!normalized.isEmpty() && fallbackPassContents.add(normalized)) {
                    aggregatedFindings.putIfAbsent(
                        "fallback|" + normalized,
                        AggregatedFinding.fallback(content, passNumber)
                    );
                }
                continue;
            }

            for (FindingBlock block : blocks) {
                String key = findingKey(block);
                aggregatedFindings.compute(
                    key,
                    (_, existing) -> existing == null
                        ? AggregatedFinding.from(block, passNumber)
                        : existing.withPass(passNumber)
                );
            }
        }

        if (aggregatedFindings.isEmpty()) {
            contentBuilder.append("指摘事項なし");
        } else {
            int index = 1;
            int totalFindings = aggregatedFindings.size();
            for (AggregatedFinding finding : aggregatedFindings.values()) {
                contentBuilder.append("### ").append(index).append(". ").append(finding.title()).append("\n\n");
                if (finding.passNumbers().size() > 1) {
                    contentBuilder.append("> 検出パス: ")
                        .append(formatPassNumbers(finding.passNumbers()))
                        .append("\n\n");
                }
                contentBuilder.append(finding.body().trim());
                if (index < totalFindings) {
                    contentBuilder.append("\n\n---\n\n");
                }
                index++;
            }
        }

        // If some passes failed, append a note
        int failedCount = agentResults.size() - successful.size();
        if (failedCount > 0) {
            contentBuilder.append("\n\n---\n\n> **注記**: %d パス中 %d パスが失敗しました。上記は成功したパスの結果のみです。\n"
                .formatted(agentResults.size(), failedCount));
        }

        return ReviewResult.builder()
            .agentConfig(config)
            .repository(repository)
            .content(contentBuilder.toString())
            .success(true)
            .timestamp(LocalDateTime.now())
            .build();
    }

    private static List<FindingBlock> extractFindingBlocks(String content) {
        Matcher matcher = FINDING_HEADER.matcher(content);
        List<HeaderMatch> headers = new ArrayList<>();
        while (matcher.find()) {
            headers.add(new HeaderMatch(matcher.start(), matcher.end(), matcher.group(2).trim()));
        }

        if (headers.isEmpty()) {
            return List.of();
        }

        List<FindingBlock> blocks = new ArrayList<>(headers.size());
        for (int i = 0; i < headers.size(); i++) {
            HeaderMatch current = headers.get(i);
            int bodyEnd = i + 1 < headers.size() ? headers.get(i + 1).startIndex() : content.length();
            String body = content.substring(current.endIndex(), bodyEnd).trim();
            if (!body.isEmpty()) {
                blocks.add(new FindingBlock(current.title(), body));
            }
        }
        return blocks;
    }

    private static String findingKey(FindingBlock block) {
        String priority = extractTableValue(block.body(), "Priority");
        String summary = extractTableValue(block.body(), "指摘の概要");
        String location = extractTableValue(block.body(), "該当箇所");

        String titlePart = normalizeText(block.title());
        String priorityPart = normalizeText(priority);
        String summaryPart = normalizeText(summary);
        String locationPart = normalizeText(location);

        if (!titlePart.isEmpty() && (!summaryPart.isEmpty() || !locationPart.isEmpty() || !priorityPart.isEmpty())) {
            return String.join("|", titlePart, priorityPart, locationPart, summaryPart);
        }

        return "raw|" + normalizeText(block.body());
    }

    private static String extractTableValue(String body, String key) {
        Pattern pattern = TABLE_VALUE_PATTERNS.computeIfAbsent(
            key,
            k -> Pattern.compile(TABLE_ROW_TEMPLATE.pattern().formatted(Pattern.quote(k)))
        );
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String formatPassNumbers(Set<Integer> passNumbers) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Integer passNumber : passNumbers) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(passNumber);
            i++;
        }
        return sb.toString();
    }

    private static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value
            .toLowerCase(Locale.ROOT)
            .replace("`", "")
            .replace("*", "")
            .replace("_", "")
            .trim();
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ");
        return normalized;
    }

    private record HeaderMatch(int startIndex, int endIndex, String title) {
    }

    private record FindingBlock(String title, String body) {
    }

    private record AggregatedFinding(String title, String body, Set<Integer> passNumbers) {

        private AggregatedFinding {
            Objects.requireNonNull(title);
            Objects.requireNonNull(body);
            passNumbers = new LinkedHashSet<>(passNumbers);
        }

        static AggregatedFinding from(FindingBlock block, int passNumber) {
            return new AggregatedFinding(
                block.title(),
                block.body(),
                new LinkedHashSet<>(Set.of(passNumber))
            );
        }

        static AggregatedFinding fallback(String rawContent, int passNumber) {
            return new AggregatedFinding(
                "レビュー結果",
                rawContent,
                new LinkedHashSet<>(Set.of(passNumber))
            );
        }

        AggregatedFinding withPass(int passNumber) {
            LinkedHashSet<Integer> updated = new LinkedHashSet<>(passNumbers);
            updated.add(passNumber);
            return new AggregatedFinding(title, body, updated);
        }
    }
}
