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
import java.util.List;
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
    private final SummaryPromptBuilder summaryPromptBuilder;
    private final FallbackSummaryBuilder fallbackSummaryBuilder;
    private final SummaryFinalReportFormatter summaryFinalReportFormatter;
    
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
        this.summaryPromptBuilder = new SummaryPromptBuilder(
            templateService,
            MAX_CONTENT_PER_AGENT,
            MAX_TOTAL_PROMPT_CONTENT);
        this.fallbackSummaryBuilder = new FallbackSummaryBuilder(
            templateService,
            FALLBACK_EXCERPT_LENGTH);
        this.summaryFinalReportFormatter = new SummaryFinalReportFormatter(templateService);
    }
    
    /// Generates an executive summary from all review results.
    /// @param results List of review results from all agents
    /// @param repository The repository that was reviewed
    /// @return Path to the generated summary file
    public Path generateSummary(List<ReviewResult> results, String repository) throws IOException {
        ensureOutputDirectory();

        String date = currentDate();
        String filename = "executive_summary_%s.md".formatted(date);
        Path summaryPath = outputDirectory.resolve(filename);
        
        logger.info("Generating executive summary from {} review results", results.size());
        
        // Build the summary using AI
        String summaryContent = buildSummaryWithAI(results, repository);
        
        // Build the final report
        String finalReport = summaryFinalReportFormatter.format(summaryContent, repository, results, date);
        Files.writeString(summaryPath, finalReport);
        
        logger.info("Generated executive summary: {}", summaryPath);
        return summaryPath;
    }
    
    private String buildSummaryWithAI(List<ReviewResult> results, String repository) {
        // Create a new session for summary generation
        logger.info("Using model for summary: {}", summaryModel);
        var sessionConfig = createSummarySessionConfig();

        long timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes);

        try (CopilotSession session = client.createSession(sessionConfig)
            .get(timeoutMinutes, TimeUnit.MINUTES)) {
            String prompt = summaryPromptBuilder.buildSummaryPrompt(results, repository);
            // Pass the configured timeout to sendAndWait explicitly.
            // The SDK default (60s) is too short for summarizing large review results.
            var response = session
                .sendAndWait(new MessageOptions().setPrompt(prompt), timeoutMs)
                .get(timeoutMinutes, TimeUnit.MINUTES);
            String content = response.getData().getContent();
            if (content == null || content.isBlank()) {
                return fallbackSummary(results, "AI summary response was empty");
            }
            return content;
        } catch (ExecutionException e) {
            return fallbackSummary(results,
                "Failed to create or execute summary session: " + e.getMessage());
        } catch (TimeoutException ex) {
            return fallbackSummary(results, "Summary generation timed out: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CopilotCliException("Summary generation interrupted", ex);
        }
    }

    private SessionConfig createSummarySessionConfig() {
        String systemPrompt = templateService.getSummarySystemPrompt();
        var sessionConfig = new SessionConfig()
            .setModel(summaryModel)
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.REPLACE)
                .setContent(systemPrompt));

        applyReasoningEffort(sessionConfig);
        return sessionConfig;
    }

    private void applyReasoningEffort(SessionConfig sessionConfig) {
        String effort = ModelConfig.resolveReasoningEffort(summaryModel, reasoningEffort);
        if (effort != null) {
            logger.info("Setting reasoning effort '{}' for model: {}", effort, summaryModel);
            sessionConfig.setReasoningEffort(effort);
        }
    }

    private String fallbackSummary(List<ReviewResult> results, String reason) {
        logger.warn("{}, using fallback summary", reason);
        return fallbackSummaryBuilder.buildFallbackSummary(results);
    }

    private String currentDate() {
        return LocalDate.now().format(DATE_FORMATTER);
    }
    
    private void ensureOutputDirectory() throws IOException {
        ReportFileUtils.ensureOutputDirectory(outputDirectory);
    }
}
