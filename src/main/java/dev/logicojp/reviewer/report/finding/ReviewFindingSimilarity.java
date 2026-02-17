package dev.logicojp.reviewer.report.finding;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReviewFindingSimilarity {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern KEYWORD_PATTERN = Pattern.compile("[a-z0-9_]+|[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]{2,}");
    private static final double NEAR_DUPLICATE_SIMILARITY = 0.80d;

    private ReviewFindingSimilarity() {
    }

    public static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        char[] chars = value.toCharArray();
        StringBuilder sb = new StringBuilder(chars.length);
        boolean lastWasSpace = true;
        for (char c : chars) {
            char lower = Character.toLowerCase(c);
            switch (lower) {
                case '`', '*', '_' -> { /* skip */ }
                case '|', '/', '\t', '\n', '\r', ' ' -> {
                    if (!lastWasSpace) { sb.append(' '); lastWasSpace = true; }
                }
                default -> {
                    if (lower == '\u30FB') { // ・
                        if (!lastWasSpace) { sb.append(' '); lastWasSpace = true; }
                    } else {
                        sb.append(lower); lastWasSpace = false;
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    public static Set<String> bigrams(String text) {
        String compact = text.replace(" ", "");
        if (compact.length() < 2) {
            return compact.isEmpty() ? Set.of() : Set.of(compact);
        }

        Set<String> grams = HashSet.newHashSet(compact.length() - 1);
        for (int i = 0; i < compact.length() - 1; i++) {
            grams.add(compact.substring(i, i + 2));
        }
        return grams;
    }

    public static boolean isSimilarText(String left, String right,
                                 Set<String> leftBigrams,
                                 Set<String> rightBigrams) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        if (left.length() >= 8 && right.contains(left)) {
            return true;
        }
        if (right.length() >= 8 && left.contains(right)) {
            return true;
        }
        return diceCoefficient(leftBigrams, rightBigrams) >= NEAR_DUPLICATE_SIMILARITY;
    }

    public static boolean hasCommonKeyword(String left, String right) {
        Set<String> leftWords = extractKeywords(left);
        Set<String> rightWords = extractKeywords(right);
        if (leftWords.isEmpty() || rightWords.isEmpty()) {
            return false;
        }
        for (String word : leftWords) {
            if (rightWords.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> keywords = new LinkedHashSet<>();
        Matcher matcher = KEYWORD_PATTERN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2) {
                keywords.add(token);
            }
        }
        return keywords;
    }

    private static double diceCoefficient(Set<String> leftBigrams, Set<String> rightBigrams) {
        if (leftBigrams.isEmpty() || rightBigrams.isEmpty()) {
            return 0.0d;
        }

        int overlap = 0;
        for (String gram : leftBigrams) {
            if (rightBigrams.contains(gram)) {
                overlap++;
            }
        }
        return (2.0d * overlap) / (leftBigrams.size() + rightBigrams.size());
    }
}