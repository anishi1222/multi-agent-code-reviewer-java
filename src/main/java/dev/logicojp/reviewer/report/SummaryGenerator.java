package dev.logicojp.reviewer.report;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.SystemMessageMode;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SystemMessageConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.logicojp.reviewer.service.CopilotCliException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Generates executive summary by aggregating all agent review results.
/// All prompt/template content is loaded from external templates via {@link TemplateService}.
public class SummaryGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(SummaryGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_CONTENT_PER_AGENT = 50_000;
    private static final int MAX_TOTAL_PROMPT_CONTENT = 200_000;
    private static final int FALLBACK_EXCERPT_LENGTH = 180;
    
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
    
    /// Generates an executive summary from all review results.
    /// @param results List of review results from all agents
    /// @param repository The repository that was reviewed
    /// @return Path to the generated summary file
    public Path generateSummary(List<ReviewResult> results, String repository) throws IOException {
        ensureOutputDirectory();
        
        String filename = "executive_summary_%s.md".formatted(
            LocalDate.now().format(DATE_FORMATTER));
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
    
    private String buildSummaryWithAI(List<ReviewResult> results, String repository) {
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
        String effort = ModelConfig.resolveReasoningEffort(summaryModel, reasoningEffort);
        if (effort != null) {
            logger.info("Setting reasoning effort '{}' for model: {}", effort, summaryModel);
            sessionConfig.setReasoningEffort(effort);
        }

        CopilotSession session;
        try {
            session = client.createSession(sessionConfig)
                .get(timeoutMinutes, TimeUnit.MINUTES);
        } catch (ExecutionException | TimeoutException e) {
            logger.error("Failed to create summary session: {}", e.getMessage());
            return buildFallbackSummary(results);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CopilotCliException("Summary session creation interrupted", e);
        }
        long timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes);
        
        try {
            String prompt = buildSummaryPrompt(results, repository);
            // Pass the configured timeout to sendAndWait explicitly.
            // The SDK default (60s) is too short for summarizing large review results.
            var response = session
                .sendAndWait(new MessageOptions().setPrompt(prompt), timeoutMs)
                .get(timeoutMinutes, TimeUnit.MINUTES);
            String content = response.getData().getContent();
            if (content == null || content.isBlank()) {
                logger.warn("AI summary response was empty, using fallback summary");
                return buildFallbackSummary(results);
            }
            return content;
        } catch (TimeoutException ex) {
            logger.error("Summary generation timed out: {}", ex.getMessage());
            return buildFallbackSummary(results);
        } catch (ExecutionException ex) {
            logger.error("Summary generation failed: {}", ex.getMessage());
            return buildFallbackSummary(results);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CopilotCliException("Summary generation interrupted", ex);
        } finally {
            session.close();
        }
    }

    private String buildFallbackSummary(List<ReviewResult> results) {
        // Build table rows using template
        var tableRowsBuilder = new StringBuilder();
        for (ReviewResult result : results) {
            tableRowsBuilder.append(templateService.getFallbackAgentRow(
                Map.of(
                    "displayName", result.agentConfig().displayName(),
                    "content", excerpt(result)
                )));
        }
        
        // Build agent summaries using templates
        var agentSummariesBuilder = new StringBuilder();
        for (ReviewResult result : results) {
            if (result.isSuccess()) {
                agentSummariesBuilder.append(templateService.getFallbackAgentSuccess(
                    Map.of(
                        "displayName", result.agentConfig().displayName(),
                        "content", excerpt(result)
                    )));
            } else {
                agentSummariesBuilder.append(templateService.getFallbackAgentFailure(
                    Map.of(
                        "displayName", result.agentConfig().displayName(),
                        "errorMessage", result.errorMessage() != null ? result.errorMessage() : ""
                    )));
            }
            agentSummariesBuilder.append("\n");
        }
        
        // Apply fallback summary template
        var placeholders = Map.of(
            "tableRows", tableRowsBuilder.toString(),
            "agentSummaries", agentSummariesBuilder.toString());
        
        return templateService.getFallbackSummaryTemplate(placeholders);
    }
    
    private String buildSummaryPrompt(List<ReviewResult> results, String repository) {
        var resultsSection = new StringBuilder(Math.min(results.size() * 8192, 4_000_000));
        int totalContentSize = 0;
        for (ReviewResult result : results) {
            if (result.isSuccess()) {
                String content = result.content() != null ? result.content() : "";
                int remaining = MAX_TOTAL_PROMPT_CONTENT - totalContentSize;
                if (remaining <= 0) {
                    break;
                }
                int maxAllowed = Math.min(MAX_CONTENT_PER_AGENT, remaining);
                if (content.length() > maxAllowed) {
                    content = content.substring(0, maxAllowed)
                        + "\n\n... (truncated for summary)";
                }
                totalContentSize += content.length();
                resultsSection.append(templateService.getSummaryResultEntry(
                    Map.of(
                        "displayName", result.agentConfig().displayName(),
                        "content", content
                    )));
            } else {
                resultsSection.append(templateService.getSummaryResultErrorEntry(
                    Map.of(
                        "displayName", result.agentConfig().displayName(),
                        "errorMessage", result.errorMessage() != null ? result.errorMessage() : ""
                    )));
            }
        }
        
        // Apply summary prompt template
        var placeholders = Map.of(
            "repository", repository,
            "results", resultsSection.toString());
        return templateService.getSummaryUserPrompt(placeholders);
    }

    private String excerpt(ReviewResult result) {
        if (result == null || !result.isSuccess() || result.content() == null || result.content().isBlank()) {
            return "N/A";
        }
        String normalized = result.content().replaceAll("\\s+", " ").trim();
        if (normalized.length() <= FALLBACK_EXCERPT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, FALLBACK_EXCERPT_LENGTH) + "...";
    }
    
    private String buildFinalReport(String summaryContent, String repository, 
                                     List<ReviewResult> results) {
        // Build individual report links using template
        var reportLinksBuilder = new StringBuilder();
        for (ReviewResult result : results) {
            String filename = "%s_%s.md".formatted(
                result.agentConfig().name(),
                LocalDate.now().format(DATE_FORMATTER));
            var linkPlaceholders = Map.of(
                "displayName", result.agentConfig().displayName(),
                "filename", filename);
            reportLinksBuilder.append(templateService.getReportLinkEntry(linkPlaceholders));
        }
        
        // Build deterministic findings summary from review results
        String findingsSummary = FindingsExtractor.buildFindingsSummary(results);
        if (findingsSummary.isEmpty()) {
            findingsSummary = "指摘事項はありません。";
        }
        
        // Apply executive summary template
        var placeholders = new HashMap<String, String>();
        placeholders.put("date", LocalDate.now().format(DATE_FORMATTER));
        placeholders.put("repository", repository);
        placeholders.put("agentCount", String.valueOf(results.size()));
        long successCount = results.stream().filter(ReviewResult::isSuccess).count();
        placeholders.put("successCount", String.valueOf(successCount));
        placeholders.put("failureCount", String.valueOf(results.size() - successCount));
        placeholders.put("summaryContent", summaryContent != null ? summaryContent : "");
        placeholders.put("findingsSummary", findingsSummary);
        placeholders.put("reportLinks", reportLinksBuilder.toString());
        
        return templateService.getExecutiveSummaryTemplate(placeholders);
    }
    
    private void ensureOutputDirectory() throws IOException {
        ReportFileUtils.ensureOutputDirectory(outputDirectory);
    }
}
