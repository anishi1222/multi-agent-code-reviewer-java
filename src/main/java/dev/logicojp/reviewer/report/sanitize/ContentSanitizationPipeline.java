package dev.logicojp.reviewer.report.sanitize;

import java.util.List;

public final class ContentSanitizationPipeline {

    private final List<ContentSanitizationRule> rules;

    ContentSanitizationPipeline(List<ContentSanitizationRule> rules) {
        this.rules = List.copyOf(rules);
    }

    String apply(String content) {
        String result = content;
        for (ContentSanitizationRule rule : rules) {
            result = rule.apply(result);
        }
        return result;
    }
}