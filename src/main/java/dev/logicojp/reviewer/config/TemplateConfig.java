package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

/// Configuration for template paths.
/// Uses Micronaut's nested `@ConfigurationProperties` for logical grouping.
@ConfigurationProperties("reviewer.templates")
public record TemplateConfig(
    @Nullable String directory,
    @Nullable String defaultOutputFormat,
    @Nullable String report,
    @Nullable String localReviewContent,
    @Nullable String outputConstraints,
    @Nullable String reportLinkEntry,
    @Nullable SummaryTemplates summary,
    @Nullable FallbackTemplates fallback
) {

    private static final String DEFAULT_DIRECTORY = "templates";
    private static final String DEFAULT_OUTPUT_FORMAT = "default-output-format.md";
    private static final String DEFAULT_REPORT = "report.md";
    private static final String DEFAULT_LOCAL_REVIEW_CONTENT = "local-review-content.md";
    private static final String DEFAULT_OUTPUT_CONSTRAINTS = "output-constraints.md";
    private static final String DEFAULT_REPORT_LINK_ENTRY = "report-link-entry.md";

    /// Shared defaults utility used by this record and nested template records.
    static final class Defaults {
        private Defaults() {
        }

        static String defaultIfBlank(String value, String defaultValue) {
            return (value == null || value.isBlank()) ? defaultValue : value;
        }
    }

    public TemplateConfig {
        directory = Defaults.defaultIfBlank(directory, DEFAULT_DIRECTORY);
        defaultOutputFormat = Defaults.defaultIfBlank(defaultOutputFormat, DEFAULT_OUTPUT_FORMAT);
        report = Defaults.defaultIfBlank(report, DEFAULT_REPORT);
        localReviewContent = Defaults.defaultIfBlank(localReviewContent, DEFAULT_LOCAL_REVIEW_CONTENT);
        outputConstraints = Defaults.defaultIfBlank(outputConstraints, DEFAULT_OUTPUT_CONSTRAINTS);
        reportLinkEntry = Defaults.defaultIfBlank(reportLinkEntry, DEFAULT_REPORT_LINK_ENTRY);
        summary = summary != null ? summary : new SummaryTemplates(null, null, null, null, null);
        fallback = fallback != null ? fallback : new FallbackTemplates(null, null, null, null);
    }

    /// Summary-related template configuration.
    @ConfigurationProperties("summary")
    public record SummaryTemplates(
        @Nullable String systemPrompt,
        @Nullable String userPrompt,
        @Nullable String executiveSummary,
        @Nullable String resultEntry,
        @Nullable String resultErrorEntry
    ) {
        private static final String DEFAULT_SYSTEM = "summary-system.md";
        private static final String DEFAULT_USER = "summary-prompt.md";
        private static final String DEFAULT_EXECUTIVE_SUMMARY = "executive-summary.md";
        private static final String DEFAULT_RESULT_ENTRY = "summary-result-entry.md";
        private static final String DEFAULT_RESULT_ERROR_ENTRY = "summary-result-error-entry.md";

        public SummaryTemplates {
            systemPrompt = Defaults.defaultIfBlank(systemPrompt, DEFAULT_SYSTEM);
            userPrompt = Defaults.defaultIfBlank(userPrompt, DEFAULT_USER);
            executiveSummary = Defaults.defaultIfBlank(executiveSummary, DEFAULT_EXECUTIVE_SUMMARY);
            resultEntry = Defaults.defaultIfBlank(resultEntry, DEFAULT_RESULT_ENTRY);
            resultErrorEntry = Defaults.defaultIfBlank(resultErrorEntry, DEFAULT_RESULT_ERROR_ENTRY);
        }
    }

    /// Fallback template configuration (used when AI summary generation fails).
    @ConfigurationProperties("fallback")
    public record FallbackTemplates(
        @Nullable String summary,
        @Nullable String agentRow,
        @Nullable String agentSuccess,
        @Nullable String agentFailure
    ) {
        private static final String DEFAULT_SUMMARY = "fallback-summary.md";
        private static final String DEFAULT_AGENT_ROW = "fallback-agent-row.md";
        private static final String DEFAULT_AGENT_SUCCESS = "fallback-agent-success.md";
        private static final String DEFAULT_AGENT_FAILURE = "fallback-agent-failure.md";

        public FallbackTemplates {
            summary = Defaults.defaultIfBlank(summary, DEFAULT_SUMMARY);
            agentRow = Defaults.defaultIfBlank(agentRow, DEFAULT_AGENT_ROW);
            agentSuccess = Defaults.defaultIfBlank(agentSuccess, DEFAULT_AGENT_SUCCESS);
            agentFailure = Defaults.defaultIfBlank(agentFailure, DEFAULT_AGENT_FAILURE);
        }
    }
}
