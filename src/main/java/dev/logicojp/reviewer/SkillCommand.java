package dev.logicojp.reviewer;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.cli.CliParsing;
import dev.logicojp.reviewer.cli.CliUsage;
import dev.logicojp.reviewer.cli.CliValidationException;
import dev.logicojp.reviewer.cli.ExitCodes;
import dev.logicojp.reviewer.config.ExecutionConfig;
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
import java.util.concurrent.TimeUnit;

@Singleton
public class SkillCommand {
    private static final Logger logger = LoggerFactory.getLogger(SkillCommand.class);

    private final AgentService agentService;
    private final CopilotService copilotService;
    private final SkillService skillService;
    private final ExecutionConfig executionConfig;

    private int exitCode = ExitCodes.OK;
    private String skillId;
    private List<String> paramStrings;
    private String githubToken;
    private String model;
    private List<Path> additionalAgentDirs;
    private boolean listSkills;
    private boolean helpRequested;

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
        resetDefaults();
        try {
            parseArgs(args);
            if (helpRequested) {
                return ExitCodes.OK;
            }
            executeInternal();
        } catch (CliValidationException e) {
            exitCode = ExitCodes.USAGE;
            if (!e.getMessage().isBlank()) {
                System.err.println(e.getMessage());
            }
            if (e.showUsage()) {
                CliUsage.printSkill(System.err);
            }
        } catch (Exception e) {
            exitCode = ExitCodes.SOFTWARE;
            logger.error("Skill execution failed: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
        }
        return exitCode;
    }

    private void resetDefaults() {
        exitCode = ExitCodes.OK;
        skillId = null;
        paramStrings = new ArrayList<>();
        githubToken = System.getenv("GITHUB_TOKEN");
        model = "claude-sonnet-4";
        additionalAgentDirs = new ArrayList<>();
        listSkills = false;
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
                    CliUsage.printSkill(System.out);
                    helpRequested = true;
                    return;
                }
                case "-p", "--param" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--param");
                    i = value.newIndex();
                    paramStrings.addAll(CliParsing.splitComma(value.value()));
                }
                case "--token" -> {
                    CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--token");
                    i = value.newIndex();
                    githubToken = value.value();
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
    }

    private void executeInternal() throws Exception {
        List<Path> agentDirs = agentService.buildAgentDirectories(additionalAgentDirs);
        Map<String, AgentConfig> agents = agentService.loadAllAgents(agentDirs);
        skillService.registerAllAgentSkills(agents);
        if (listSkills) {
            listAvailableSkills();
            return;
        }
        if (skillId == null || skillId.isBlank()) {
            throw new CliValidationException("Skill ID required. Use --list to see available skills.", true);
        }
        GitHubTokenResolver tokenResolver = new GitHubTokenResolver(executionConfig.ghAuthTimeoutSeconds());
        String resolvedToken = tokenResolver.resolve(githubToken).orElse(null);
        if (resolvedToken == null || resolvedToken.isBlank()) {
            throw new CliValidationException(
                "GitHub token is required. Set GITHUB_TOKEN, use --token, or login with `gh auth login`.",
                true
            );
        }
        Map<String, String> parameters = parseParameters();
        if (skillService.getSkill(skillId).isEmpty()) {
            throw new CliValidationException("Skill not found: " + skillId, true);
        }
        copilotService.initialize(resolvedToken);
        try {
            System.out.println("Executing skill: " + skillId);
            System.out.println("Parameters: " + parameters);
            SkillResult result = skillService.executeSkill(skillId, parameters, resolvedToken, model)
                .get(10, TimeUnit.MINUTES);
            if (result.isSuccess()) {
                System.out.println("=== Skill Result ===\n");
                System.out.println(result.content());
            } else {
                System.err.println("Skill execution failed: " + result.errorMessage());
                exitCode = ExitCodes.SOFTWARE;
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

    private Map<String, String> parseParameters() {
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
}
