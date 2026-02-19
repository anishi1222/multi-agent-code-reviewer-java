package dev.logicojp.reviewer.report.summary;

import dev.logicojp.reviewer.report.util.ReportFileUtils;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.formatter.SummaryFinalReportFormatter;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.SystemMessageMode;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SystemMessageConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.config.SummaryConfig;
import dev.logicojp.reviewer.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.logicojp.reviewer.service.CopilotCliException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/// Generates executive summary by aggregating all agent review results.
/// All prompt/template content is loaded from external templates via {@link TemplateService}.
public class SummaryGenerator {

    @FunctionalInterface
    interface AiSummaryBuilder {
        String build(List<ReviewResult> results, String repository);
    }

    /// Groups collaborator dependencies for testability.
    /// Use {@link SummaryCollaborators#defaults} to create production instances.
    record SummaryCollaborators(
        SummaryPromptBuilder summaryPromptBuilder,
        FallbackSummaryBuilder fallbackSummaryBuilder,
        SummaryFinalReportFormatter summaryFinalReportFormatter,
        AiSummaryBuilder aiSummaryBuilder
    ) {
        static SummaryCollaborators defaults(TemplateService templateService,
                                              SummaryConfig summaryConfig,
                                              SummaryGenerator generator) {
            SummaryConfig effective = summaryConfig != null ? summaryConfig : new SummaryConfig(0, 0, 0, 0, 0, 0);
            return new SummaryCollaborators(
                new SummaryPromptBuilder(templateService,
                    effective.maxContentPerAgent(), effective.maxTotalPromptContent(),
                    effective.averageResultContentEstimate(), effective.initialBufferMargin()),
                new FallbackSummaryBuilder(templateService, effective.fallbackExcerptLength(),
                    effective.excerptNormalizationMultiplier()),
                new SummaryFinalReportFormatter(templateService),
                generator::buildSummaryWithAI
            );
        }

        /// Merges this collaborators instance with defaults, filling in null fields.
        SummaryCollaborators withDefaults(SummaryCollaborators defaults) {
            return new SummaryCollaborators(
                summaryPromptBuilder != null ? summaryPromptBuilder : defaults.summaryPromptBuilder(),
                fallbackSummaryBuilder != null ? fallbackSummaryBuilder : defaults.fallbackSummaryBuilder(),
                summaryFinalReportFormatter != null ? summaryFinalReportFormatter : defaults.summaryFinalReportFormatter(),
                aiSummaryBuilder != null ? aiSummaryBuilder : defaults.aiSummaryBuilder()
            );
        }
    }
    
    private static final Logger logger = LoggerFactory.getLogger(SummaryGenerator.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final Pattern INVOCATION_TIMESTAMP_PATTERN =
        Pattern.compile("\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}");
    
    private final Path outputDirectory;
    private final CopilotClient client;
    private final String summaryModel;
    private final String reasoningEffort;
    private final long timeoutMinutes;
    private final TemplateService templateService;
    private final String invocationTimestamp;
    private final SummaryPromptBuilder summaryPromptBuilder;
    private final FallbackSummaryBuilder fallbackSummaryBuilder;
    private final SummaryFinalReportFormatter summaryFinalReportFormatter;
    private final AiSummaryBuilder aiSummaryBuilder;
    
    public SummaryGenerator(
            Path outputDirectory, 
            CopilotClient client, 
            String summaryModel,
            String reasoningEffort,
            long timeoutMinutes,
            TemplateService templateService,
            SummaryConfig summaryConfig) {
        this(outputDirectory, client, summaryModel, reasoningEffort, timeoutMinutes, templateService,
            summaryConfig, null, Clock.systemDefaultZone());
    }

    /// Full-parameter constructor for testing — all collaborators are injectable.
    SummaryGenerator(
            Path outputDirectory,
            CopilotClient client,
            String summaryModel,
            String reasoningEffort,
            long timeoutMinutes,
            TemplateService templateService,
            SummaryConfig summaryConfig,
            SummaryCollaborators collaborators,
            Clock clock) {
        this.outputDirectory = outputDirectory;
        this.client = client;
        this.summaryModel = summaryModel;
        this.reasoningEffort = reasoningEffort;
        this.timeoutMinutes = timeoutMinutes;
        this.templateService = templateService;
        this.invocationTimestamp = LocalDateTime.now(clock).format(TIMESTAMP_FORMATTER);
        SummaryCollaborators defaults = SummaryCollaborators.defaults(templateService, summaryConfig, this);
        var effective = (collaborators != null ? collaborators : defaults).withDefaults(defaults);
        this.summaryPromptBuilder = effective.summaryPromptBuilder();
        this.fallbackSummaryBuilder = effective.fallbackSummaryBuilder();
        this.summaryFinalReportFormatter = effective.summaryFinalReportFormatter();
        this.aiSummaryBuilder = effective.aiSummaryBuilder();
    }
    
    /// Generates an executive summary from all review results.
    /// @param results List of review results from all agents
    /// @param repository The repository that was reviewed
    /// @return Path to the generated summary file
    public Path generateSummary(List<ReviewResult> results, String repository) throws IOException {
        Path summaryOutputDirectory = resolveSummaryOutputDirectory();
        ensureOutputDirectory(summaryOutputDirectory);

        String timestamp = invocationTimestamp;
        String filename = "executive_summary_%s.md".formatted(timestamp);
        Path summaryPath = summaryOutputDirectory.resolve(filename);
        
        logger.info("Generating executive summary from {} review results", results.size());
        
        // Build the summary using AI
        String summaryContent = aiSummaryBuilder.build(results, repository);
        
        // Build the final report
        String finalReport = summaryFinalReportFormatter.format(summaryContent, repository, results, timestamp);
        ReportFileUtils.writeSecureString(summaryPath, finalReport);
        
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
            String content = response.getData().content();
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

    private Path resolveSummaryOutputDirectory() {
        Path invocationDirectory = outputDirectory.getFileName();
        if (invocationDirectory == null) {
            return outputDirectory;
        }
        if (!INVOCATION_TIMESTAMP_PATTERN.matcher(invocationDirectory.toString()).matches()) {
            return outputDirectory;
        }
        Path parent = outputDirectory.getParent();
        return parent != null ? parent : outputDirectory;
    }

    private void ensureOutputDirectory(Path directory) throws IOException {
        ReportFileUtils.ensureOutputDirectory(directory);
    }
}
