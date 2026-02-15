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
            Optional<T> options = parser.apply(args);
            if (options.isEmpty()) {
                // --help was requested
                return ExitCodes.OK;
            }
            return executor.apply(options.get());
        } catch (CliValidationException e) {
            if (!e.getMessage().isBlank()) {
                output.errorln(e.getMessage());
            }
            if (e.showUsage()) {
                usagePrinter.accept(output.err());
            }
            return ExitCodes.USAGE;
        } catch (Exception e) {
            logger.error("Execution failed: {}", e.getMessage(), e);
            output.errorln("Error: " + e.getMessage());
            return ExitCodes.SOFTWARE;
        }
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
