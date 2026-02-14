package dev.logicojp.reviewer;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.cli.CliParsing;
import dev.logicojp.reviewer.cli.CliUsage;
import dev.logicojp.reviewer.cli.CliValidationException;
import dev.logicojp.reviewer.cli.ExitCodes;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.instruction.CustomInstructionLoader;
import dev.logicojp.reviewer.instruction.InstructionSource;
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
import java.util.Objects;

/// Main review command that executes the multi-agent code review.
@Singleton
public class ReviewCommand {

    private static final Logger logger = LoggerFactory.getLogger(ReviewCommand.class);

    private final AgentService agentService;

    private final CopilotService copilotService;

    private final ReviewService reviewService;

    private final ReportService reportService;

    private final ModelConfig defaultModelConfig;

    private final ExecutionConfig executionConfig;

    /// Target selection — sealed interface for type-safe exclusive choice.
    sealed interface TargetSelection {
        record Repository(String repository) implements TargetSelection {}
        record LocalDirectory(Path directory) implements TargetSelection {}
    }

    /// Agent selection — sealed interface for type-safe exclusive choice.
    sealed interface AgentSelection {
        record All() implements AgentSelection {}
        record Named(List<String> agents) implements AgentSelection {}
    }

    /// Parsed CLI options for a review run.
    record ParsedOptions(
        TargetSelection target,
        AgentSelection agents,
        Path outputDirectory,
        List<Path> additionalAgentDirs,
        String githubToken,
        int parallelism,
        boolean noSummary,
        String reviewModel,
        String reportModel,
        String summaryModel,
        String defaultModel,
        List<Path> instructionPaths,
        boolean noInstructions,
        boolean noPrompts
    ) {}

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
        try {
            ParsedOptions options = parseArgs(args);
            if (options == null) {
                // --help was requested
                return ExitCodes.OK;
            }
            return executeInternal(options);
        } catch (CliValidationException e) {
            System.err.println(e.getMessage());
            if (e.showUsage()) {
                CliUsage.printRun(System.err);
            }
            return ExitCodes.USAGE;
        } catch (Exception e) {
            logger.error("Execution failed: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            return ExitCodes.SOFTWARE;
        }
    }

    private ParsedOptions parseArgs(String[] args) {
        args = Objects.requireNonNullElse(args, new String[0]);

        // Mutable accumulators during parsing
        String repository = null;
        Path localDirectory = null;
        boolean allAgents = false;
        List<String> agentNames = new ArrayList<>();
        Path outputDirectory = Path.of("./reports");
        List<Path> additionalAgentDirs = new ArrayList<>();
        String githubToken = System.getenv("GITHUB_TOKEN");
        int parallelism = executionConfig.parallelism();
        boolean noSummary = false;
        String reviewModel = null;
        String reportModel = null;
        String summaryModel = null;
        String defaultModel = null;
        List<Path> instructionPaths = new ArrayList<>();
        boolean noInstructions = false;
        boolean noPrompts = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> {
                    CliUsage.printRun(System.out);
                    return null;
                }
                case "-r", "--repo" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--repo");
                    i = value.newIndex();
                    repository = value.value();
                }
                case "-l", "--local" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--local");
                    i = value.newIndex();
                    localDirectory = Path.of(value.value());
                }
                case "--all" -> allAgents = true;
                case "-a", "--agents" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--agents");
                    i = value.newIndex();
                    List<String> parsed = CliParsing.splitComma(value.value());
                    if (parsed.isEmpty()) {
                        throw new CliValidationException("--agents requires at least one value", true);
                    }
                    agentNames.addAll(parsed);
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
                    String tokenValue = value.value();
                    if (!"-".equals(tokenValue)) {
                        System.err.println("WARNING: Token passed via command line is visible in process listings. "
                            + "Use '--token -' or GITHUB_TOKEN env var for safer input.");
                    }
                    githubToken = CliParsing.readToken(tokenValue);
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
                case "--no-prompts" -> noPrompts = true;
                default -> {
                    if (arg.startsWith("-")) {
                        throw new CliValidationException("Unknown option: " + arg, true);
                    }
                    throw new CliValidationException("Unexpected argument: " + arg, true);
                }
            }
        }

        // Validate and build target selection
        TargetSelection target = validateTargetSelection(repository, localDirectory);

        // Validate and build agent selection
        AgentSelection agents = validateAgentSelection(allAgents, agentNames);

        return new ParsedOptions(
            target, agents, outputDirectory, List.copyOf(additionalAgentDirs),
            githubToken, parallelism, noSummary,
            reviewModel, reportModel, summaryModel, defaultModel,
            List.copyOf(instructionPaths), noInstructions, noPrompts
        );
    }

    private static TargetSelection validateTargetSelection(String repository, Path localDirectory) {
        boolean hasRepo = repository != null && !repository.isBlank();
        boolean hasLocal = localDirectory != null;
        if (!hasRepo && !hasLocal) {
            throw new CliValidationException("Either --repo or --local must be specified.", true);
        }
        if (hasRepo && hasLocal) {
            throw new CliValidationException("Specify either --repo or --local (not both).", true);
        }
        return hasRepo
            ? new TargetSelection.Repository(repository)
            : new TargetSelection.LocalDirectory(localDirectory);
    }

    private static AgentSelection validateAgentSelection(boolean allAgents, List<String> agentNames) {
        boolean hasAgents = !agentNames.isEmpty();
        if (!allAgents && !hasAgents) {
            throw new CliValidationException("Either --all or --agents must be specified.", true);
        }
        if (allAgents && hasAgents) {
            throw new CliValidationException("Specify either --all or --agents (not both).", true);
        }
        return allAgents
            ? new AgentSelection.All()
            : new AgentSelection.Named(List.copyOf(agentNames));
    }

    private int parseInt(String value, String optionName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new CliValidationException("Invalid value for " + optionName + ": " + value, true);
        }
    }

    private int executeInternal(ParsedOptions options) throws Exception {
        // Build review target
        ReviewTarget target;
        String resolvedToken = null;
        switch (options.target()) {
            case TargetSelection.Repository(String repository) -> {
                GitHubTokenResolver tokenResolver = new GitHubTokenResolver(executionConfig.ghAuthTimeoutSeconds());
                resolvedToken = tokenResolver.resolve(options.githubToken()).orElse(null);
                target = ReviewTarget.gitHub(repository);

                if (resolvedToken == null || resolvedToken.isBlank()) {
                    throw new CliValidationException(
                        "GitHub token is required for repository review. Set GITHUB_TOKEN, use --token, or login with `gh auth login`.",
                        true
                    );
                }
            }
            case TargetSelection.LocalDirectory(Path localDir) -> {
                Path localPath = localDir.toAbsolutePath();
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
            }
        }

        // Build model configuration
        ModelConfig modelConfig = buildModelConfig(options);

        // Configure agent directories
        List<Path> agentDirs = agentService.buildAgentDirectories(options.additionalAgentDirs());

        // Load agent configurations
        Map<String, AgentConfig> agentConfigs = switch (options.agents()) {
            case AgentSelection.All() -> agentService.loadAllAgents(agentDirs);
            case AgentSelection.Named(List<String> names) -> agentService.loadAgents(agentDirs, names);
        };

        if (agentConfigs.isEmpty()) {
            System.err.println("Error: No agents found. Check the agents directories:");
            for (Path dir : agentDirs) {
                System.err.println("  - " + dir);
            }
            return ExitCodes.SOFTWARE;
        }

        // Apply model overrides if specified
        if (options.reviewModel() != null) {
            for (Map.Entry<String, AgentConfig> entry : agentConfigs.entrySet()) {
                entry.setValue(entry.getValue().withModel(options.reviewModel()));
            }
        }

        // Resolve output directory with repository sub-path
        Path outputDirectory = options.outputDirectory().resolve(target.repositorySubPath());

        printBanner(agentConfigs, agentDirs, modelConfig, target, outputDirectory, options.reviewModel());

        // Load custom instructions
        List<CustomInstruction> customInstructions = loadCustomInstructions(target, options);


        // Execute reviews using the Copilot service
        copilotService.initialize(resolvedToken);

        try {
            System.out.println("Starting reviews...");
            List<ReviewResult> results = reviewService.executeReviews(
                agentConfigs, target, resolvedToken, options.parallelism(),
                customInstructions, modelConfig.reasoningEffort());

            // Generate individual reports
            System.out.println("\nGenerating reports...");
            List<Path> reports = reportService.generateReports(results, outputDirectory);

            for (Path report : reports) {
                System.out.println("  ✓ " + report.getFileName());
            }

            // Generate executive summary
            if (!options.noSummary()) {
                System.out.println("\nGenerating executive summary...");
                Path summaryPath = reportService.generateSummary(
                    results, target.displayName(), outputDirectory,
                    modelConfig.summaryModel(), modelConfig.reasoningEffort());
                System.out.println("  ✓ " + summaryPath.getFileName());
            }

            // Print summary
            printCompletionSummary(results, outputDirectory);

        } finally {
            copilotService.shutdown();
        }
        return ExitCodes.OK;
    }

    private ModelConfig buildModelConfig(ParsedOptions options) {
        ModelConfig baseConfig = defaultModelConfig != null ? defaultModelConfig : new ModelConfig();
        ModelConfig.Builder builder = ModelConfig.builder()
            .reviewModel(baseConfig.reviewModel())
            .reportModel(baseConfig.reportModel())
            .summaryModel(baseConfig.summaryModel());

        if (options.defaultModel() != null) {
            builder.defaultModel(options.defaultModel());
        }
        if (options.reviewModel() != null) {
            builder.reviewModel(options.reviewModel());
        }
        if (options.reportModel() != null) {
            builder.reportModel(options.reportModel());
        }
        if (options.summaryModel() != null) {
            builder.summaryModel(options.summaryModel());
        }

        return builder.build();
    }

    /// Loads custom instructions from specified paths or target directory.
    private List<CustomInstruction> loadCustomInstructions(ReviewTarget target, ParsedOptions options) {
        if (options.noInstructions()) {
            logger.info("Custom instructions disabled by --no-instructions flag");
            return List.of();
        }

        List<CustomInstruction> instructions = new ArrayList<>();

        // Load from explicitly specified paths
        if (!options.instructionPaths().isEmpty()) {
            for (Path path : options.instructionPaths()) {
                try {
                    if (Files.exists(path) && Files.isRegularFile(path)) {
                        String content = Files.readString(path);
                        if (!content.isBlank()) {
                            instructions.add(new CustomInstruction(
                                path.toString(), content.trim(),
                                InstructionSource.LOCAL_FILE, null, null));
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
            boolean shouldLoadPrompts = !options.noPrompts();
            CustomInstructionLoader loader = new CustomInstructionLoader(null, shouldLoadPrompts);
            List<CustomInstruction> targetInstructions = loader.loadForTarget(target);
            for (CustomInstruction instruction : targetInstructions) {
                instructions.add(instruction);
                System.out.println("  ✓ Loaded instructions from target: " + instruction.sourcePath());
            }
        }

        return List.copyOf(instructions);
    }

    private void printBanner(Map<String, AgentConfig> agentConfigs,
                             List<Path> agentDirs, ModelConfig modelConfig,
                             ReviewTarget target, Path outputDirectory,
                             String reviewModel) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           Multi-Agent Code Reviewer                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Target: " + target.displayName() +
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

    private void printCompletionSummary(List<ReviewResult> results, Path outputDirectory) {
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
