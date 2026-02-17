package dev.logicojp.reviewer.report;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

record AggregatedFinding(String title,
                        String body,
                        Set<Integer> passNumbers,
                        String normalizedTitle,
                        String normalizedPriority,
                        String normalizedSummary,
                        String normalizedLocation,
                        Set<String> titleBigrams,
                        Set<String> summaryBigrams,
                        Set<String> locationBigrams) {

    record NormalizedFinding(String title,
                             String priority,
                             String summary,
                             String location,
                             Set<String> titleBigrams,
                             Set<String> summaryBigrams,
                             Set<String> locationBigrams) {
    }

    private static final int FALLBACK_SIMILARITY_PREFIX_LENGTH = 500;

    AggregatedFinding {
        Objects.requireNonNull(title);
        Objects.requireNonNull(body);
        passNumbers = Collections.unmodifiableSet(new LinkedHashSet<>(passNumbers));
    }

    static AggregatedFinding from(ReviewFindingParser.FindingBlock block, int passNumber) {
        return fromNormalized(block, normalize(block), passNumber);
    }

    static AggregatedFinding fromNormalized(ReviewFindingParser.FindingBlock block,
                                            NormalizedFinding normalized,
                                            int passNumber) {
        return new AggregatedFinding(
            block.title(),
            block.body(),
            new LinkedHashSet<>(Set.of(passNumber)),
            normalized.title(),
            normalized.priority(),
            normalized.summary(),
            normalized.location(),
            normalized.titleBigrams(),
            normalized.summaryBigrams(),
            normalized.locationBigrams()
        );
    }

    static NormalizedFinding normalize(ReviewFindingParser.FindingBlock block) {
        String normalizedTitle = normalizeText(block.title());
        String normalizedPriority = normalizeText(ReviewFindingParser.extractTableValue(block.body(), "Priority"));
        String normalizedSummary = normalizeText(ReviewFindingParser.extractTableValue(block.body(), "指摘の概要"));
        String normalizedLocation = normalizeText(ReviewFindingParser.extractTableValue(block.body(), "該当箇所"));
        return new NormalizedFinding(
            normalizedTitle,
            normalizedPriority,
            normalizedSummary,
            normalizedLocation,
            ReviewFindingSimilarity.bigrams(normalizedTitle),
            ReviewFindingSimilarity.bigrams(normalizedSummary),
            ReviewFindingSimilarity.bigrams(normalizedLocation)
        );
    }

    static AggregatedFinding fallback(String rawContent, int passNumber) {
        String normalizedRaw = normalizeText(rawContent);
        String similarityTarget = normalizedRaw.length() > FALLBACK_SIMILARITY_PREFIX_LENGTH
            ? normalizedRaw.substring(0, FALLBACK_SIMILARITY_PREFIX_LENGTH)
            : normalizedRaw;

        return new AggregatedFinding(
            "レビュー結果",
            rawContent,
            new LinkedHashSet<>(Set.of(passNumber)),
            normalizeText("レビュー結果"),
            "",
            normalizedRaw,
            "",
            ReviewFindingSimilarity.bigrams(normalizeText("レビュー結果")),
            ReviewFindingSimilarity.bigrams(similarityTarget),
            Set.of()
        );
    }

    boolean isNearDuplicateOf(NormalizedFinding incoming) {
        if (hasPriorityMismatch(incoming.priority())) {
            return false;
        }

        if (hasLocationContext(incoming.location())) {
            return matchByLocationAndContent(incoming);
        }

        return matchBySummaryAndTitle(incoming);
    }

    private boolean hasPriorityMismatch(String incomingPriority) {
        return !normalizedPriority.isEmpty()
            && !incomingPriority.isEmpty()
            && !normalizedPriority.equals(incomingPriority);
    }

    private boolean hasLocationContext(String incomingLocation) {
        return !normalizedLocation.isEmpty() && !incomingLocation.isEmpty();
    }

    private boolean matchByLocationAndContent(NormalizedFinding incoming) {
        if (!ReviewFindingSimilarity.isSimilarText(normalizedLocation, incoming.location(),
            locationBigrams, incoming.locationBigrams())) {
            return false;
        }

        return ReviewFindingSimilarity.isSimilarText(normalizedSummary, incoming.summary(),
            summaryBigrams, incoming.summaryBigrams())
            || ReviewFindingSimilarity.isSimilarText(normalizedTitle, incoming.title(),
            titleBigrams, incoming.titleBigrams())
            || ReviewFindingSimilarity.hasCommonKeyword(normalizedTitle, incoming.title());
    }

    private boolean matchBySummaryAndTitle(NormalizedFinding incoming) {
        return ReviewFindingSimilarity.isSimilarText(normalizedSummary, incoming.summary(),
            summaryBigrams, incoming.summaryBigrams())
            && ReviewFindingSimilarity.isSimilarText(normalizedTitle, incoming.title(),
            titleBigrams, incoming.titleBigrams());
    }

    AggregatedFinding withPass(int passNumber) {
        var newPasses = new LinkedHashSet<>(passNumbers);
        newPasses.add(passNumber);
        return new AggregatedFinding(title, body, newPasses,
            normalizedTitle, normalizedPriority, normalizedSummary,
            normalizedLocation, titleBigrams, summaryBigrams, locationBigrams);
    }

    private static String normalizeText(String value) {
        return ReviewFindingSimilarity.normalizeText(value);
    }
}