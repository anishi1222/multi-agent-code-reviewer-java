package dev.logicojp.reviewer;

import dev.logicojp.reviewer.cli.CliParsing;
import dev.logicojp.reviewer.cli.CliUsage;
import dev.logicojp.reviewer.cli.ExitCodes;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Multi-Agent Code Reviewer CLI Application.
 */
@Singleton
public class ReviewApp {
    private final ReviewCommand reviewCommand;
    private final ListAgentsCommand listAgentsCommand;
    private final SkillCommand skillCommand;

    @Inject
    public ReviewApp(ReviewCommand reviewCommand,
                     ListAgentsCommand listAgentsCommand,
                     SkillCommand skillCommand) {
        this.reviewCommand = reviewCommand;
        this.listAgentsCommand = listAgentsCommand;
        this.skillCommand = skillCommand;
    }

    public static void main(String[] args) {
        try (ApplicationContext context = ApplicationContext.run()) {
            ReviewApp app = context.getBean(ReviewApp.class);
            int exitCode = app.execute(args);
            System.exit(exitCode);
        }
    }

    public int execute(String[] args) {
        if (args == null || args.length == 0) {
            CliUsage.printGeneral(System.out);
            return ExitCodes.USAGE;
        }

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

        if (verbose) {
            enableVerboseLogging();
        }

        if (versionRequested) {
            System.out.println("Multi-Agent Reviewer 1.0.0");
            return ExitCodes.OK;
        }

        String[] filteredArgs = remaining.toArray(new String[0]);
        if (filteredArgs.length == 0) {
            CliUsage.printGeneral(System.out);
            return ExitCodes.USAGE;
        }

        // Treat --help / -h as general help only when no subcommand is provided.
        boolean hasHelpFlag = CliParsing.hasHelpFlag(filteredArgs);
        boolean hasSubcommand = Arrays.stream(filteredArgs)
            .anyMatch(arg -> "run".equals(arg) || "list".equals(arg) || "skill".equals(arg));
        if (hasHelpFlag && !hasSubcommand) {
            CliUsage.printGeneral(System.out);
            return ExitCodes.OK;
        }

        int startIndex = 0;
        if ("review".equals(filteredArgs[0])) {
            if (filteredArgs.length == 1) {
                CliUsage.printGeneral(System.out);
                return ExitCodes.USAGE;
            }
            startIndex = 1;
        }

        String command = filteredArgs[startIndex];
        String[] commandArgs = Arrays.copyOfRange(filteredArgs, startIndex + 1, filteredArgs.length);

        return switch (command) {
            case "run" -> reviewCommand.execute(commandArgs);
            case "list" -> listAgentsCommand.execute(commandArgs);
            case "skill" -> skillCommand.execute(commandArgs);
            default -> {
                System.err.println("Unknown command: " + command);
                CliUsage.printGeneral(System.err);
                yield ExitCodes.USAGE;
            }
        };
    }

    private void enableVerboseLogging() {
        try {
            ch.qos.logback.classic.LoggerContext context =
                (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
            context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
                .setLevel(ch.qos.logback.classic.Level.DEBUG);
            context.getLogger("dev.logicojp")
                .setLevel(ch.qos.logback.classic.Level.DEBUG);
        } catch (Exception e) {
            System.err.println("Failed to enable verbose logging: " + e.getMessage());
        }
    }
}
