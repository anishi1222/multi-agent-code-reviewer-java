package dev.logicojp.reviewer.report.formatter;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.service.TemplateService;

import java.util.Map;
import java.util.StringJoiner;

public final class ReportContentFormatter {

    private final TemplateService templateService;

    public ReportContentFormatter(TemplateService templateService) {
        this.templateService = templateService;
    }

    public String format(ReviewResult result, String date) {
        AgentConfig config = result.agentConfig();
        String content = resolveReportContent(result);
        Map<String, String> placeholders = buildPlaceholders(result, config, date, content);

        return templateService.getReportTemplate(placeholders);
    }

    private String resolveReportContent(ReviewResult result) {
        if (result.success()) {
            return result.content() != null ? result.content() : "";
        }
        return "⚠️ **レビュー失敗**\n\nエラー: " + result.errorMessage();
    }

    private Map<String, String> buildPlaceholders(ReviewResult result,
                                                   AgentConfig config,
                                                   String date,
                                                   String content) {
        return Map.of(
            "displayName", config.displayName(),
            "date", date,
            "repository", result.repository(),
            "focusAreas", formatFocusAreas(config),
            "content", content
        );
    }

    private String formatFocusAreas(AgentConfig config) {
        var focusAreasJoiner = new StringJoiner("\n", "", "\n");
        for (String area : config.focusAreas()) {
            focusAreasJoiner.add("- " + area);
        }
        return focusAreasJoiner.toString();
    }
}