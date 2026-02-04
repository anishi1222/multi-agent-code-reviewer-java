package dev.logicojp.reviewer;

import io.micronaut.configuration.picocli.PicocliRunner;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

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
        ListAgentsCommand.class,
        SkillCommand.class
    }
)
public class ReviewApp implements Runnable {
    
    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    boolean verbose;

    @Spec
    private CommandSpec spec;
    
    public static void main(String[] args) {
        int exitCode = PicocliRunner.execute(ReviewApp.class, args);
        System.exit(exitCode);
    }
    
    @Override
    public void run() {
        // When no subcommand is specified, show help
        spec.commandLine().usage(System.out);
    }
}
