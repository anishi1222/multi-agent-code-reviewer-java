package dev.logicojp.reviewer;

import dev.logicojp.reviewer.service.AgentService;
import jakarta.inject.Inject;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExitCodeGenerator;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Command to list all available review agents.
 */
@Command(
    name = "list",
    description = "List all available review agents."
)
public class ListAgentsCommand implements Runnable, IExitCodeGenerator {
    
    @ParentCommand
    private ReviewApp parent;

    @Spec
    private CommandSpec spec;
    
    private final AgentService agentService;

    private int exitCode = CommandLine.ExitCode.OK;
    
    @Option(
        names = {"--agents-dir"},
        description = "Additional directory for agent definitions. Can be specified multiple times.",
        arity = "1..*"
    )
    private List<Path> additionalAgentDirs;

    @Inject
    public ListAgentsCommand(AgentService agentService) {
        this.agentService = agentService;
    }
    
    @Override
    public void run() {
        try {
            List<Path> agentDirs = agentService.buildAgentDirectories(additionalAgentDirs);
            List<String> availableAgents = agentService.listAvailableAgents(agentDirs);
            
            System.out.println("Agent directories:");
            for (Path dir : agentDirs) {
                System.out.println("  - " + dir + (Files.exists(dir) ? "" : " (not found)"));
            }
            System.out.println();
            
            if (availableAgents.isEmpty()) {
                System.out.println("No agents found.");
                return;
            }
            
            System.out.println("Available agents:");
            for (String agent : availableAgents) {
                System.out.println("  - " + agent);
            }
        } catch (Exception e) {
            exitCode = CommandLine.ExitCode.SOFTWARE;
            spec.commandLine().getErr().println("Error listing agents: " + e.getMessage());
        }
    }

    public int getExitCode() {
        return exitCode;
    }
}
