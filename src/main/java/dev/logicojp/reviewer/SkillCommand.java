package dev.logicojp.reviewer;
import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.service.AgentService;
import dev.logicojp.reviewer.service.CopilotService;
import dev.logicojp.reviewer.service.SkillService;
import dev.logicojp.reviewer.skill.SkillDefinition;
import dev.logicojp.reviewer.skill.SkillResult;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExitCodeGenerator;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
@Command(name = "skill", description = "Execute a specific agent skill.")
public class SkillCommand implements Runnable, IExitCodeGenerator {
    private static final Logger logger = LoggerFactory.getLogger(SkillCommand.class);
    @ParentCommand
    private ReviewApp parent;
    @Spec
    private CommandSpec spec;
    @Inject
    private AgentService agentService;
    @Inject
    private CopilotService copilotService;
    @Inject
    private SkillService skillService;
    private int exitCode = CommandLine.ExitCode.OK;
    @Parameters(index = "0", description = "Skill ID to execute", arity = "0..1")
    private String skillId;
    @Option(names = {"-p", "--param"}, description = "Skill parameters in key=value format", split = ",")
    private List<String> paramStrings;
    @Option(names = {"--token"}, description = "GitHub token", defaultValue = "${GITHUB_TOKEN}")
    private String githubToken;
    @Option(names = {"--model"}, description = "LLM model to use", defaultValue = "claude-sonnet-4")
    private String model;
    @Option(names = {"--agents-dir"}, description = "Additional agent definitions directory", arity = "1..*")
    private List<Path> additionalAgentDirs;
    @Option(names = {"--list"}, description = "List available skills")
    private boolean listSkills;
    @Override
    public void run() {
        try {
            execute();
        } catch (Exception e) {
            exitCode = CommandLine.ExitCode.SOFTWARE;
            logger.error("Skill execution failed: {}", e.getMessage(), e);
            spec.commandLine().getErr().println("Error: " + e.getMessage());
        }
    }
    @Override
    public int getExitCode() {
        return exitCode;
    }
    private void execute() throws Exception {
        List<Path> agentDirs = agentService.buildAgentDirectories(additionalAgentDirs);
        Map<String, AgentConfig> agents = agentService.loadAllAgents(agentDirs);
        skillService.registerAllAgentSkills(agents);
        if (listSkills) {
            listAvailableSkills();
            return;
        }
        if (skillId == null || skillId.isBlank()) {
            spec.commandLine().getErr().println("Error: Skill ID required. Use --list to see available skills.");
            exitCode = CommandLine.ExitCode.USAGE;
            return;
        }
        if (githubToken == null || githubToken.isEmpty() || githubToken.equals("${GITHUB_TOKEN}")) {
            throw new IllegalArgumentException("GitHub token is required.");
        }
        Map<String, String> parameters = parseParameters();
        if (!skillService.getSkill(skillId).isPresent()) {
            spec.commandLine().getErr().println("Error: Skill not found: " + skillId);
            exitCode = CommandLine.ExitCode.USAGE;
            return;
        }
        copilotService.initialize();
        try {
            System.out.println("Executing skill: " + skillId);
            System.out.println("Parameters: " + parameters);
            SkillResult result = skillService.executeSkill(skillId, parameters, githubToken, model)
                .get(10, TimeUnit.MINUTES);
            if (result.isSuccess()) {
                System.out.println("=== Skill Result ===\n");
                System.out.println(result.content());
            } else {
                System.err.println("Skill execution failed: " + result.errorMessage());
                exitCode = CommandLine.ExitCode.SOFTWARE;
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
