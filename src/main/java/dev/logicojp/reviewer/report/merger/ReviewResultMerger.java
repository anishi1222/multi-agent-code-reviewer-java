package dev.logicojp.reviewer.report.merger;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.finding.AggregatedFinding;
import dev.logicojp.reviewer.report.finding.ReviewFindingParser;
import dev.logicojp.reviewer.report.finding.ReviewFindingSimilarity;
import dev.logicojp.reviewer.report.formatter.ReviewMergedContentFormatter;

import dev.logicojp.reviewer.agent.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Merges multiple review results from the same agent (multi-pass reviews)
/// into a single consolidated `ReviewResult`.
///
/// When an agent performs multiple review passes, each pass may discover
/// overlapping findings. This merger consolidates identical findings and
/// emits a deduplicated per-agent report.
public final class ReviewResultMerger {

    @FunctionalInterface
    interface FindingBlockExtractor {
        List<ReviewFindingParser.FindingBlock> extract(String content);
    }

    @FunctionalInterface
    interface FindingKeyResolver {
        String resolve(ReviewFindingParser.FindingBlock block);
    }

    @FunctionalInterface
    interface MergedContentFormatter {
        String format(Map<String, AggregatedFinding> aggregatedFindings, int totalPasses, int failedPasses);
    }

    private static final Logger logger = LoggerFactory.getLogger(ReviewResultMerger.class);

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
        return mergeByAgent(
            results,
            ReviewFindingParser::extractFindingBlocks,
            ReviewFindingParser::findingKey,
            ReviewMergedContentFormatter::format
        );
    }

    static List<ReviewResult> mergeByAgent(List<ReviewResult> results,
                                           FindingBlockExtractor findingBlockExtractor,
                                           FindingKeyResolver findingKeyResolver,
                                           MergedContentFormatter mergedContentFormatter) {
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
                merged.add(mergeAgentResults(
                    agentResults,
                    findingBlockExtractor,
                    findingKeyResolver,
                    mergedContentFormatter
                ));
            }
        }

        return merged;
    }

    /// Merges multiple results from the same agent into a single result.
    private static ReviewResult mergeAgentResults(List<ReviewResult> agentResults,
                                                  FindingBlockExtractor findingBlockExtractor,
                                                  FindingKeyResolver findingKeyResolver,
                                                  MergedContentFormatter mergedContentFormatter) {
        // Use the config from the first result
        AgentConfig config = agentResults.getFirst().agentConfig();
        String repository = agentResults.getFirst().repository();

        List<ReviewResult> successful = agentResults.stream()
            .filter(ReviewResult::success)
            .toList();

        if (successful.isEmpty()) {
            // All passes failed — return the last failure
            logger.warn("Agent {}: all {} passes failed", config.name(), agentResults.size());
            return agentResults.getLast();
        }

        logger.info("Agent {}: merging {} successful pass(es) out of {} total",
            config.name(), successful.size(), agentResults.size());

        Map<String, AggregatedFinding> aggregatedFindings = new LinkedHashMap<>();
        Map<String, Set<String>> findingKeysByPriority = new LinkedHashMap<>();
        Set<String> fallbackPassContents = new LinkedHashSet<>();

        for (int i = 0; i < successful.size(); i++) {
            ReviewResult result = successful.get(i);
            int passNumber = i + 1;
            String content = result.content();
            if (content == null || content.isBlank()) {
                continue;
            }

            List<ReviewFindingParser.FindingBlock> blocks = findingBlockExtractor.extract(content);
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

            for (ReviewFindingParser.FindingBlock block : blocks) {
                AggregatedFinding.NormalizedFinding normalized = AggregatedFinding.normalize(block);
                String key = findingKeyResolver.resolve(block);
                AggregatedFinding existingExact = aggregatedFindings.get(key);
                if (existingExact != null) {
                    aggregatedFindings.put(key, existingExact.withPass(passNumber));
                    continue;
                }

                String nearDuplicateKey = findNearDuplicateKey(aggregatedFindings, findingKeysByPriority, normalized);
                if (nearDuplicateKey != null) {
                    AggregatedFinding nearExisting = aggregatedFindings.get(nearDuplicateKey);
                    aggregatedFindings.put(nearDuplicateKey, nearExisting.withPass(passNumber));
                    continue;
                }

                aggregatedFindings.put(key, AggregatedFinding.fromNormalized(block, normalized, passNumber));
                indexByPriority(findingKeysByPriority, normalized.priority(), key);
            }
        }

        int failedCount = agentResults.size() - successful.size();
        String content = mergedContentFormatter.format(aggregatedFindings, agentResults.size(), failedCount);

        return ReviewResult.builder()
            .agentConfig(config)
            .repository(repository)
            .content(content)
            .success(true)
            .timestamp(Instant.now())
            .build();
    }

    private static String findNearDuplicateKey(Map<String, AggregatedFinding> existing,
                                               Map<String, Set<String>> findingKeysByPriority,
                                               AggregatedFinding.NormalizedFinding incoming) {
        // Use index for both priority-specified and priority-blank findings
        String priorityKey = incoming.priority().isBlank() ? "" : incoming.priority();
        Set<String> keys = findingKeysByPriority.getOrDefault(priorityKey, Set.of());
        for (String key : keys) {
            AggregatedFinding candidate = existing.get(key);
            if (candidate != null && candidate.isNearDuplicateOf(incoming)) {
                return key;
            }
        }

        return null;
    }

    private static void indexByPriority(Map<String, Set<String>> findingKeysByPriority,
                                        String priority,
                                        String key) {
        // Index all findings including those with blank priority (keyed as "")
        String indexKey = (priority == null || priority.isBlank()) ? "" : priority;
        findingKeysByPriority.computeIfAbsent(indexKey, _ -> new LinkedHashSet<>()).add(key);
    }

    private static String normalizeText(String value) {
        return ReviewFindingSimilarity.normalizeText(value);
    }

}
