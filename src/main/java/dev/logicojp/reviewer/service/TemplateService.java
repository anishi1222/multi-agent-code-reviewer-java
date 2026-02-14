package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.config.TemplateConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Service for loading and processing templates.
/// Supports loading from external files with fallback to classpath resources.
@Singleton
public class TemplateService {

    private static final Logger logger = LoggerFactory.getLogger(TemplateService.class);

    private final TemplateConfig config;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    @Inject
    public TemplateService(TemplateConfig config) {
        this.config = config;
    }

    /// Loads a template by name, applying placeholder substitutions.
    ///
    /// @param templateName The template name (without directory prefix)
    /// @param placeholders Map of placeholder names to values (e.g., "repository" -> "owner/repo")
    /// @return The processed template content
    public String loadTemplate(String templateName, Map<String, String> placeholders) {
        String content = loadTemplateContent(templateName);
        return applyPlaceholders(content, placeholders);
    }

    /// Loads raw template content without placeholder substitution.
    /// Results are cached in memory — each template is read from disk at most once.
    ///
    /// @param templateName The template name
    /// @return The raw template content
    public String loadTemplateContent(String templateName) {
        return templateCache.computeIfAbsent(templateName, this::loadTemplateFromSource);
    }

    /// Loads template content from the filesystem or classpath.
    private String loadTemplateFromSource(String templateName) {
        Path templatePath = Path.of(config.directory(), templateName);

        // Try external file first
        if (Files.exists(templatePath)) {
            try {
                logger.debug("Loading template from file: {}", templatePath);
                return Files.readString(templatePath);
            } catch (IOException e) {
                logger.warn("Failed to read template file {}: {}", templatePath, e.getMessage());
            }
        }

        // Fall back to classpath resource
        String resourcePath = config.directory() + "/" + templateName;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                logger.debug("Loading template from classpath: {}", resourcePath);
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.warn("Failed to read template from classpath {}: {}", resourcePath, e.getMessage());
        }

        logger.warn("Template not found: {} (checked {} and classpath:{})", 
            templateName, templatePath, resourcePath);
        return "";
    }

    /// Applies placeholder substitutions to a template.
    /// Placeholders are in the format {{name}}.
    ///
    /// @param template The template content
    /// @param placeholders Map of placeholder names to values
    /// @return The processed content
    public String applyPlaceholders(String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return template;
        }

        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    // Convenience methods for specific templates

    /// Loads the default output format template.
    public String getDefaultOutputFormat() {
        return loadTemplateContent(config.defaultOutputFormat());
    }

    /// Loads the report template with placeholders applied.
    public String getReportTemplate(Map<String, String> placeholders) {
        return loadTemplate(config.report(), placeholders);
    }

    /// Loads the executive summary template with placeholders applied.
    public String getExecutiveSummaryTemplate(Map<String, String> placeholders) {
        return loadTemplate(config.executiveSummary(), placeholders);
    }

    /// Loads the fallback summary template with placeholders applied.
    public String getFallbackSummaryTemplate(Map<String, String> placeholders) {
        return loadTemplate(config.fallbackSummary(), placeholders);
    }

    /// Loads the local review content template with placeholders applied.
    public String getLocalReviewContent(Map<String, String> placeholders) {
        return loadTemplate(config.localReviewContent(), placeholders);
    }

    /// Loads the summary system prompt template.
    public String getSummarySystemPrompt() {
        return loadTemplateContent(config.summarySystemPrompt());
    }

    /// Loads the summary user prompt template with placeholders applied.
    public String getSummaryUserPrompt(Map<String, String> placeholders) {
        return loadTemplate(config.summaryUserPrompt(), placeholders);
    }

    /// Loads the summary result entry template (per-agent success) with placeholders applied.
    public String getSummaryResultEntry(Map<String, String> placeholders) {
        return loadTemplate(config.summaryResultEntry(), placeholders);
    }

    /// Loads the summary result error entry template (per-agent failure) with placeholders applied.
    public String getSummaryResultErrorEntry(Map<String, String> placeholders) {
        return loadTemplate(config.summaryResultErrorEntry(), placeholders);
    }

    /// Loads the fallback agent row template (table row) with placeholders applied.
    public String getFallbackAgentRow(Map<String, String> placeholders) {
        return loadTemplate(config.fallbackAgentRow(), placeholders);
    }

    /// Loads the fallback agent success detail template with placeholders applied.
    public String getFallbackAgentSuccess(Map<String, String> placeholders) {
        return loadTemplate(config.fallbackAgentSuccess(), placeholders);
    }

    /// Loads the fallback agent failure detail template with placeholders applied.
    public String getFallbackAgentFailure(Map<String, String> placeholders) {
        return loadTemplate(config.fallbackAgentFailure(), placeholders);
    }

    /// Loads the report link entry template with placeholders applied.
    public String getReportLinkEntry(Map<String, String> placeholders) {
        return loadTemplate(config.reportLinkEntry(), placeholders);
    }

    /// Loads the output constraints template.
    /// Contains constraints such as CoT suppression, output format enforcement,
    /// and language requirements for review output.
    public String getOutputConstraints() {
        return loadTemplateContent(config.outputConstraints());
    }

    /// Gets the template configuration.
    public TemplateConfig getConfig() {
        return config;
    }
}
