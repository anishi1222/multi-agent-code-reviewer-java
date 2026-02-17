package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.config.ExecutionConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Singleton
class ReviewOptionsParser {

    private final ExecutionConfig executionConfig;

    @Inject
    public ReviewOptionsParser(ExecutionConfig executionConfig) {
        this.executionConfig = executionConfig;
    }

    public Optional<ReviewCommand.ParsedOptions> parse(String[] args) {
        args = Objects.requireNonNullElse(args, new String[0]);

        var state = new ParseState(executionConfig.parallelism());

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            i = applyOption(state, arg, args, i);
            if (state.helpRequested) {
                return Optional.empty();
            }
        }

        return Optional.of(toParsedOptions(state));
    }

    private ReviewCommand.ParsedOptions toParsedOptions(ParseState state) {
        ReviewCommand.TargetSelection target = validateTargetSelection(state.repository, state.localDirectory);
        ReviewCommand.AgentSelection agents = validateAgentSelection(state.allAgents, state.agentNames);
        return new ReviewCommand.ParsedOptions(
            target, agents, state.outputDirectory, List.copyOf(state.additionalAgentDirs),
            state.githubToken, state.parallelism, state.noSummary,
            state.reviewModel, state.reportModel, state.summaryModel, state.defaultModel,
            List.copyOf(state.instructionPaths), state.noInstructions, state.noPrompts,
            state.trustTarget
        );
    }

    private static class ParseState {
        private String repository;
        private Path localDirectory;
        private boolean allAgents;
        private final List<String> agentNames = new ArrayList<>();
        private Path outputDirectory = Path.of("./reports");
        private final List<Path> additionalAgentDirs = new ArrayList<>();
        private String githubToken;
        private int parallelism;
        private boolean noSummary;
        private String reviewModel;
        private String reportModel;
        private String summaryModel;
        private String defaultModel;
        private final List<Path> instructionPaths = new ArrayList<>();
        private boolean noInstructions;
        private boolean noPrompts;
        private boolean trustTarget;
        private boolean helpRequested;

        ParseState(int defaultParallelism) {
            this.parallelism = defaultParallelism;
        }
    }

    private int applyOption(ParseState state, String arg, String[] args, int i) {
        if ("-h".equals(arg) || "--help".equals(arg)) {
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
            case "--all" -> {
                state.allAgents = true;
                yield i;
            }
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
            case "--token" -> CliParsing.readTokenInto(args, i, "--token", v -> state.githubToken = v);
            case "--parallelism" -> CliParsing.readInto(args, i, "--parallelism",
                v -> state.parallelism = parseInt(v, "--parallelism"));
            case "--no-summary" -> {
                state.noSummary = true;
                yield i;
            }
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
            case "--no-instructions" -> {
                state.noInstructions = true;
                yield i;
            }
            case "--no-prompts" -> {
                state.noPrompts = true;
                yield i;
            }
            case "--trust" -> {
                state.trustTarget = true;
                yield i;
            }
            default -> -1;
        };
    }

    private static ReviewCommand.TargetSelection validateTargetSelection(String repository, Path localDirectory) {
        boolean hasRepo = repository != null && !repository.isBlank();
        boolean hasLocal = localDirectory != null;
        if (!hasRepo && !hasLocal) {
            throw new CliValidationException("Either --repo or --local must be specified.", true);
        }
        if (hasRepo && hasLocal) {
            throw new CliValidationException("Specify either --repo or --local (not both).", true);
        }
        return hasRepo
            ? new ReviewCommand.TargetSelection.Repository(repository)
            : new ReviewCommand.TargetSelection.LocalDirectory(localDirectory);
    }

    private static ReviewCommand.AgentSelection validateAgentSelection(boolean allAgents, List<String> agentNames) {
        boolean hasAgents = !agentNames.isEmpty();
        if (!allAgents && !hasAgents) {
            throw new CliValidationException("Either --all or --agents must be specified.", true);
        }
        if (allAgents && hasAgents) {
            throw new CliValidationException("Specify either --all or --agents (not both).", true);
        }
        return allAgents
            ? new ReviewCommand.AgentSelection.All()
            : new ReviewCommand.AgentSelection.Named(List.copyOf(agentNames));
    }

    private int parseInt(String value, String optionName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new CliValidationException("Invalid value for " + optionName + ": " + value, true);
        }
    }
}
