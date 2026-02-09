package dev.logicojp.reviewer;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.cli.CliParsing;
import dev.logicojp.reviewer.cli.CliUsage;
import dev.logicojp.reviewer.cli.CliValidationException;
import dev.logicojp.reviewer.cli.ExitCodes;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.instruction.CustomInstructionLoader;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.service.AgentService;
import dev.logicojp.reviewer.service.CopilotService;
import dev.logicojp.reviewer.service.ReportService;
import dev.logicojp.reviewer.service.ReviewService;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.GitHubTokenResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main review command that executes the multi-agent code review.
 */
@Singleton
public class ReviewCommand {

    private static final Logger logger = LoggerFactory.getLogger(ReviewCommand.class);

    private final AgentService agentService;

    private final CopilotService copilotService;

    private final ReviewService reviewService;

    private final ReportService reportService;

    private final ModelConfig defaultModelConfig;

    private final ExecutionConfig executionConfig;

    private int exitCode = ExitCodes.OK;

    /**
     * Target selection - either GitHub repository or local directory.
     */
    static class TargetSelection {
        private String repository;
        private Path localDirectory;
    }

    private TargetSelection targetSelection;

    static class AgentSelection {
        private boolean allAgents;
        private List<String> agents;
    }

    private AgentSelection agentSelection;

    private Path outputDirectory;

    private List<Path> additionalAgentDirs;

    private String githubToken;

    private int parallelism;

    private boolean noSummary;

    // LLM Model options
    private String reviewModel;

    private String reportModel;

    private String summaryModel;

    private String defaultModel;

    // Custom instruction options
    private List<Path> instructionPaths;

    private boolean noInstructions;

    private boolean helpRequested;

    @Inject
    public ReviewCommand(
        AgentService agentService,
        CopilotService copilotService,
        ReviewService reviewService,
        ReportService reportService,
        ModelConfig defaultModelConfig,
        ExecutionConfig executionConfig
    ) {
        this.agentService = agentService;
        this.copilotService = copilotService;
        this.reviewService = reviewService;
        this.reportService = reportService;
        this.defaultModelConfig = defaultModelConfig;
        this.executionConfig = executionConfig;
    }

    public int execute(String[] args) {
        resetDefaults();
        try {
            parseArgs(args);
            if (helpRequested) {
                return ExitCodes.OK;
            }
            executeInternal();
        } catch (CliValidationException e) {
            exitCode = ExitCodes.USAGE;
            System.err.println(e.getMessage());
            if (e.showUsage()) {
                CliUsage.printRun(System.err);
            }
        } catch (Exception e) {
            exitCode = ExitCodes.SOFTWARE;
            logger.error("Execution failed: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
        }
        return exitCode;
    }

    private void resetDefaults() {
        exitCode = ExitCodes.OK;
        targetSelection = null;
        agentSelection = null;
        outputDirectory = Path.of("./report");
        additionalAgentDirs = new ArrayList<>();
        githubToken = System.getenv("GITHUB_TOKEN");
        parallelism = executionConfig.parallelism();
        noSummary = false;
        reviewModel = null;
        reportModel = null;
        summaryModel = null;
        defaultModel = null;
        instructionPaths = new ArrayList<>();
        noInstructions = false;
        helpRequested = false;
    }

    private void parseArgs(String[] args) {
        if (args == null) {
            args = new String[0];
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> {
                    CliUsage.printRun(System.out);
                    helpRequested = true;
                    return;
                }
                case "-r", "--repo" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--repo");
                    i = value.newIndex();
                    ensureTargetSelection().repository = value.value();
                }
                case "-l", "--local" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--local");
                    i = value.newIndex();
                    ensureTargetSelection().localDirectory = Path.of(value.value());
                }
                case "--all" -> ensureAgentSelection().allAgents = true;
                case "-a", "--agents" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--agents");
                    i = value.newIndex();
                    List<String> agents = CliParsing.splitComma(value.value());
                    if (agents.isEmpty()) {
                        throw new CliValidationException("--agents requires at least one value", true);
                    }
                    AgentSelection selection = ensureAgentSelection();
                    if (selection.agents == null) {
                        selection.agents = new ArrayList<>();
                    }
                    selection.agents.addAll(agents);
                }
                case "-o", "--output" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--output");
                    i = value.newIndex();
                    outputDirectory = Path.of(value.value());
                }
                case "--agents-dir" -> {
                    CliParsing.MultiValue values = CliParsing.readMultiValues(arg, args, i, "--agents-dir");
                    i = values.newIndex();
                    for (String path : values.values()) {
                        additionalAgentDirs.add(Path.of(path));
                    }
                }
                case "--token" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--token");
                    i = value.newIndex();
                    githubToken = value.value();
                }
                case "--parallelism" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--parallelism");
                    i = value.newIndex();
                    parallelism = parseInt(value.value(), "--parallelism");
                }
                case "--no-summary" -> noSummary = true;
                case "--review-model" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--review-model");
                    i = value.newIndex();
                    reviewModel = value.value();
                }
                case "--report-model" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--report-model");
                    i = value.newIndex();
                    reportModel = value.value();
                }
                case "--summary-model" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--summary-model");
                    i = value.newIndex();
                    summaryModel = value.value();
                }
                case "--model" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--model");
                    i = value.newIndex();
                    defaultModel = value.value();
                }
                case "--instructions" -> {
                    CliParsing.MultiValue values = CliParsing.readMultiValues(arg, args, i, "--instructions");
                    i = values.newIndex();
                    for (String path : values.values()) {
                        instructionPaths.add(Path.of(path));
                    }
                }
                case "--no-instructions" -> noInstructions = true;
                default -> {
                    if (arg.startsWith("-")) {
                        throw new CliValidationException("Unknown option: " + arg, true);
                    }
                    throw new CliValidationException("Unexpected argument: " + arg, true);
                }
            }
        }

        validateSelections();
    }

    private TargetSelection ensureTargetSelection() {
        if (targetSelection == null) {
            targetSelection = new TargetSelection();
        }
        return targetSelection;
    }

    private AgentSelection ensureAgentSelection() {
        if (agentSelection == null) {
            agentSelection = new AgentSelection();
        }
        return agentSelection;
    }

    private void validateSelections() {
        if (agentSelection == null) {
            throw new CliValidationException("Either --all or --agents must be specified.", true);
        }
        boolean hasAll = agentSelection.allAgents;
        boolean hasAgents = agentSelection.agents != null && !agentSelection.agents.isEmpty();
        if (hasAll == hasAgents) {
            throw new CliValidationException("Specify either --all or --agents (not both).", true);
        }
        if (targetSelection == null) {
            throw new CliValidationException("Either --repo or --local must be specified.", true);
        }
        boolean hasRepo = targetSelection.repository != null && !targetSelection.repository.isBlank();
        boolean hasLocal = targetSelection.localDirectory != null;
        if (hasRepo == hasLocal) {
            throw new CliValidationException("Specify either --repo or --local (not both).", true);
        }
    }

    private int parseInt(String value, String optionName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new CliValidationException("Invalid value for " + optionName + ": " + value, true);
        }
    }

    private void executeInternal() throws Exception {
        // Build review target
        ReviewTarget target;
        String resolvedToken = null;
        if (targetSelection.repository != null) {
            GitHubTokenResolver tokenResolver = new GitHubTokenResolver(executionConfig.ghAuthTimeoutSeconds());
            resolvedToken = tokenResolver.resolve(githubToken).orElse(null);
            target = ReviewTarget.gitHub(targetSelection.repository);

            // Validate GitHub token for repository access
            if (resolvedToken == null || resolvedToken.isBlank()) {
                throw new CliValidationException(
                    "GitHub token is required for repository review. Set GITHUB_TOKEN, use --token, or login with `gh auth login`.",
                    true
                );
            }
        } else if (targetSelection.localDirectory != null) {
            Path localPath = targetSelection.localDirectory.toAbsolutePath();
            if (!Files.exists(localPath)) {
                throw new CliValidationException(
                    "Local directory does not exist: " + localPath,
                    true
                );
            }
            if (!Files.isDirectory(localPath)) {
                throw new CliValidationException(
                    "Path is not a directory: " + localPath,
                    true
                );
            }
            target = ReviewTarget.local(localPath);
        } else {
            throw new CliValidationException("Either --repo or --local must be specified.", true);
        }

        // Build model configuration
        ModelConfig modelConfig = buildModelConfig();

        // Configure agent directories
        List<Path> agentDirs = agentService.buildAgentDirectories(additionalAgentDirs);

        // Load agent configurations
        Map<String, AgentConfig> agentConfigs;
        if (agentSelection.allAgents) {
            agentConfigs = agentService.loadAllAgents(agentDirs);
        } else {
            agentConfigs = agentService.loadAgents(agentDirs, agentSelection.agents);
        }

        if (agentConfigs.isEmpty()) {
            exitCode = ExitCodes.SOFTWARE;
            System.err.println("Error: No agents found. Check the agents directories:");
            for (Path dir : agentDirs) {
                System.err.println("  - " + dir);
            }
            return;
        }

        // Apply model overrides if specified
        if (reviewModel != null) {
            for (Map.Entry<String, AgentConfig> entry : agentConfigs.entrySet()) {
                entry.setValue(entry.getValue().withModel(reviewModel));
            }
        }

        printBanner(agentConfigs, agentDirs, modelConfig, target);

        // Load custom instructions
        String customInstruction = loadCustomInstructions(target);


        // Execute reviews using the Copilot service
        copilotService.initialize(resolvedToken);

        try {
            System.out.println("Starting reviews...");
            List<ReviewResult> results = reviewService.executeReviews(
                agentConfigs, target, resolvedToken, parallelism,
                customInstruction, modelConfig.reasoningEffort());

            // Generate individual reports
            System.out.println("\nGenerating reports...");
            List<Path> reports = reportService.generateReports(results, outputDirectory);

            for (Path report : reports) {
                System.out.println("  ✓ " + report.getFileName());
            }

            // Generate executive summary
            if (!noSummary) {
                System.out.println("\nGenerating executive summary...");
                Path summaryPath = reportService.generateSummary(
                    results, target.getDisplayName(), outputDirectory,
                    modelConfig.summaryModel(), modelConfig.reasoningEffort());
                System.out.println("  ✓ " + summaryPath.getFileName());
            }

            // Print summary
            printCompletionSummary(results);

        } finally {
            copilotService.shutdown();
        }
    }

    private ModelConfig buildModelConfig() {
        ModelConfig baseConfig = defaultModelConfig != null ? defaultModelConfig : new ModelConfig();
        ModelConfig.Builder builder = ModelConfig.builder()
            .reviewModel(baseConfig.reviewModel())
            .reportModel(baseConfig.reportModel())
            .summaryModel(baseConfig.summaryModel());

        if (defaultModel != null) {
            builder.defaultModel(defaultModel);
        }
        if (reviewModel != null) {
            builder.reviewModel(reviewModel);
        }
        if (reportModel != null) {
            builder.reportModel(reportModel);
        }
        if (summaryModel != null) {
            builder.summaryModel(summaryModel);
        }

        return builder.build();
    }

    /**
     * Loads custom instructions from specified paths or target directory.
     */
    private String loadCustomInstructions(ReviewTarget target) {
        if (noInstructions) {
            logger.info("Custom instructions disabled by --no-instructions flag");
            return null;
        }

        StringBuilder combined = new StringBuilder();

        // Load from explicitly specified paths
        if (instructionPaths != null && !instructionPaths.isEmpty()) {
            for (Path path : instructionPaths) {
                try {
                    if (Files.exists(path) && Files.isRegularFile(path)) {
                        String content = Files.readString(path);
                        if (!content.isBlank()) {
                            if (!combined.isEmpty()) {
                                combined.append("\n\n---\n\n");
                            }
                            combined.append("<!-- Source: ").append(path).append(" -->\n");
                            combined.append(content.trim());
                            System.out.println("  ✓ Loaded instructions: " + path);
                        }
                    } else {
                        logger.warn("Instruction file not found: {}", path);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to read instruction file {}: {}", path, e.getMessage());
                }
            }
        }

        // Also try to load from target directory (for local targets)
        if (target.isLocal()) {
            CustomInstructionLoader loader = new CustomInstructionLoader();
            loader.loadForTarget(target).ifPresent(instruction -> {
                if (!combined.isEmpty()) {
                    combined.append("\n\n---\n\n");
                }
                combined.append(instruction.content());
                System.out.println("  ✓ Loaded instructions from target: " + instruction.sourcePath());
            });
        }

        String result = combined.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private void printBanner(Map<String, AgentConfig> agentConfigs,
                             List<Path> agentDirs, ModelConfig modelConfig,
                             ReviewTarget target) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           Multi-Agent Code Reviewer                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Target: " + target.getDisplayName() +
            (target.isLocal() ? " (local)" : " (GitHub)"));
        System.out.println("Agents: " + agentConfigs.keySet());
        System.out.println("Output: " + outputDirectory.toAbsolutePath());
        System.out.println();
        System.out.println("Agent directories:");
        for (Path dir : agentDirs) {
            System.out.println("  - " + dir + (Files.exists(dir) ? "" : " (not found)"));
        }
        System.out.println();
        System.out.println("Models:");
        System.out.println("  Review: " + (reviewModel != null ? reviewModel : "(agent default)"));
        System.out.println("  Summary: " + modelConfig.summaryModel());
        System.out.println();
    }

    private void printCompletionSummary(List<ReviewResult> results) {
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println("Review completed!");
        System.out.println("  Total agents: " + results.size());
        System.out.println("  Successful: " + results.stream().filter(ReviewResult::isSuccess).count());
        System.out.println("  Failed: " + results.stream().filter(r -> !r.isSuccess()).count());
        System.out.println("  Reports: " + outputDirectory.toAbsolutePath());
        System.out.println("════════════════════════════════════════════════════════════");
    }
}
