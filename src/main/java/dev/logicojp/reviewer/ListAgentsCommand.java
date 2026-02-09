package dev.logicojp.reviewer;

import dev.logicojp.reviewer.cli.CliParsing;
import dev.logicojp.reviewer.cli.CliUsage;
import dev.logicojp.reviewer.cli.CliValidationException;
import dev.logicojp.reviewer.cli.ExitCodes;
import dev.logicojp.reviewer.service.AgentService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Command to list all available review agents.
 */
@Singleton
public class ListAgentsCommand {

    private final AgentService agentService;

    private int exitCode = ExitCodes.OK;

    private List<Path> additionalAgentDirs;

    private boolean helpRequested;

    @Inject
    public ListAgentsCommand(AgentService agentService) {
        this.agentService = agentService;
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
                CliUsage.printList(System.err);
            }
        } catch (Exception e) {
            exitCode = ExitCodes.SOFTWARE;
            System.err.println("Error listing agents: " + e.getMessage());
        }
        return exitCode;
    }

    private void resetDefaults() {
        exitCode = ExitCodes.OK;
        additionalAgentDirs = new ArrayList<>();
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
                    CliUsage.printList(System.out);
                    helpRequested = true;
                    return;
                }
                case "--agents-dir" -> {
                    CliParsing.MultiValue values = CliParsing.readMultiValues(arg, args, i, "--agents-dir");
                    i = values.newIndex();
                    for (String path : values.values()) {
                        additionalAgentDirs.add(Path.of(path));
                    }
                }
                default -> {
                    if (arg.startsWith("-")) {
                        throw new CliValidationException("Unknown option: " + arg, true);
                    }
                    throw new CliValidationException("Unexpected argument: " + arg, true);
                }
            }
        }
    }

    private void executeInternal() {
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
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
