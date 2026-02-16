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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Service for loading and processing templates.
/// Supports loading from external files with fallback to classpath resources.
@Singleton
public class TemplateService {

    private static final Logger logger = LoggerFactory.getLogger(TemplateService.class);

    private final TemplateConfig config;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();
    private static final Pattern TEMPLATE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]+\\.md");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

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
        if (!isValidTemplateName(templateName)) {
            return "";
        }

        Path templatePath = resolveTemplatePath(templateName);
        if (templatePath == null) {
            return "";
        }

        String content = loadTemplateByPath(templateName, templatePath);
        if (content != null) {
            return content;
        }

        warnTemplateNotFound(templateName, templatePath);
        return "";
    }

    private String loadTemplateByPath(String templateName, Path templatePath) {

        String fromFile = loadTemplateFromFile(templatePath);
        if (fromFile != null) {
            return fromFile;
        }

        String resourcePath = toResourcePath(templateName);
        String fromClasspath = loadTemplateFromClasspath(resourcePath);
        if (fromClasspath != null) {
            return fromClasspath;
        }

        return null;
    }

    private void warnTemplateNotFound(String templateName, Path templatePath) {
        String resourcePath = toResourcePath(templateName);
        logger.warn("Template not found: {} (checked {} and classpath:{})",
            templateName, templatePath, resourcePath);
    }

    private boolean isValidTemplateName(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            logger.warn("Invalid template name rejected: blank");
            return false;
        }
        if (!TEMPLATE_NAME_PATTERN.matcher(templateName).matches()) {
            logger.warn("Invalid template name rejected: {}", templateName);
            return false;
        }
        return true;
    }

    private Path resolveTemplatePath(String templateName) {
        Path baseDirectory = resolveBaseDirectory();
        Path templatePath = baseDirectory.resolve(templateName).normalize();
        if (isPathTraversal(templatePath, baseDirectory)) {
            logger.warn("Template path traversal rejected: {}", templateName);
            return null;
        }
        return templatePath;
    }

    private Path resolveBaseDirectory() {
        return Path.of(config.directory()).toAbsolutePath().normalize();
    }

    private boolean isPathTraversal(Path templatePath, Path baseDirectory) {
        return !templatePath.startsWith(baseDirectory);
    }

    private String loadTemplateFromFile(Path templatePath) {
        if (!Files.exists(templatePath)) {
            return null;
        }
        try {
            logger.debug("Loading template from file: {}", templatePath);
            return Files.readString(templatePath);
        } catch (IOException e) {
            logger.warn("Failed to read template file {}: {}", templatePath, e.getMessage());
            return null;
        }
    }

    private String toResourcePath(String templateName) {
        return config.directory() + "/" + templateName;
    }

    private String loadTemplateFromClasspath(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                logger.debug("Loading template from classpath: {}", resourcePath);
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.warn("Failed to read template from classpath {}: {}", resourcePath, e.getMessage());
        }
        return null;
    }

    /// Applies placeholder substitutions to a template in a single pass.
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

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = placeholders.getOrDefault(key, matcher.group());
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
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
        return loadTemplate(config.summary().executiveSummary(), placeholders);
    }

    /// Loads the fallback summary template with placeholders applied.
    public String getFallbackSummaryTemplate(Map<String, String> placeholders) {
        return loadTemplate(config.fallback().summary(), placeholders);
    }

    /// Loads the local review content template with placeholders applied.
    public String getLocalReviewContent(Map<String, String> placeholders) {
        return loadTemplate(config.localReviewContent(), placeholders);
    }

    /// Loads the summary system prompt template.
    public String getSummarySystemPrompt() {
        return loadTemplateContent(config.summary().systemPrompt());
    }

    /// Loads the summary user prompt template with placeholders applied.
    public String getSummaryUserPrompt(Map<String, String> placeholders) {
        return loadTemplate(config.summary().userPrompt(), placeholders);
    }

    /// Loads the summary result entry template (per-agent success) with placeholders applied.
    public String getSummaryResultEntry(Map<String, String> placeholders) {
        return loadTemplate(config.summary().resultEntry(), placeholders);
    }

    /// Loads the summary result error entry template (per-agent failure) with placeholders applied.
    public String getSummaryResultErrorEntry(Map<String, String> placeholders) {
        return loadTemplate(config.summary().resultErrorEntry(), placeholders);
    }

    /// Loads the fallback agent row template (table row) with placeholders applied.
    public String getFallbackAgentRow(Map<String, String> placeholders) {
        return loadTemplate(config.fallback().agentRow(), placeholders);
    }

    /// Loads the fallback agent success detail template with placeholders applied.
    public String getFallbackAgentSuccess(Map<String, String> placeholders) {
        return loadTemplate(config.fallback().agentSuccess(), placeholders);
    }

    /// Loads the fallback agent failure detail template with placeholders applied.
    public String getFallbackAgentFailure(Map<String, String> placeholders) {
        return loadTemplate(config.fallback().agentFailure(), placeholders);
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
