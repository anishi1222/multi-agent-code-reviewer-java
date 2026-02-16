package dev.logicojp.reviewer;

import dev.logicojp.reviewer.cli.CliParsing;
import dev.logicojp.reviewer.cli.CliOutput;
import dev.logicojp.reviewer.cli.CliUsage;
import dev.logicojp.reviewer.cli.ExitCodes;
import dev.logicojp.reviewer.cli.ListAgentsCommand;
import dev.logicojp.reviewer.cli.ReviewCommand;
import dev.logicojp.reviewer.cli.SkillCommand;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/// Multi-Agent Code Reviewer CLI Application.
@Singleton
public class ReviewApp {
    private static final Set<String> SUBCOMMANDS = Set.of("run", "list", "skill");

    private final ReviewCommand reviewCommand;
    private final ListAgentsCommand listAgentsCommand;
    private final SkillCommand skillCommand;
    private final CliOutput output;

    @Inject
    public ReviewApp(ReviewCommand reviewCommand,
                     ListAgentsCommand listAgentsCommand,
                     SkillCommand skillCommand,
                     CliOutput output) {
        this.reviewCommand = reviewCommand;
        this.listAgentsCommand = listAgentsCommand;
        this.skillCommand = skillCommand;
        this.output = output;
    }

    public static void main(String[] args) {
        try (var context = ApplicationContext.run()) {
            var app = context.getBean(ReviewApp.class);
            int exitCode = app.execute(args);
            System.exit(exitCode);
        }
    }

    public int execute(String[] args) {
        if (args == null || args.length == 0) {
            CliUsage.printGeneral(output);
            return ExitCodes.USAGE;
        }

        GlobalOptions globalOptions = parseGlobalOptions(args);
        boolean verbose = globalOptions.verbose();
        boolean versionRequested = globalOptions.versionRequested();
        List<String> remaining = globalOptions.remainingArgs();

        if (verbose) {
            enableVerboseLogging();
        }

        if (versionRequested) {
            String version = getClass().getPackage().getImplementationVersion();
            output.println("Multi-Agent Reviewer " + (version != null ? version : "dev"));
            return ExitCodes.OK;
        }

        String[] filteredArgs = remaining.toArray(String[]::new);
        if (filteredArgs.length == 0) {
            CliUsage.printGeneral(output);
            return ExitCodes.USAGE;
        }

        // Treat --help / -h as general help only when no subcommand is provided.
        boolean hasHelpFlag = CliParsing.hasHelpFlag(filteredArgs);
        boolean hasSubcommand = Arrays.stream(filteredArgs)
            .anyMatch(SUBCOMMANDS::contains);
        if (hasHelpFlag && !hasSubcommand) {
            CliUsage.printGeneral(output);
            return ExitCodes.OK;
        }

        int startIndex = 0;
        if ("review".equals(filteredArgs[0])) {
            if (filteredArgs.length == 1) {
                CliUsage.printGeneral(output);
                return ExitCodes.USAGE;
            }
            startIndex = 1;
        }

        String command = filteredArgs[startIndex];
        String[] commandArgs = Arrays.copyOfRange(filteredArgs, startIndex + 1, filteredArgs.length);

        return executeCommand(command, commandArgs);
    }

    private int executeCommand(String command, String[] commandArgs) {
        return switch (command) {
            case "run" -> reviewCommand.execute(commandArgs);
            case "list" -> listAgentsCommand.execute(commandArgs);
            case "skill" -> skillCommand.execute(commandArgs);
            default -> {
                output.errorln("Unknown command: " + command);
                CliUsage.printGeneralError(output);
                yield ExitCodes.USAGE;
            }
        };
    }

    private GlobalOptions parseGlobalOptions(String[] args) {
        boolean verbose = false;
        boolean versionRequested = false;
        List<String> remaining = new ArrayList<>();
        for (String arg : args) {
            switch (arg) {
                case "-v", "--verbose" -> verbose = true;
                case "-V", "--version" -> versionRequested = true;
                default -> remaining.add(arg);
            }
        }
        return new GlobalOptions(verbose, versionRequested, List.copyOf(remaining));
    }

    private record GlobalOptions(boolean verbose, boolean versionRequested, List<String> remainingArgs) {
    }

    /// Enables debug-level logging at runtime.
    /// Logback implementation direct dependency — SLF4J does not provide a dynamic log-level API.
    /// This is an acceptable trade-off for implementing the --verbose CLI flag at runtime.
    private void enableVerboseLogging() {
        try {
            LoggerContext context =
                (LoggerContext) LoggerFactory.getILoggerFactory();
            context.getLogger(Logger.ROOT_LOGGER_NAME)
                .setLevel(Level.DEBUG);
            context.getLogger("dev.logicojp")
                .setLevel(Level.DEBUG);
        } catch (ClassCastException e) {
            output.errorln("Failed to enable verbose logging (Logback not available): " + e.getMessage());
        }
    }
}
