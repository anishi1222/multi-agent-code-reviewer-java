package dev.logicojp.reviewer.report;

import dev.logicojp.reviewer.service.TemplateService;

import java.util.List;
import java.util.Map;

final class FallbackSummaryBuilder {

    private final TemplateService templateService;
    private final int excerptLength;

    FallbackSummaryBuilder(TemplateService templateService, int excerptLength) {
        this.templateService = templateService;
        this.excerptLength = excerptLength;
    }

    String buildFallbackSummary(List<ReviewResult> results) {
        String tableRows = buildTableRows(results);
        String agentSummaries = buildAgentSummaries(results);
        var placeholders = Map.of(
            "tableRows", tableRows,
            "agentSummaries", agentSummaries);
        return templateService.getFallbackSummaryTemplate(placeholders);
    }

    private String buildTableRows(List<ReviewResult> results) {
        var tableRowsBuilder = new StringBuilder();
        for (ReviewResult result : results) {
            tableRowsBuilder.append(templateService.getFallbackAgentRow(
                Map.of(
                    "displayName", result.agentConfig().displayName(),
                    "content", excerpt(result)
                )));
        }
        return tableRowsBuilder.toString();
    }

    private String buildAgentSummaries(List<ReviewResult> results) {
        var agentSummariesBuilder = new StringBuilder();
        for (ReviewResult result : results) {
            if (result.isSuccess()) {
                agentSummariesBuilder.append(templateService.getFallbackAgentSuccess(
                    Map.of(
                        "displayName", result.agentConfig().displayName(),
                        "content", excerpt(result)
                    )));
            } else {
                agentSummariesBuilder.append(templateService.getFallbackAgentFailure(
                    Map.of(
                        "displayName", result.agentConfig().displayName(),
                        "errorMessage", errorMessageOrEmpty(result)
                    )));
            }
            agentSummariesBuilder.append("\n");
        }
        return agentSummariesBuilder.toString();
    }

    private String errorMessageOrEmpty(ReviewResult result) {
        return result.errorMessage() != null ? result.errorMessage() : "";
    }

    private String excerpt(ReviewResult result) {
        if (result == null || !result.isSuccess() || result.content() == null || result.content().isBlank()) {
            return "N/A";
        }
        String content = result.content();
        int prefixLength = Math.min(content.length(), excerptLength * 3);
        String normalizedPrefix = content.substring(0, prefixLength).replaceAll("\\s+", " ").trim();
        if (normalizedPrefix.length() <= excerptLength) {
            return normalizedPrefix;
        }
        return normalizedPrefix.substring(0, excerptLength) + "...";
    }
}