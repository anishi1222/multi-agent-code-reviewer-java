package dev.logicojp.reviewer.cli;

import java.io.PrintStream;

public final class CliUsage {
    private CliUsage() {
    }

    public static void printGeneral(CliOutput output) {
        printGeneral(output.out());
    }

    public static void printGeneral(PrintStream out) {
        print(out, """
                Usage: review <command> [options]

                Global options:
                    -v, --verbose               Enable verbose logging
                    --version                   Show version

                Feature flags (env / JVM properties):
                    REVIEWER_STRUCTURED_CONCURRENCY=true
                    REVIEWER_STRUCTURED_CONCURRENCY_SKILLS=true
                    -Dreviewer.structuredConcurrency=true
                    -Dreviewer.structuredConcurrency.skills=true

                Commands:
                    run    Execute a multi-agent code review
                    list   List available agents
                    skill  Execute a specific agent skill

                Use 'review <command> --help' for command options.
                """);
    }

    public static void printGeneralError(CliOutput output) {
        printGeneral(output.err());
    }

    public static void printRun(CliOutput output) {
        printRun(output.out());
    }

    public static void printRun(PrintStream out) {
            print(out, """
                Usage: review run [options]

                Target options (required):
                    -r, --repo <owner/repo>     Target GitHub repository
                    -l, --local <path>          Target local directory

                Agent options (required):
                    --all                       Run all available agents
                    -a, --agents <a,b,c>        Comma-separated agent names

                Other options:
                    -o, --output <path>         Output directory (default: ./reports)
                    --agents-dir <path...>      Additional agent definition directories
                    --token -                   Read GitHub token from stdin (default: GITHUB_TOKEN env var)
                    --parallelism <n>           Number of agents to run in parallel
                    --no-summary                Skip executive summary generation
                    --review-model <model>      Model for review stage
                    --report-model <model>      Model for report stage
                    --summary-model <model>     Model for summary stage
                    --model <model>             Default model for all stages
                    --instructions <path...>    Custom instruction files (Markdown)
                    --no-instructions           Disable automatic instructions
                    --no-prompts                Disable loading .github/prompts/*.prompt.md
                """);
    }

    public static void printList(CliOutput output) {
        printList(output.out());
    }

    public static void printList(PrintStream out) {
        print(out, """
                Usage: review list [options]

                Options:
                    --agents-dir <path...>      Additional agent definition directories
                """);
    }

    public static void printSkill(CliOutput output) {
        printSkill(output.out());
    }

    public static void printSkill(PrintStream out) {
        print(out, """
                Usage: review skill [skill-id] [options]

                Options:
                    -p, --param <key=value>     Skill parameters (repeatable or comma-separated)
                    --token -                   Read GitHub token from stdin (default: GITHUB_TOKEN env var)
                    --model <model>             Model for skill execution
                    --agents-dir <path...>      Additional agent definition directories
                    --list                      List available skills
                """);
    }

    private static void print(PrintStream out, String content) {
        out.print(content);
    }
}
