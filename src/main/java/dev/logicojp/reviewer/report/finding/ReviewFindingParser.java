package dev.logicojp.reviewer.report.finding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReviewFindingParser {

    private static final Pattern FINDING_HEADER = Pattern.compile("(?m)^###\\s+(\\d+)\\.\\s+(.+?)\\s*$");
    private static final Pattern TABLE_ROW_TEMPLATE = Pattern.compile(
        "(?m)^\\|\\s*\\*\\*%s\\*\\*\\s*\\|\\s*(.*?)\\s*\\|\\s*$");
    private static final Map<String, Pattern> TABLE_VALUE_PATTERNS = new ConcurrentHashMap<>();

    private ReviewFindingParser() {
    }

    public static List<FindingBlock> extractFindingBlocks(String content) {
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

    public static String findingKey(FindingBlock block) {
        String priority = extractTableValue(block.body(), "Priority");
        String summary = extractTableValue(block.body(), "指摘の概要");
        String location = extractTableValue(block.body(), "該当箇所");

        String titlePart = ReviewFindingSimilarity.normalizeText(block.title());
        String priorityPart = ReviewFindingSimilarity.normalizeText(priority);
        String summaryPart = ReviewFindingSimilarity.normalizeText(summary);
        String locationPart = ReviewFindingSimilarity.normalizeText(location);

        if (!titlePart.isEmpty() && (!summaryPart.isEmpty() || !locationPart.isEmpty() || !priorityPart.isEmpty())) {
            return String.join("|", titlePart, priorityPart, locationPart, summaryPart);
        }

        return "raw|" + ReviewFindingSimilarity.normalizeText(block.body());
    }

    /// Derives a finding key from an already-computed NormalizedFinding,
    /// avoiding redundant extractTableValue and normalizeText calls.
     static String findingKeyFromNormalized(AggregatedFinding.NormalizedFinding normalized,
                                            String rawBody) {
        if (!normalized.title().isEmpty()
            && (!normalized.summary().isEmpty() || !normalized.location().isEmpty()
                || !normalized.priority().isEmpty())) {
            return String.join("|", normalized.title(), normalized.priority(),
                               normalized.location(), normalized.summary());
        }
        return "raw|" + ReviewFindingSimilarity.normalizeText(rawBody);
    }

     static String extractTableValue(String body, String key) {
        Pattern pattern = TABLE_VALUE_PATTERNS.computeIfAbsent(
            key,
            k -> Pattern.compile(TABLE_ROW_TEMPLATE.pattern().formatted(Pattern.quote(k)))
        );
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private record HeaderMatch(int startIndex, int endIndex, String title) {
    }

    public record FindingBlock(String title, String body) {
    }
}