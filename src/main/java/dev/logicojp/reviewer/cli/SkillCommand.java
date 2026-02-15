package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.service.AgentService;
import dev.logicojp.reviewer.service.CopilotService;
import dev.logicojp.reviewer.service.SkillService;
import dev.logicojp.reviewer.skill.SkillDefinition;
import dev.logicojp.reviewer.skill.SkillResult;
import dev.logicojp.reviewer.util.GitHubTokenResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
public class SkillCommand {
    private static final Logger logger = LoggerFactory.getLogger(SkillCommand.class);

    private final AgentService agentService;
    private final CopilotService copilotService;
    private final SkillService skillService;
    private final ExecutionConfig executionConfig;
    private final GitHubTokenResolver tokenResolver;
    private final CliOutput output;

    /// Parsed CLI options for the skill command.
    record ParsedOptions(
        String skillId,
        List<String> paramStrings,
        String githubToken,
        String model,
        List<Path> additionalAgentDirs,
        boolean listSkills
    ) {}

    @Inject
    public SkillCommand(
        AgentService agentService,
        CopilotService copilotService,
        SkillService skillService,
        ExecutionConfig executionConfig,
        GitHubTokenResolver tokenResolver,
        CliOutput output
    ) {
        this.agentService = agentService;
        this.copilotService = copilotService;
        this.skillService = skillService;
        this.executionConfig = executionConfig;
        this.tokenResolver = tokenResolver;
        this.output = output;
    }

    public int execute(String[] args) {
        return CommandExecutor.execute(
            args,
            this::parseArgs,
            this::executeInternal,
            CliUsage::printSkill,
            logger,
            output
        );
    }

    private Optional<ParsedOptions> parseArgs(String[] args) {
        args = Objects.requireNonNullElse(args, new String[0]);
        var state = new SkillParseState();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            i = applySkillOption(state, arg, args, i);
            if (state.helpRequested) {
                return Optional.empty();
            }
        }

        return Optional.of(new ParsedOptions(
            state.skillId, List.copyOf(state.paramStrings), state.githubToken, state.model,
            List.copyOf(state.additionalAgentDirs), state.listSkills
        ));
    }

    /// Mutable accumulator for skill CLI argument parsing.
    private static class SkillParseState {
        String skillId;
        final List<String> paramStrings = new ArrayList<>();
        String githubToken = null;
        String model = ModelConfig.DEFAULT_MODEL;
        final List<Path> additionalAgentDirs = new ArrayList<>();
        boolean listSkills;
        boolean helpRequested;
    }

    /// Applies a single CLI option to the skill parse state.
    private int applySkillOption(SkillParseState state, String arg, String[] args, int i) {
        return switch (arg) {
            case "-h", "--help" -> {
                CliUsage.printSkill(output.out());
                state.helpRequested = true;
                yield i;
            }
            case "-p", "--param" -> {
                CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--param");
                state.paramStrings.addAll(CliParsing.splitComma(value.value()));
                yield value.newIndex();
            }
            case "--token" -> CliParsing.readInto(args, i, "--token",
                v -> state.githubToken = CliParsing.readTokenWithWarning(v));
            case "--model" -> CliParsing.readInto(args, i, "--model", v -> state.model = v);
            case "--agents-dir" -> CliParsing.readMultiInto(args, i, "--agents-dir",
                v -> state.additionalAgentDirs.add(Path.of(v)));
            case "--list" -> { state.listSkills = true; yield i; }
            default -> {
                if (arg.startsWith("-")) {
                    throw new CliValidationException("Unknown option: " + arg, true);
                }
                if (state.skillId == null) {
                    state.skillId = arg;
                } else {
                    throw new CliValidationException("Unexpected argument: " + arg, true);
                }
                yield i;
            }
        };
    }

    private int executeInternal(ParsedOptions options) {
        List<Path> agentDirs;
        Map<String, AgentConfig> agents;
        try {
            agentDirs = agentService.buildAgentDirectories(options.additionalAgentDirs());
            agents = agentService.loadAllAgents(agentDirs);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load agents", e);
        }
        skillService.registerAllAgentSkills(agents);
        if (options.listSkills()) {
            listAvailableSkills();
            return ExitCodes.OK;
        }
        if (options.skillId() == null || options.skillId().isBlank()) {
            throw new CliValidationException("Skill ID required. Use --list to see available skills.", true);
        }
        String resolvedToken = tokenResolver.resolve(options.githubToken()).orElse(null);
        if (resolvedToken == null || resolvedToken.isBlank()) {
            throw new CliValidationException(
                "GitHub token is required. Set GITHUB_TOKEN, use --token, or login with `gh auth login`.",
                true
            );
        }
        Map<String, String> parameters = parseParameters(options.paramStrings());
        if (skillService.getSkill(options.skillId()).isEmpty()) {
            throw new CliValidationException("Skill not found: " + options.skillId(), true);
        }
        copilotService.initializeOrThrow(resolvedToken);
        try {
            output.println("Executing skill: " + options.skillId());
            output.println("Parameters: " + parameters.keySet());
            SkillResult result = skillService.executeSkill(
                    options.skillId(), parameters, resolvedToken, options.model())
                .get(executionConfig.skillTimeoutMinutes(), TimeUnit.MINUTES);
            if (result.isSuccess()) {
                output.println("=== Skill Result ===\n");
                output.println(result.content());
                return ExitCodes.OK;
            } else {
                output.errorln("Skill execution failed: " + result.errorMessage());
                return ExitCodes.SOFTWARE;
            }
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException("Skill execution failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Skill execution interrupted", e);
        } finally {
            copilotService.shutdown();
        }
    }

    private void listAvailableSkills() {
        output.println("Available Skills:\n");
        for (SkillDefinition skill : skillService.getRegistry().getAll()) {
            output.println("  " + skill.id());
            output.println("    Name: " + skill.name());
            output.println("    Description: " + skill.description());
            if (!skill.parameters().isEmpty()) {
                output.println("    Parameters:");
                for (var param : skill.parameters()) {
                    String required = param.required() ? " (required)" : "";
                    output.println("      - " + param.name() + ": " + param.description() + required);
                }
            }
            output.println("");
        }
        if (skillService.getRegistry().getAll().isEmpty()) {
            output.println("  No skills found.");
        }
    }

    private static Map<String, String> parseParameters(List<String> paramStrings) {
        Map<String, String> params = new HashMap<>();
        if (paramStrings != null) {
            for (String paramStr : paramStrings) {
                int eqIdx = paramStr.indexOf('=');
                if (eqIdx > 0) {
                    params.put(paramStr.substring(0, eqIdx).trim(), paramStr.substring(eqIdx + 1).trim());
                } else {
                    throw new CliValidationException(
                        "Invalid parameter format: '" + paramStr + "'. Expected 'key=value'.", true);
                }
            }
        }
        return Map.copyOf(params);
    }
}
