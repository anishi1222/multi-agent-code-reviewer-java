package dev.logicojp.reviewer.report;

import com.github.copilot.sdk.*;
import com.github.copilot.sdk.json.*;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Generates executive summary by aggregating all agent review results.
 * All prompt/template content is loaded from external templates via {@link TemplateService}.
 */
public class SummaryGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(SummaryGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final Path outputDirectory;
    private final CopilotClient client;
    private final String summaryModel;
    private final String reasoningEffort;
    private final long timeoutMinutes;
    private final TemplateService templateService;
    
    public SummaryGenerator(
            Path outputDirectory, 
            CopilotClient client, 
            String summaryModel,
            String reasoningEffort,
            long timeoutMinutes,
            TemplateService templateService) {
        this.outputDirectory = outputDirectory;
        this.client = client;
        this.summaryModel = summaryModel;
        this.reasoningEffort = reasoningEffort;
        this.timeoutMinutes = timeoutMinutes;
        this.templateService = templateService;
    }
    
    /**
     * Generates an executive summary from all review results.
     * @param results List of review results from all agents
     * @param repository The repository that was reviewed
     * @return Path to the generated summary file
     */
    public Path generateSummary(List<ReviewResult> results, String repository) throws Exception {
        ensureOutputDirectory();
        
        String filename = String.format("executive_summary_%s.md", 
            LocalDate.now().format(FILE_DATE_FORMATTER));
        Path summaryPath = outputDirectory.resolve(filename);
        
        logger.info("Generating executive summary from {} review results", results.size());
        
        // Build the summary using AI
        String summaryContent = buildSummaryWithAI(results, repository);
        
        // Build the final report
        String finalReport = buildFinalReport(summaryContent, repository, results);
        Files.writeString(summaryPath, finalReport);
        
        logger.info("Generated executive summary: {}", summaryPath);
        return summaryPath;
    }
    
    /**
     * Determines the appropriate reasoning effort for a model.
     * <p>If the model is a reasoning model, returns the configured
     * {@code reasoningEffort} value. Otherwise returns {@code null}.</p>
     *
     * @param model the model name
     * @param configuredEffort the configured reasoning effort value
     * @return the reasoning effort level, or null if not a reasoning model
     */
    public static String resolveReasoningEffort(String model, String configuredEffort) {
        if (ModelConfig.isReasoningModel(model)) {
            return configuredEffort != null ? configuredEffort : ModelConfig.DEFAULT_REASONING_EFFORT;
        }
        return null;
    }

    private String buildSummaryWithAI(List<ReviewResult> results, String repository) throws Exception {
        // Create a new session for summary generation
        logger.info("Using model for summary: {}", summaryModel);
        String systemPrompt = templateService.getSummarySystemPrompt();
        var sessionConfig = new SessionConfig()
                .setModel(summaryModel)
                .setSystemMessage(new SystemMessageConfig()
                    .setMode(SystemMessageMode.REPLACE)
                    .setContent(systemPrompt));

        // Explicitly set reasoning effort for reasoning models to override
        // the Copilot CLI's auto-detection which may send invalid effort values.
        String effort = resolveReasoningEffort(summaryModel, reasoningEffort);
        if (effort != null) {
            logger.info("Setting reasoning effort '{}' for model: {}", effort, summaryModel);
            sessionConfig.setReasoningEffort(effort);
        }

        var session = client.createSession(sessionConfig
        ).get(timeoutMinutes, TimeUnit.MINUTES);
        long timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes);
        
        try {
            String prompt = buildSummaryPrompt(results, repository);
            var response = session
                .sendAndWait(new MessageOptions().setPrompt(prompt), timeoutMs)
                .get(timeoutMinutes, TimeUnit.MINUTES);
            return response.getData().getContent();
        } catch (java.util.concurrent.TimeoutException ex) {
            logger.error("Summary generation timed out: {}", ex.getMessage());
            return buildFallbackSummary(results);
        } finally {
            session.close();
        }
    }

    private String buildFallbackSummary(List<ReviewResult> results) {
        // Build table rows using template
        StringBuilder tableRowsBuilder = new StringBuilder();
        for (ReviewResult result : results) {
            Map<String, String> rowPlaceholders = Map.of(
                "displayName", result.getAgentConfig().getDisplayName());
            tableRowsBuilder.append(templateService.getFallbackAgentRow(rowPlaceholders));
        }
        
        // Build agent summaries using templates
        StringBuilder agentSummariesBuilder = new StringBuilder();
        for (ReviewResult result : results) {
            if (result.isSuccess()) {
                Map<String, String> successPlaceholders = Map.of(
                    "displayName", result.getAgentConfig().getDisplayName());
                agentSummariesBuilder.append(templateService.getFallbackAgentSuccess(successPlaceholders));
            } else {
                Map<String, String> failurePlaceholders = Map.of(
                    "displayName", result.getAgentConfig().getDisplayName(),
                    "errorMessage", result.getErrorMessage() != null ? result.getErrorMessage() : "");
                agentSummariesBuilder.append(templateService.getFallbackAgentFailure(failurePlaceholders));
            }
            agentSummariesBuilder.append("\n");
        }
        
        // Apply fallback summary template
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("tableRows", tableRowsBuilder.toString());
        placeholders.put("agentSummaries", agentSummariesBuilder.toString());
        
        return templateService.getFallbackSummaryTemplate(placeholders);
    }
    
    private String buildSummaryPrompt(List<ReviewResult> results, String repository) {
        // Build results section using templates
        StringBuilder resultsSection = new StringBuilder();
        for (ReviewResult result : results) {
            if (result.isSuccess()) {
                Map<String, String> entryPlaceholders = Map.of(
                    "displayName", result.getAgentConfig().getDisplayName(),
                    "content", result.getContent() != null ? result.getContent() : "");
                resultsSection.append(templateService.getSummaryResultEntry(entryPlaceholders));
            } else {
                Map<String, String> errorPlaceholders = Map.of(
                    "displayName", result.getAgentConfig().getDisplayName(),
                    "errorMessage", result.getErrorMessage() != null ? result.getErrorMessage() : "");
                resultsSection.append(templateService.getSummaryResultErrorEntry(errorPlaceholders));
            }
        }
        
        // Apply summary prompt template
        Map<String, String> placeholders = Map.of(
            "repository", repository,
            "results", resultsSection.toString());
        return templateService.getSummaryUserPrompt(placeholders);
    }
    
    private String buildFinalReport(String summaryContent, String repository, 
                                     List<ReviewResult> results) {
        // Build individual report links using template
        StringBuilder reportLinksBuilder = new StringBuilder();
        for (ReviewResult result : results) {
            String filename = String.format("%s_%s.md", 
                result.getAgentConfig().getName(),
                LocalDate.now().format(FILE_DATE_FORMATTER));
            Map<String, String> linkPlaceholders = Map.of(
                "displayName", result.getAgentConfig().getDisplayName(),
                "filename", filename);
            reportLinksBuilder.append(templateService.getReportLinkEntry(linkPlaceholders));
        }
        
        // Apply executive summary template
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("date", LocalDate.now().format(DATE_FORMATTER));
        placeholders.put("repository", repository);
        placeholders.put("agentCount", String.valueOf(results.size()));
        placeholders.put("successCount", String.valueOf(results.stream().filter(ReviewResult::isSuccess).count()));
        placeholders.put("failureCount", String.valueOf(results.stream().filter(r -> !r.isSuccess()).count()));
        placeholders.put("summaryContent", summaryContent);
        placeholders.put("reportLinks", reportLinksBuilder.toString());
        
        return templateService.getExecutiveSummaryTemplate(placeholders);
    }
    
    private void ensureOutputDirectory() throws IOException {
        if (!Files.exists(outputDirectory)) {
            Files.createDirectories(outputDirectory);
        }
    }
}
