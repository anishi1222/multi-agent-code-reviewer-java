package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.service.ReportService;
import dev.logicojp.reviewer.service.ReviewService;
import dev.logicojp.reviewer.target.ReviewTarget;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/// Executes the review run lifecycle: review execution, report generation, summary generation.
@Singleton
public class ReviewRunExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ReviewRunExecutor.class);

    private final ReviewService reviewService;
    private final ReportService reportService;
    private final ReviewOutputFormatter outputFormatter;
    private final CliOutput output;

    @Inject
    public ReviewRunExecutor(ReviewService reviewService,
                             ReportService reportService,
                             ReviewOutputFormatter outputFormatter,
                             CliOutput output) {
        this.reviewService = reviewService;
        this.reportService = reportService;
        this.outputFormatter = outputFormatter;
        this.output = output;
    }

    public int execute(ReviewRunRequest context) {
        output.println("Starting reviews...");
        List<ReviewResult> results = executeReviews(context);
        generateOutputs(results, context);

        outputFormatter.printCompletionSummary(results, context.outputDirectory());
        return ExitCodes.OK;
    }

    private List<ReviewResult> executeReviews(ReviewRunRequest context) {
        return reviewService.executeReviews(
            context.agentConfigs(),
            context.target(),
            context.resolvedToken(),
            context.parallelism(),
            context.customInstructions(),
            context.reasoningEffort()
        );
    }

    private void generateOutputs(List<ReviewResult> results, ReviewRunRequest context) {
        generateReports(results, context.outputDirectory());
        generateSummaryIfEnabled(results, context);
    }

    private void generateSummaryIfEnabled(List<ReviewResult> results, ReviewRunRequest context) {
        if (shouldGenerateSummary(context)) {
            generateSummary(results, context);
        }
    }

    private void generateReports(List<ReviewResult> results, Path outputDirectory) {
        output.println("\nGenerating reports...");
        List<Path> reports;
        try {
            reports = reportService.generateReports(results, outputDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Report generation failed", e);
        }
        for (Path report : reports) {
            output.println("  ✓ " + report.getFileName());
        }
    }

    private boolean shouldGenerateSummary(ReviewRunRequest context) {
        return !context.noSummary();
    }

    private void generateSummary(List<ReviewResult> results, ReviewRunRequest context) {
        output.println("\nGenerating executive summary...");
        try {
            Path summaryPath = reportService.generateSummary(
                results,
                context.target().displayName(),
                context.outputDirectory(),
                context.summaryModel(),
                context.reasoningEffort());
            output.println("  ✓ " + summaryPath.getFileName());
        } catch (IOException e) {
            logger.error("Summary generation failed: {}", e.getMessage(), e);
            output.errorln("Warning: Summary generation failed: " + e.getMessage());
        }
    }

    public record ReviewRunRequest(
        ReviewTarget target,
        String resolvedToken,
        String summaryModel,
        String reasoningEffort,
        Map<String, AgentConfig> agentConfigs,
        int parallelism,
        boolean noSummary,
        List<CustomInstruction> customInstructions,
        Path outputDirectory
    ) {
    }
}
