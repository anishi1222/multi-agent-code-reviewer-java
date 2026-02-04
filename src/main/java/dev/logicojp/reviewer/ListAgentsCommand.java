package dev.logicojp.reviewer;

import dev.logicojp.reviewer.service.AgentService;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

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
public class ListAgentsCommand implements Runnable {
    
    @ParentCommand
    private ReviewApp parent;
    
    @Inject
    private AgentService agentService;
    
    @Option(
        names = {"--agents-dir"},
        description = "Additional directory for agent definitions. Can be specified multiple times.",
        arity = "1..*"
    )
    private List<Path> additionalAgentDirs;
    
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
            System.err.println("Error listing agents: " + e.getMessage());
            System.exit(1);
        }
    }
}
