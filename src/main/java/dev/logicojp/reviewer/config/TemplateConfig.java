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
    @Nullable String summaryUserPrompt
) {

    private static final String DEFAULT_DIRECTORY = "templates";
    private static final String DEFAULT_SUMMARY_SYSTEM = "summary-system.md";
    private static final String DEFAULT_SUMMARY_USER = "summary-prompt.md";

    public TemplateConfig {
        directory = (directory == null || directory.isBlank()) ? DEFAULT_DIRECTORY : directory;
        summarySystemPrompt = (summarySystemPrompt == null || summarySystemPrompt.isBlank()) 
            ? DEFAULT_SUMMARY_SYSTEM : summarySystemPrompt;
        summaryUserPrompt = (summaryUserPrompt == null || summaryUserPrompt.isBlank()) 
            ? DEFAULT_SUMMARY_USER : summaryUserPrompt;
    }
}
