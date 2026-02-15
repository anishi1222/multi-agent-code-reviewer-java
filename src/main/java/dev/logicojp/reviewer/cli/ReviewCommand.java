package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.instruction.CustomInstructionLoader;
import dev.logicojp.reviewer.instruction.CustomInstructionSafetyValidator;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

    private final GitHubTokenResolver tokenResolver;

    private final CustomInstructionLoader instructionLoader;

    private final CliOutput output;

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
        boolean noPrompts,
        boolean trustTarget
    ) {}

    @Inject
    public ReviewCommand(
        AgentService agentService,
        CopilotService copilotService,
        ReviewService reviewService,
        ReportService reportService,
        ModelConfig defaultModelConfig,
        ExecutionConfig executionConfig,
        GitHubTokenResolver tokenResolver,
        CustomInstructionLoader instructionLoader,
        CliOutput output
    ) {
        this.agentService = agentService;
        this.copilotService = copilotService;
        this.reviewService = reviewService;
        this.reportService = reportService;
        this.defaultModelConfig = defaultModelConfig;
        this.executionConfig = executionConfig;
        this.tokenResolver = tokenResolver;
        this.instructionLoader = instructionLoader;
        this.output = output;
    }

    public int execute(String[] args) {
        return CommandExecutor.execute(
            args,
            this::parseArgs,
            this::executeInternal,
            CliUsage::printRun,
            logger
        );
    }

    private Optional<ParsedOptions> parseArgs(String[] args) {
        args = Objects.requireNonNullElse(args, new String[0]);

        // Mutable accumulators during parsing — encapsulated in local scope
        var state = new ParseState(executionConfig.parallelism());

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            i = applyOption(state, arg, args, i);
            if (state.helpRequested) {
                return Optional.empty();
            }
        }

        // Validate and build target selection
        TargetSelection target = validateTargetSelection(state.repository, state.localDirectory);

        // Validate and build agent selection
        AgentSelection agents = validateAgentSelection(state.allAgents, state.agentNames);

        return Optional.of(new ParsedOptions(
            target, agents, state.outputDirectory, List.copyOf(state.additionalAgentDirs),
            state.githubToken, state.parallelism, state.noSummary,
            state.reviewModel, state.reportModel, state.summaryModel, state.defaultModel,
            List.copyOf(state.instructionPaths), state.noInstructions, state.noPrompts,
            state.trustTarget
        ));
    }

    /// Mutable accumulator for CLI argument parsing.
    /// Encapsulates all mutable state to keep parseArgs clean.
    private static class ParseState {
        String repository;
        Path localDirectory;
        boolean allAgents;
        final List<String> agentNames = new ArrayList<>();
        Path outputDirectory = Path.of("./reports");
        final List<Path> additionalAgentDirs = new ArrayList<>();
        String githubToken = null;  // resolved later by GitHubTokenResolver
        int parallelism;
        boolean noSummary;
        String reviewModel;
        String reportModel;
        String summaryModel;
        String defaultModel;
        final List<Path> instructionPaths = new ArrayList<>();
        boolean noInstructions;
        boolean noPrompts;
        boolean trustTarget;
        boolean helpRequested;

        ParseState(int defaultParallelism) {
            this.parallelism = defaultParallelism;
        }
    }

    /// Applies a single CLI option to the parse state.
    /// Returns the (possibly advanced) argument index.
    private int applyOption(ParseState state, String arg, String[] args, int i) {
        if ("-h".equals(arg) || "--help".equals(arg)) {
            CliUsage.printRun(output.out());
            state.helpRequested = true;
            return i;
        }

        int parsedIndex = applyTargetOption(state, arg, args, i);
        if (parsedIndex >= 0) return parsedIndex;

        parsedIndex = applyAgentOption(state, arg, args, i);
        if (parsedIndex >= 0) return parsedIndex;

        parsedIndex = applyExecutionOption(state, arg, args, i);
        if (parsedIndex >= 0) return parsedIndex;

        parsedIndex = applyModelOption(state, arg, args, i);
        if (parsedIndex >= 0) return parsedIndex;

        parsedIndex = applyInstructionOption(state, arg, args, i);
        if (parsedIndex >= 0) return parsedIndex;

        if (arg.startsWith("-")) {
            throw new CliValidationException("Unknown option: " + arg, true);
        }
        throw new CliValidationException("Unexpected argument: " + arg, true);
    }

    private int applyTargetOption(ParseState state, String arg, String[] args, int i) {
        return switch (arg) {
            case "-r", "--repo" -> CliParsing.readInto(args, i, "--repo", v -> state.repository = v);
            case "-l", "--local" -> CliParsing.readInto(args, i, "--local", v -> state.localDirectory = Path.of(v));
            default -> -1;
        };
    }

    private int applyAgentOption(ParseState state, String arg, String[] args, int i) {
        return switch (arg) {
            case "--all" -> { state.allAgents = true; yield i; }
            case "-a", "--agents" -> {
                CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--agents");
                List<String> parsed = CliParsing.splitComma(value.value());
                if (parsed.isEmpty()) {
                    throw new CliValidationException("--agents requires at least one value", true);
                }
                state.agentNames.addAll(parsed);
                yield value.newIndex();
            }
            default -> -1;
        };
    }

    private int applyExecutionOption(ParseState state, String arg, String[] args, int i) {
        return switch (arg) {
            case "-o", "--output" -> CliParsing.readInto(args, i, "--output", v -> state.outputDirectory = Path.of(v));
            case "--agents-dir" -> CliParsing.readMultiInto(args, i, "--agents-dir",
                v -> state.additionalAgentDirs.add(Path.of(v)));
            case "--token" -> CliParsing.readInto(args, i, "--token", v -> state.githubToken = CliParsing.readTokenWithWarning(v));
            case "--parallelism" -> CliParsing.readInto(args, i, "--parallelism",
                v -> state.parallelism = parseInt(v, "--parallelism"));
            case "--no-summary" -> { state.noSummary = true; yield i; }
            default -> -1;
        };
    }

    private int applyModelOption(ParseState state, String arg, String[] args, int i) {
        return switch (arg) {
            case "--review-model" -> CliParsing.readInto(args, i, "--review-model", v -> state.reviewModel = v);
            case "--report-model" -> CliParsing.readInto(args, i, "--report-model", v -> state.reportModel = v);
            case "--summary-model" -> CliParsing.readInto(args, i, "--summary-model", v -> state.summaryModel = v);
            case "--model" -> CliParsing.readInto(args, i, "--model", v -> state.defaultModel = v);
            default -> -1;
        };
    }

    private int applyInstructionOption(ParseState state, String arg, String[] args, int i) {
        return switch (arg) {
            case "--instructions" -> CliParsing.readMultiInto(args, i, "--instructions",
                v -> state.instructionPaths.add(Path.of(v)));
            case "--no-instructions" -> { state.noInstructions = true; yield i; }
            case "--no-prompts" -> { state.noPrompts = true; yield i; }
            case "--trust" -> { state.trustTarget = true; yield i; }
            default -> -1;
        };
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

    private int executeInternal(ParsedOptions options) {
        var targetAndToken = buildReviewTarget(options);
        ReviewTarget target = targetAndToken.target();
        String resolvedToken = targetAndToken.resolvedToken();

        ModelConfig modelConfig = buildModelConfig(options);
        List<Path> agentDirs = agentService.buildAgentDirectories(options.additionalAgentDirs());
        Map<String, AgentConfig> agentConfigs = loadAgentConfigs(options, agentDirs);

        if (agentConfigs.isEmpty()) {
            output.errorln("Error: No agents found. Check the agents directories:");
            for (Path dir : agentDirs) {
                output.errorln("  - " + dir);
            }
            return ExitCodes.SOFTWARE;
        }

        // Apply model overrides if specified
        if (options.reviewModel() != null) {
            for (Map.Entry<String, AgentConfig> entry : agentConfigs.entrySet()) {
                entry.setValue(entry.getValue().withModel(options.reviewModel()));
            }
        }

        Path outputDirectory = options.outputDirectory().resolve(target.repositorySubPath());

        printBanner(agentConfigs, agentDirs, modelConfig, target, outputDirectory, options.reviewModel());

        List<CustomInstruction> customInstructions = loadCustomInstructions(target, options);
        ReviewExecutionContext executionContext = new ReviewExecutionContext(
            target,
            resolvedToken,
            modelConfig,
            agentConfigs,
            options,
            customInstructions,
            outputDirectory
        );

        copilotService.initializeOrThrow(resolvedToken);
        try {
            return executeReviewsAndReport(executionContext);
        } finally {
            copilotService.shutdown();
        }
    }

    /// Resolved target and token pair.
    private record TargetAndToken(ReviewTarget target, String resolvedToken) {}

    /// Execution context for the review run.
    private record ReviewExecutionContext(
        ReviewTarget target,
        String resolvedToken,
        ModelConfig modelConfig,
        Map<String, AgentConfig> agentConfigs,
        ParsedOptions options,
        List<CustomInstruction> customInstructions,
        Path outputDirectory
    ) {}

    /// Builds and validates the review target from parsed options.
    private TargetAndToken buildReviewTarget(ParsedOptions options) {
        return switch (options.target()) {
            case TargetSelection.Repository(String repository) -> {
                String resolvedToken = tokenResolver.resolve(options.githubToken()).orElse(null);
                if (resolvedToken == null || resolvedToken.isBlank()) {
                    throw new CliValidationException(
                        "GitHub token is required for repository review. Set GITHUB_TOKEN, use --token, or login with `gh auth login`.",
                        true);
                }
                yield new TargetAndToken(ReviewTarget.gitHub(repository), resolvedToken);
            }
            case TargetSelection.LocalDirectory(Path localDir) -> {
                Path localPath = localDir.toAbsolutePath();
                if (!Files.exists(localPath)) {
                    throw new CliValidationException("Local directory does not exist: " + localPath, true);
                }
                if (!Files.isDirectory(localPath)) {
                    throw new CliValidationException("Path is not a directory: " + localPath, true);
                }
                yield new TargetAndToken(ReviewTarget.local(localPath), null);
            }
        };
    }

    /// Loads agent configurations from configured directories.
    private Map<String, AgentConfig> loadAgentConfigs(ParsedOptions options, List<Path> agentDirs) {
        try {
            return switch (options.agents()) {
                case AgentSelection.All() -> agentService.loadAllAgents(agentDirs);
                case AgentSelection.Named(List<String> names) -> agentService.loadAgents(agentDirs, names);
            };
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load agent configurations", e);
        }
    }

    /// Executes reviews, generates reports and summary.
    private int executeReviewsAndReport(ReviewExecutionContext context) {
        output.println("Starting reviews...");
        List<ReviewResult> results = reviewService.executeReviews(
            context.agentConfigs(), context.target(), context.resolvedToken(), context.options().parallelism(),
            context.customInstructions(), context.modelConfig().reasoningEffort());

        output.println("\nGenerating reports...");
        List<Path> reports;
        try {
            reports = reportService.generateReports(results, context.outputDirectory());
        } catch (IOException e) {
            throw new UncheckedIOException("Report generation failed", e);
        }
        for (Path report : reports) {
            output.println("  ✓ " + report.getFileName());
        }

        if (!context.options().noSummary()) {
            output.println("\nGenerating executive summary...");
            try {
                Path summaryPath = reportService.generateSummary(
                    results, context.target().displayName(), context.outputDirectory(),
                    context.modelConfig().summaryModel(), context.modelConfig().reasoningEffort());
                output.println("  ✓ " + summaryPath.getFileName());
            } catch (Exception e) {
                logger.error("Summary generation failed: {}", e.getMessage(), e);
                output.errorln("Warning: Summary generation failed: " + e.getMessage());
            }
        }

        printCompletionSummary(results, context.outputDirectory());
        return ExitCodes.OK;
    }

    private ModelConfig buildModelConfig(ParsedOptions options) {
        ModelConfig baseConfig = defaultModelConfig != null ? defaultModelConfig : new ModelConfig();
        ModelConfig.Builder builder = ModelConfig.builder()
            .reviewModel(baseConfig.reviewModel())
            .reportModel(baseConfig.reportModel())
            .summaryModel(baseConfig.summaryModel());

        if (options.defaultModel() != null) {
            builder.allModels(options.defaultModel());
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
        loadInstructionsFromExplicitPaths(options.instructionPaths(), instructions);
        loadInstructionsFromTrustedTarget(target, options, instructions);

        return List.copyOf(instructions);
    }

    private void loadInstructionsFromExplicitPaths(List<Path> instructionPaths,
                                                   List<CustomInstruction> instructions) {
        if (instructionPaths.isEmpty()) {
            return;
        }

        for (Path path : instructionPaths) {
            try {
                if (Files.exists(path) && Files.isRegularFile(path)) {
                    String content = Files.readString(path);
                    if (!content.isBlank()) {
                        CustomInstruction instruction = new CustomInstruction(
                            path.toString(), content.trim(),
                            InstructionSource.LOCAL_FILE, null, null);
                        addIfSafe(instruction, instructions, "  ✓ Loaded instructions: ");
                    }
                } else {
                    logger.warn("Instruction file not found: {}", path);
                }
            } catch (Exception e) {
                logger.warn("Failed to read instruction file {}: {}", path, e.getMessage());
            }
        }
    }

    private void loadInstructionsFromTrustedTarget(ReviewTarget target,
                                                   ParsedOptions options,
                                                   List<CustomInstruction> instructions) {
        if (!target.isLocal()) {
            return;
        }

        if (!options.trustTarget()) {
            output.println("ℹ  Target instructions skipped (use --trust to load from review target).");
            return;
        }

        output.println("⚠  --trust enabled: loading custom instructions from the review target.");
        CustomInstructionLoader targetLoader = options.noPrompts()
            ? new CustomInstructionLoader(null, false)
            : instructionLoader;
        List<CustomInstruction> targetInstructions = targetLoader.loadForTarget(target);
        for (CustomInstruction instruction : targetInstructions) {
            addIfSafe(instruction, instructions, "  ✓ Loaded instructions from target: ");
        }
    }

    private void addIfSafe(CustomInstruction instruction,
                           List<CustomInstruction> instructions,
                           String loadedPrefix) {
        CustomInstructionSafetyValidator.ValidationResult result =
            CustomInstructionSafetyValidator.validate(instruction);
        if (result.safe()) {
            instructions.add(instruction);
            output.println(loadedPrefix + instruction.sourcePath());
        } else {
            output.errorln("⚠  Skipped unsafe instruction: "
                + instruction.sourcePath() + " (" + result.reason() + ")");
        }
    }

    private void printBanner(Map<String, AgentConfig> agentConfigs,
                             List<Path> agentDirs, ModelConfig modelConfig,
                             ReviewTarget target, Path outputDirectory,
                             String reviewModel) {
        output.println("╔════════════════════════════════════════════════════════════╗");
        output.println("║           Multi-Agent Code Reviewer                       ║");
        output.println("╚════════════════════════════════════════════════════════════╝");
        output.println("");
        output.println("Target: " + target.displayName() +
            (target.isLocal() ? " (local)" : " (GitHub)"));
        output.println("Agents: " + agentConfigs.keySet());
        output.println("Output: " + outputDirectory.toAbsolutePath());
        output.println("");
        output.println("Agent directories:");
        for (Path dir : agentDirs) {
            output.println("  - " + dir + (Files.exists(dir) ? "" : " (not found)"));
        }
        output.println("");
        output.println("Models:");
        output.println("  Review: " + (reviewModel != null ? reviewModel : "(agent default)"));
        output.println("  Summary: " + modelConfig.summaryModel());
        if (executionConfig.reviewPasses() > 1) {
            output.println("Review passes: " + executionConfig.reviewPasses() + " per agent");
        }
        output.println("");
    }

    private void printCompletionSummary(List<ReviewResult> results, Path outputDirectory) {
        long successCount = results.stream().filter(ReviewResult::isSuccess).count();
        output.println("");
        output.println("════════════════════════════════════════════════════════════");
        output.println("Review completed!");
        output.println("  Total agents: " + results.size());
        output.println("  Successful: " + successCount);
        output.println("  Failed: " + (results.size() - successCount));
        output.println("  Reports: " + outputDirectory.toAbsolutePath());
        output.println("════════════════════════════════════════════════════════════");
    }
}
