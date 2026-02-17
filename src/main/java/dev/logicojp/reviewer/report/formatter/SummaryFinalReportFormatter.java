package dev.logicojp.reviewer.report.formatter;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.finding.FindingsExtractor;
import dev.logicojp.reviewer.report.util.ReportFileUtils;
import dev.logicojp.reviewer.report.util.ReportFilenameUtils;

import dev.logicojp.reviewer.service.TemplateService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SummaryFinalReportFormatter {

    private final TemplateService templateService;

    public SummaryFinalReportFormatter(TemplateService templateService) {
        this.templateService = templateService;
    }

    public String format(String summaryContent,
                  String repository,
                  List<ReviewResult> results,
                  String date) {
        String reportLinks = buildReportLinks(results, date);
        String findingsSummary = resolveFindingsSummary(results);
        Map<String, String> placeholders = buildSummaryPlaceholders(
            summaryContent, repository, results, date, reportLinks, findingsSummary);
        return templateService.getExecutiveSummaryTemplate(placeholders);
    }

    private String resolveFindingsSummary(List<ReviewResult> results) {
        String findingsSummary = FindingsExtractor.buildFindingsSummary(results);
        if (findingsSummary.isEmpty()) {
            return "指摘事項はありません。";
        }
        return findingsSummary;
    }

    private Map<String, String> buildSummaryPlaceholders(String summaryContent,
                                                         String repository,
                                                         List<ReviewResult> results,
                                                         String date,
                                                         String reportLinks,
                                                         String findingsSummary) {
        var placeholders = new HashMap<String, String>();
        placeholders.put("date", date);
        placeholders.put("repository", repository);
        placeholders.put("agentCount", String.valueOf(results.size()));
        long successCount = results.stream().filter(ReviewResult::success).count();
        placeholders.put("successCount", String.valueOf(successCount));
        placeholders.put("failureCount", String.valueOf(results.size() - successCount));
        placeholders.put("summaryContent", summaryContent != null ? summaryContent : "");
        placeholders.put("findingsSummary", findingsSummary);
        placeholders.put("reportLinks", reportLinks);
        return placeholders;
    }

    private String buildReportLinks(List<ReviewResult> results, String date) {
        var reportLinksBuilder = new StringBuilder();
        for (ReviewResult result : results) {
            String safeName = ReportFilenameUtils.sanitizeAgentName(result.agentConfig().name());
            String filename = "%s_%s.md".formatted(safeName, date);
            var linkPlaceholders = Map.of(
                "displayName", result.agentConfig().displayName(),
                "filename", filename);
            reportLinksBuilder.append(templateService.getReportLinkEntry(linkPlaceholders));
        }
        return reportLinksBuilder.toString();
    }
}