package dev.logicojp.reviewer.report;

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

    AggregatedFinding {
        Objects.requireNonNull(title);
        Objects.requireNonNull(body);
        passNumbers = new LinkedHashSet<>(passNumbers);
    }

    static AggregatedFinding from(ReviewFindingParser.FindingBlock block, int passNumber) {
        String normalizedPriority = normalizeText(ReviewFindingParser.extractTableValue(block.body(), "Priority"));
        String normalizedSummary = normalizeText(ReviewFindingParser.extractTableValue(block.body(), "指摘の概要"));
        String normalizedLocation = normalizeText(ReviewFindingParser.extractTableValue(block.body(), "該当箇所"));
        return new AggregatedFinding(
            block.title(),
            block.body(),
            new LinkedHashSet<>(Set.of(passNumber)),
            normalizeText(block.title()),
            normalizedPriority,
            normalizedSummary,
            normalizedLocation,
            ReviewFindingSimilarity.bigrams(normalizeText(block.title())),
            ReviewFindingSimilarity.bigrams(normalizedSummary),
            ReviewFindingSimilarity.bigrams(normalizedLocation)
        );
    }

    static AggregatedFinding fallback(String rawContent, int passNumber) {
        return new AggregatedFinding(
            "レビュー結果",
            rawContent,
            new LinkedHashSet<>(Set.of(passNumber)),
            normalizeText("レビュー結果"),
            "",
            normalizeText(rawContent),
            "",
            ReviewFindingSimilarity.bigrams(normalizeText("レビュー結果")),
            ReviewFindingSimilarity.bigrams(normalizeText(rawContent)),
            Set.of()
        );
    }

    boolean isNearDuplicateOf(String incomingTitle,
                              String incomingPriority,
                              String incomingSummary,
                              String incomingLocation,
                              Set<String> incomingTitleBigrams,
                              Set<String> incomingSummaryBigrams,
                              Set<String> incomingLocationBigrams) {
        if (!normalizedPriority.isEmpty() && !incomingPriority.isEmpty()
            && !normalizedPriority.equals(incomingPriority)) {
            return false;
        }

        if (!normalizedLocation.isEmpty() && !incomingLocation.isEmpty()) {
            if (!ReviewFindingSimilarity.isSimilarText(normalizedLocation, incomingLocation,
                locationBigrams, incomingLocationBigrams)) {
                return false;
            }
            if (ReviewFindingSimilarity.isSimilarText(normalizedSummary, incomingSummary,
                summaryBigrams, incomingSummaryBigrams)
                || ReviewFindingSimilarity.isSimilarText(normalizedTitle, incomingTitle,
                titleBigrams, incomingTitleBigrams)
                || ReviewFindingSimilarity.hasCommonKeyword(normalizedTitle, incomingTitle)) {
                return true;
            }
        }

        return ReviewFindingSimilarity.isSimilarText(normalizedSummary, incomingSummary,
            summaryBigrams, incomingSummaryBigrams)
            && ReviewFindingSimilarity.isSimilarText(normalizedTitle, incomingTitle,
            titleBigrams, incomingTitleBigrams);
    }

    AggregatedFinding withPass(int passNumber) {
        LinkedHashSet<Integer> updated = new LinkedHashSet<>(passNumbers);
        updated.add(passNumber);
        return new AggregatedFinding(
            title,
            body,
            updated,
            normalizedTitle,
            normalizedPriority,
            normalizedSummary,
            normalizedLocation,
            titleBigrams,
            summaryBigrams,
            locationBigrams
        );
    }

    private static String normalizeText(String value) {
        return ReviewFindingSimilarity.normalizeText(value);
    }
}