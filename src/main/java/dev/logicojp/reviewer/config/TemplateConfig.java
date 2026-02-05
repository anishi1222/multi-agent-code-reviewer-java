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
    @Nullable String reviewCustomInstruction
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
    }
}
