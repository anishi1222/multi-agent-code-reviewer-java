package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.report.core.ReviewResult;
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
class ReviewRunExecutor {

    @FunctionalInterface
    interface ReviewRunner {
        List<ReviewResult> run(String resolvedToken, ReviewRunRequest context);
    }

    @FunctionalInterface
    interface ReportsGenerator {
        List<Path> generate(List<ReviewResult> results, Path outputDirectory) throws IOException;
    }

    @FunctionalInterface
    interface SummaryGeneratorRunner {
        Path generate(List<ReviewResult> results, ReviewRunRequest context) throws IOException;
    }

    private static final Logger logger = LoggerFactory.getLogger(ReviewRunExecutor.class);

    private final ReviewOutputFormatter outputFormatter;
    private final CliOutput output;
    private final ReviewRunner reviewRunner;
    private final ReportsGenerator reportsGenerator;
    private final SummaryGeneratorRunner summaryGeneratorRunner;

    @Inject
    public ReviewRunExecutor(ReviewService reviewService,
                             ReportService reportService,
                             ReviewOutputFormatter outputFormatter,
                             CliOutput output) {
        this(
            reviewService,
            reportService,
            outputFormatter,
            output,
            (resolvedToken, context) -> reviewService.executeReviews(
                context.agentConfigs(),
                context.target(),
                resolvedToken,
                context.parallelism(),
                context.customInstructions(),
                context.reasoningEffort()
            ),
            reportService::generateReports,
            (results, context) -> reportService.generateSummary(
                results,
                context.target().displayName(),
                context.outputDirectory(),
                context.summaryModel(),
                context.reasoningEffort()
            )
        );
    }

    ReviewRunExecutor(ReviewService reviewService,
                      ReportService reportService,
                      ReviewOutputFormatter outputFormatter,
                      CliOutput output,
                      ReviewRunner reviewRunner,
                      ReportsGenerator reportsGenerator,
                      SummaryGeneratorRunner summaryGeneratorRunner) {
        this.outputFormatter = outputFormatter;
        this.output = output;
        this.reviewRunner = reviewRunner;
        this.reportsGenerator = reportsGenerator;
        this.summaryGeneratorRunner = summaryGeneratorRunner;
    }

    public int execute(String resolvedToken, ReviewRunRequest context) {
        output.println("Starting reviews...");
        List<ReviewResult> results = executeReviews(resolvedToken, context);
        generateOutputs(results, context);

        outputFormatter.printCompletionSummary(results, context.outputDirectory());
        return ExitCodes.OK;
    }

    private List<ReviewResult> executeReviews(String resolvedToken, ReviewRunRequest context) {
        return reviewRunner.run(resolvedToken, context);
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
            reports = reportsGenerator.generate(results, outputDirectory);
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
            Path summaryPath = summaryGeneratorRunner.generate(results, context);
            output.println("  ✓ " + summaryPath.getFileName());
        } catch (IOException e) {
            logger.error("Summary generation failed: {}", e.getMessage(), e);
            output.errorln("Warning: Summary generation failed: " + e.getMessage());
        }
    }

    public record ReviewRunRequest(
        ReviewTarget target,
        String summaryModel,
        String reasoningEffort,
        Map<String, AgentConfig> agentConfigs,
        int parallelism,
        boolean noSummary,
        List<CustomInstruction> customInstructions,
        Path outputDirectory
    ) {
        @Override
        public String toString() {
            return "ReviewRunRequest{target=%s, summaryModel='%s', reasoningEffort='%s', parallelism=%d, noSummary=%s, outputDirectory=%s}"
                .formatted(target, summaryModel, reasoningEffort, parallelism, noSummary, outputDirectory);
        }
    }
}
