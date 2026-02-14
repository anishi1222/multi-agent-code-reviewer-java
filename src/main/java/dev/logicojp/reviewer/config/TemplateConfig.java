package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

/// Configuration for template paths (summary system prompt, user prompt, etc.).
@ConfigurationProperties("reviewer.templates")
public record TemplateConfig(
    @Nullable String directory,
    @Nullable String summarySystemPrompt,
    @Nullable String summaryUserPrompt,
    @Nullable String defaultOutputFormat,
    @Nullable String report,
    @Nullable String executiveSummary,
    @Nullable String fallbackSummary,
    @Nullable String localReviewContent,
    @Nullable String summaryResultEntry,
    @Nullable String summaryResultErrorEntry,
    @Nullable String fallbackAgentRow,
    @Nullable String fallbackAgentSuccess,
    @Nullable String fallbackAgentFailure,
    @Nullable String reportLinkEntry,
    @Nullable String outputConstraints
) {

    private static final String DEFAULT_DIRECTORY = "templates";
    private static final String DEFAULT_SUMMARY_SYSTEM = "summary-system.md";
    private static final String DEFAULT_SUMMARY_USER = "summary-prompt.md";
    private static final String DEFAULT_OUTPUT_FORMAT = "default-output-format.md";
    private static final String DEFAULT_REPORT = "report.md";
    private static final String DEFAULT_EXECUTIVE_SUMMARY = "executive-summary.md";
    private static final String DEFAULT_FALLBACK_SUMMARY = "fallback-summary.md";
    private static final String DEFAULT_LOCAL_REVIEW_CONTENT = "local-review-content.md";
    private static final String DEFAULT_SUMMARY_RESULT_ENTRY = "summary-result-entry.md";
    private static final String DEFAULT_SUMMARY_RESULT_ERROR_ENTRY = "summary-result-error-entry.md";
    private static final String DEFAULT_FALLBACK_AGENT_ROW = "fallback-agent-row.md";
    private static final String DEFAULT_FALLBACK_AGENT_SUCCESS = "fallback-agent-success.md";
    private static final String DEFAULT_FALLBACK_AGENT_FAILURE = "fallback-agent-failure.md";
    private static final String DEFAULT_REPORT_LINK_ENTRY = "report-link-entry.md";
    private static final String DEFAULT_OUTPUT_CONSTRAINTS = "output-constraints.md";

    public TemplateConfig {
        directory = defaultIfBlank(directory, DEFAULT_DIRECTORY);
        summarySystemPrompt = defaultIfBlank(summarySystemPrompt, DEFAULT_SUMMARY_SYSTEM);
        summaryUserPrompt = defaultIfBlank(summaryUserPrompt, DEFAULT_SUMMARY_USER);
        defaultOutputFormat = defaultIfBlank(defaultOutputFormat, DEFAULT_OUTPUT_FORMAT);
        report = defaultIfBlank(report, DEFAULT_REPORT);
        executiveSummary = defaultIfBlank(executiveSummary, DEFAULT_EXECUTIVE_SUMMARY);
        fallbackSummary = defaultIfBlank(fallbackSummary, DEFAULT_FALLBACK_SUMMARY);
        localReviewContent = defaultIfBlank(localReviewContent, DEFAULT_LOCAL_REVIEW_CONTENT);
        summaryResultEntry = defaultIfBlank(summaryResultEntry, DEFAULT_SUMMARY_RESULT_ENTRY);
        summaryResultErrorEntry = defaultIfBlank(summaryResultErrorEntry, DEFAULT_SUMMARY_RESULT_ERROR_ENTRY);
        fallbackAgentRow = defaultIfBlank(fallbackAgentRow, DEFAULT_FALLBACK_AGENT_ROW);
        fallbackAgentSuccess = defaultIfBlank(fallbackAgentSuccess, DEFAULT_FALLBACK_AGENT_SUCCESS);
        fallbackAgentFailure = defaultIfBlank(fallbackAgentFailure, DEFAULT_FALLBACK_AGENT_FAILURE);
        reportLinkEntry = defaultIfBlank(reportLinkEntry, DEFAULT_REPORT_LINK_ENTRY);
        outputConstraints = defaultIfBlank(outputConstraints, DEFAULT_OUTPUT_CONSTRAINTS);
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
