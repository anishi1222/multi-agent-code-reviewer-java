package dev.logicojp.reviewer.cli;

import org.slf4j.Logger;

import java.io.PrintStream;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/// Shared command execution logic for all CLI commands.
///
/// Centralizes the common try-catch structure for parsing arguments,
/// handling {@link CliValidationException}, and returning exit codes.
public final class CommandExecutor {

    private CommandExecutor() {
        // Utility class — not instantiable
    }

    /// Executes a command with standardized error handling.
    ///
    /// @param args           CLI arguments to parse
    /// @param parser         Function that parses args into an Optional of parsed options
    /// @param executor       Function that executes the command with parsed options
    /// @param usagePrinter   Consumer that prints usage help to a given PrintStream
    /// @param logger         Logger for error reporting
    /// @param <T>            The type of parsed options
    /// @return Exit code
    public static <T> int execute(
            String[] args,
            Function<String[], Optional<T>> parser,
            Function<T, Integer> executor,
            Consumer<PrintStream> usagePrinter,
            Logger logger,
            CliOutput output) {
        try {
            Optional<T> options = parseOptions(args, parser);
            return executeParsedOptions(options, executor);
        } catch (CliValidationException e) {
            return handleValidationError(e, usagePrinter, output);
        } catch (Exception e) {
            return handleUnexpectedError(e, logger, output);
        }
    }

    private static <T> Optional<T> parseOptions(String[] args,
                                                Function<String[], Optional<T>> parser) {
        return parser.apply(args);
    }

    private static <T> int executeParsedOptions(Optional<T> options,
                                                Function<T, Integer> executor) {
        if (options.isEmpty()) {
            return ExitCodes.OK;
        }
        return executor.apply(options.get());
    }

    private static int handleValidationError(CliValidationException e,
                                             Consumer<PrintStream> usagePrinter,
                                             CliOutput output) {
        if (hasValidationMessage(e)) {
            output.errorln(e.getMessage());
        }
        printUsageIfNeeded(e, usagePrinter, output);
        return ExitCodes.USAGE;
    }

    private static int handleUnexpectedError(Exception e,
                                             Logger logger,
                                             CliOutput output) {
        logger.error("Execution failed: {}", e.getMessage(), e);
        output.errorln(formatUnexpectedErrorMessage(e));
        return ExitCodes.SOFTWARE;
    }

    private static boolean hasValidationMessage(CliValidationException e) {
        String message = e.getMessage();
        return message != null && !message.isBlank();
    }

    private static void printUsageIfNeeded(CliValidationException e,
                                           Consumer<PrintStream> usagePrinter,
                                           CliOutput output) {
        if (e.showUsage()) {
            usagePrinter.accept(output.err());
        }
    }

    private static String formatUnexpectedErrorMessage(Exception e) {
        return "Error: " + e.getMessage();
    }

    public static <T> int execute(
            String[] args,
            Function<String[], Optional<T>> parser,
            Function<T, Integer> executor,
            Consumer<PrintStream> usagePrinter,
            Logger logger) {
        return execute(args, parser, executor, usagePrinter, logger, new CliOutput());
    }
}
