package dev.logicojp.reviewer;

import dev.logicojp.reviewer.cli.CliParsing;
import dev.logicojp.reviewer.cli.CliUsage;
import dev.logicojp.reviewer.cli.CliValidationException;
import dev.logicojp.reviewer.cli.ExitCodes;
import dev.logicojp.reviewer.service.AgentService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Command to list all available review agents.
@Singleton
public class ListAgentsCommand {

    private final AgentService agentService;

    /// Parsed CLI options for the list command.
    record ParsedOptions(List<Path> additionalAgentDirs) {}

    @Inject
    public ListAgentsCommand(AgentService agentService) {
        this.agentService = agentService;
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
                CliUsage.printList(System.err);
            }
            return ExitCodes.USAGE;
        } catch (Exception e) {
            System.err.println("Error listing agents: " + e.getMessage());
            return ExitCodes.SOFTWARE;
        }
    }

    private ParsedOptions parseArgs(String[] args) {
        args = Objects.requireNonNullElse(args, new String[0]);
        List<Path> additionalAgentDirs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> {
                    CliUsage.printList(System.out);
                    return null;
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

        return new ParsedOptions(List.copyOf(additionalAgentDirs));
    }

    private int executeInternal(ParsedOptions options) throws IOException {
        List<Path> agentDirs = agentService.buildAgentDirectories(options.additionalAgentDirs());
        List<String> availableAgents = agentService.listAvailableAgents(agentDirs);

        System.out.println("Agent directories:");
        for (Path dir : agentDirs) {
            System.out.println("  - " + dir + (Files.exists(dir) ? "" : " (not found)"));
        }
        System.out.println();

        if (availableAgents.isEmpty()) {
            System.out.println("No agents found.");
            return ExitCodes.OK;
        }

        System.out.println("Available agents:");
        for (String agent : availableAgents) {
            System.out.println("  - " + agent);
        }
        return ExitCodes.OK;
    }
}
