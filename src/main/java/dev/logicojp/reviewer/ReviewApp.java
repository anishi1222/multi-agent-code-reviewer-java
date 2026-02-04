package dev.logicojp.reviewer;

import io.micronaut.configuration.picocli.PicocliRunner;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Multi-Agent Code Reviewer CLI Application.
 * 
 * Uses GitHub Copilot SDK to run multiple AI agents in parallel for code review,
 * and generates individual reports and an executive summary.
 * 
 * Built with Micronaut Picocli integration for dependency injection support.
 * 
 * Supports agent definitions in:
 * - YAML format (.yaml, .yml)
 * - GitHub Copilot agent format (.agent.md)
 */
@Command(
    name = "review",
    mixinStandardHelpOptions = true,
    version = "Multi-Agent Reviewer 1.0.0",
    description = "Run multiple AI agents to review a GitHub repository and generate reports.",
    subcommands = {
        ReviewCommand.class,
        ListAgentsCommand.class
    }
)
public class ReviewApp implements Runnable {
    
    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    boolean verbose;
    
    public static void main(String[] args) {
        int exitCode = PicocliRunner.execute(ReviewApp.class, args);
        System.exit(exitCode);
    }
    
    @Override
    public void run() {
        // When no subcommand is specified, show help
        System.out.println("Use 'review run' to execute a review or 'review list' to list agents.");
        System.out.println("Use --help for more information.");
    }
}
