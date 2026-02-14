package dev.logicojp.reviewer;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.cli.CliParsing;
import dev.logicojp.reviewer.cli.CliUsage;
import dev.logicojp.reviewer.cli.CliValidationException;
import dev.logicojp.reviewer.cli.ExitCodes;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Singleton
public class SkillCommand {
    private static final Logger logger = LoggerFactory.getLogger(SkillCommand.class);

    private final AgentService agentService;
    private final CopilotService copilotService;
    private final SkillService skillService;
    private final ExecutionConfig executionConfig;

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
        ExecutionConfig executionConfig
    ) {
        this.agentService = agentService;
        this.copilotService = copilotService;
        this.skillService = skillService;
        this.executionConfig = executionConfig;
    }

    public int execute(String[] args) {
        try {
            ParsedOptions options = parseArgs(args);
            if (options == null) {
                return ExitCodes.OK;
            }
            return executeInternal(options);
        } catch (CliValidationException e) {
            if (!e.getMessage().isBlank()) {
                System.err.println(e.getMessage());
            }
            if (e.showUsage()) {
                CliUsage.printSkill(System.err);
            }
            return ExitCodes.USAGE;
        } catch (Exception e) {
            logger.error("Skill execution failed: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            return ExitCodes.SOFTWARE;
        }
    }

    private ParsedOptions parseArgs(String[] args) {
        args = Objects.requireNonNullElse(args, new String[0]);

        String skillId = null;
        List<String> paramStrings = new ArrayList<>();
        String githubToken = System.getenv("GITHUB_TOKEN");
        String model = ModelConfig.DEFAULT_MODEL;
        List<Path> additionalAgentDirs = new ArrayList<>();
        boolean listSkills = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> {
                    CliUsage.printSkill(System.out);
                    return null;
                }
                case "-p", "--param" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--param");
                    i = value.newIndex();
                    paramStrings.addAll(CliParsing.splitComma(value.value()));
                }
                case "--token" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--token");
                    i = value.newIndex();
                    githubToken = readToken(value.value());
                }
                case "--model" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--model");
                    i = value.newIndex();
                    model = value.value();
                }
                case "--agents-dir" -> {
                    CliParsing.MultiValue values = CliParsing.readMultiValues(arg, args, i, "--agents-dir");
                    i = values.newIndex();
                    for (String path : values.values()) {
                        additionalAgentDirs.add(Path.of(path));
                    }
                }
                case "--list" -> listSkills = true;
                default -> {
                    if (arg.startsWith("-")) {
                        throw new CliValidationException("Unknown option: " + arg, true);
                    }
                    if (skillId == null) {
                        skillId = arg;
                    } else {
                        throw new CliValidationException("Unexpected argument: " + arg, true);
                    }
                }
            }
        }

        return new ParsedOptions(
            skillId, List.copyOf(paramStrings), githubToken, model,
            List.copyOf(additionalAgentDirs), listSkills
        );
    }

    private int executeInternal(ParsedOptions options) throws Exception {
        List<Path> agentDirs = agentService.buildAgentDirectories(options.additionalAgentDirs());
        Map<String, AgentConfig> agents = agentService.loadAllAgents(agentDirs);
        skillService.registerAllAgentSkills(agents);
        if (options.listSkills()) {
            listAvailableSkills();
            return ExitCodes.OK;
        }
        if (options.skillId() == null || options.skillId().isBlank()) {
            throw new CliValidationException("Skill ID required. Use --list to see available skills.", true);
        }
        GitHubTokenResolver tokenResolver = new GitHubTokenResolver(executionConfig.ghAuthTimeoutSeconds());
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
        copilotService.initialize(resolvedToken);
        try {
            System.out.println("Executing skill: " + options.skillId());
            System.out.println("Parameters: " + parameters);
            SkillResult result = skillService.executeSkill(
                    options.skillId(), parameters, resolvedToken, options.model())
                .get(10, TimeUnit.MINUTES);
            if (result.isSuccess()) {
                System.out.println("=== Skill Result ===\n");
                System.out.println(result.content());
                return ExitCodes.OK;
            } else {
                System.err.println("Skill execution failed: " + result.errorMessage());
                return ExitCodes.SOFTWARE;
            }
        } finally {
            copilotService.shutdown();
        }
    }

    private void listAvailableSkills() {
        System.out.println("Available Skills:\n");
        for (SkillDefinition skill : skillService.getRegistry().getAll()) {
            System.out.println("  " + skill.id());
            System.out.println("    Name: " + skill.name());
            System.out.println("    Description: " + skill.description());
            if (!skill.parameters().isEmpty()) {
                System.out.println("    Parameters:");
                for (var param : skill.parameters()) {
                    String required = param.required() ? " (required)" : "";
                    System.out.println("      - " + param.name() + ": " + param.description() + required);
                }
            }
            System.out.println();
        }
        if (skillService.getRegistry().size() == 0) {
            System.out.println("  No skills found.");
        }
    }

    private static Map<String, String> parseParameters(List<String> paramStrings) {
        Map<String, String> params = new HashMap<>();
        if (paramStrings != null) {
            for (String paramStr : paramStrings) {
                int eqIdx = paramStr.indexOf('=');
                if (eqIdx > 0) {
                    params.put(paramStr.substring(0, eqIdx).trim(), paramStr.substring(eqIdx + 1).trim());
                }
            }
        }
        return params;
    }

    /// Reads a token value, supporting stdin input via "-" to avoid
    /// exposing the token in process listings or shell history.
    private static String readToken(String value) {
        if ("-".equals(value)) {
            try {
                if (System.console() != null) {
                    char[] chars = System.console().readPassword("GitHub Token: ");
                    return chars != null ? new String(chars).trim() : "";
                }
                return new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            } catch (java.io.IOException e) {
                throw new CliValidationException("Failed to read token from stdin: " + e.getMessage(), false);
            }
        }
        return value;
    }
}
