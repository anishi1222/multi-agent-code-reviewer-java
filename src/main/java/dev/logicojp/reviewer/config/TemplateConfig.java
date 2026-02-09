package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

/**
 * Configuration for template paths (summary system prompt, user prompt, etc.).
 */
@ConfigurationProperties("reviewer.templates")
public record TemplateConfig(
    @Nullable String directory,
    @Nullable String summarySystemPrompt,
    @Nullable String summaryUserPrompt,
    @Nullable String defaultOutputFormat,
    @Nullable String report,
    @Nullable String executiveSummary,
    @Nullable String fallbackSummary,
    @Nullable String customInstructionSection,
    @Nullable String localReviewContent,
    @Nullable String reviewCustomInstruction,
    @Nullable String summaryResultEntry,
    @Nullable String summaryResultErrorEntry,
    @Nullable String fallbackAgentRow,
    @Nullable String fallbackAgentSuccess,
    @Nullable String fallbackAgentFailure,
    @Nullable String reportLinkEntry
) {

    private static final String DEFAULT_DIRECTORY = "templates";
    private static final String DEFAULT_SUMMARY_SYSTEM = "summary-system.md";
    private static final String DEFAULT_SUMMARY_USER = "summary-prompt.md";
    private static final String DEFAULT_OUTPUT_FORMAT = "default-output-format.md";
    private static final String DEFAULT_REPORT = "report.md";
    private static final String DEFAULT_EXECUTIVE_SUMMARY = "executive-summary.md";
    private static final String DEFAULT_FALLBACK_SUMMARY = "fallback-summary.md";
    private static final String DEFAULT_CUSTOM_INSTRUCTION_SECTION = "custom-instruction-section.md";
    private static final String DEFAULT_LOCAL_REVIEW_CONTENT = "local-review-content.md";
    private static final String DEFAULT_REVIEW_CUSTOM_INSTRUCTION = "review-custom-instruction.md";
    private static final String DEFAULT_SUMMARY_RESULT_ENTRY = "summary-result-entry.md";
    private static final String DEFAULT_SUMMARY_RESULT_ERROR_ENTRY = "summary-result-error-entry.md";
    private static final String DEFAULT_FALLBACK_AGENT_ROW = "fallback-agent-row.md";
    private static final String DEFAULT_FALLBACK_AGENT_SUCCESS = "fallback-agent-success.md";
    private static final String DEFAULT_FALLBACK_AGENT_FAILURE = "fallback-agent-failure.md";
    private static final String DEFAULT_REPORT_LINK_ENTRY = "report-link-entry.md";

    public TemplateConfig {
        directory = (directory == null || directory.isBlank()) ? DEFAULT_DIRECTORY : directory;
        summarySystemPrompt = (summarySystemPrompt == null || summarySystemPrompt.isBlank()) 
            ? DEFAULT_SUMMARY_SYSTEM : summarySystemPrompt;
        summaryUserPrompt = (summaryUserPrompt == null || summaryUserPrompt.isBlank()) 
            ? DEFAULT_SUMMARY_USER : summaryUserPrompt;
        defaultOutputFormat = (defaultOutputFormat == null || defaultOutputFormat.isBlank())
            ? DEFAULT_OUTPUT_FORMAT : defaultOutputFormat;
        report = (report == null || report.isBlank())
            ? DEFAULT_REPORT : report;
        executiveSummary = (executiveSummary == null || executiveSummary.isBlank())
            ? DEFAULT_EXECUTIVE_SUMMARY : executiveSummary;
        fallbackSummary = (fallbackSummary == null || fallbackSummary.isBlank())
            ? DEFAULT_FALLBACK_SUMMARY : fallbackSummary;
        customInstructionSection = (customInstructionSection == null || customInstructionSection.isBlank())
            ? DEFAULT_CUSTOM_INSTRUCTION_SECTION : customInstructionSection;
        localReviewContent = (localReviewContent == null || localReviewContent.isBlank())
            ? DEFAULT_LOCAL_REVIEW_CONTENT : localReviewContent;
        reviewCustomInstruction = (reviewCustomInstruction == null || reviewCustomInstruction.isBlank())
            ? DEFAULT_REVIEW_CUSTOM_INSTRUCTION : reviewCustomInstruction;
        summaryResultEntry = (summaryResultEntry == null || summaryResultEntry.isBlank())
            ? DEFAULT_SUMMARY_RESULT_ENTRY : summaryResultEntry;
        summaryResultErrorEntry = (summaryResultErrorEntry == null || summaryResultErrorEntry.isBlank())
            ? DEFAULT_SUMMARY_RESULT_ERROR_ENTRY : summaryResultErrorEntry;
        fallbackAgentRow = (fallbackAgentRow == null || fallbackAgentRow.isBlank())
            ? DEFAULT_FALLBACK_AGENT_ROW : fallbackAgentRow;
        fallbackAgentSuccess = (fallbackAgentSuccess == null || fallbackAgentSuccess.isBlank())
            ? DEFAULT_FALLBACK_AGENT_SUCCESS : fallbackAgentSuccess;
        fallbackAgentFailure = (fallbackAgentFailure == null || fallbackAgentFailure.isBlank())
            ? DEFAULT_FALLBACK_AGENT_FAILURE : fallbackAgentFailure;
        reportLinkEntry = (reportLinkEntry == null || reportLinkEntry.isBlank())
            ? DEFAULT_REPORT_LINK_ENTRY : reportLinkEntry;
    }
}
