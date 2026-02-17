package dev.logicojp.reviewer.report;

import dev.logicojp.reviewer.service.TemplateService;

import java.util.List;
import java.util.Map;

final class SummaryPromptBuilder {

    private final TemplateService templateService;
    private final int maxContentPerAgent;
    private final int maxTotalPromptContent;

    SummaryPromptBuilder(TemplateService templateService, int maxContentPerAgent, int maxTotalPromptContent) {
        this.templateService = templateService;
        this.maxContentPerAgent = maxContentPerAgent;
        this.maxTotalPromptContent = maxTotalPromptContent;
    }

    String buildSummaryPrompt(List<ReviewResult> results, String repository) {
        var resultsSection = new StringBuilder(
            Math.min(results.size() * 8192, maxTotalPromptContent + 4096));
        int totalContentSize = 0;

        // Pre-load templates once to avoid per-iteration regex/Matcher overhead
        String successTemplate = templateService.loadTemplateContent(
            templateService.getConfig().summary().resultEntry());
        String errorTemplate = templateService.loadTemplateContent(
            templateService.getConfig().summary().resultErrorEntry());

        for (ReviewResult result : results) {
            if (result.success()) {
                int remaining = maxTotalPromptContent - totalContentSize;
                if (remaining <= 0) {
                    break;
                }
                String content = clipContentForSummary(result.content(), remaining);
                totalContentSize += content.length();
                appendSuccessEntry(resultsSection, result, content, successTemplate);
            } else {
                appendErrorEntry(resultsSection, result, errorTemplate);
            }
        }

        var placeholders = Map.of(
            "repository", repository,
            "results", resultsSection.toString());
        return templateService.getSummaryUserPrompt(placeholders);
    }

    private String clipContentForSummary(String content, int remaining) {
        String safeContent = content != null ? content : "";
        int maxAllowed = Math.min(maxContentPerAgent, remaining);
        if (safeContent.length() <= maxAllowed) {
            return safeContent;
        }
        return safeContent.substring(0, maxAllowed) + "\n\n... (truncated for summary)";
    }

    private void appendSuccessEntry(StringBuilder resultsSection, ReviewResult result,
                                     String content, String template) {
        resultsSection.append(templateService.applyPlaceholders(template,
            Map.of(
                "displayName", result.agentConfig().displayName(),
                "content", content
            )));
    }

    private void appendErrorEntry(StringBuilder resultsSection, ReviewResult result, String template) {
        resultsSection.append(templateService.applyPlaceholders(template,
            Map.of(
                "displayName", result.agentConfig().displayName(),
                "errorMessage", result.errorMessage() != null ? result.errorMessage() : ""
            )));
    }
}