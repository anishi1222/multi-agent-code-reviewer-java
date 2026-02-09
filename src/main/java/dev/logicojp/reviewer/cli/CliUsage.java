package dev.logicojp.reviewer.cli;

import java.io.PrintStream;

public final class CliUsage {
    private CliUsage() {
    }

    public static void printGeneral(PrintStream out) {
        out.println("Usage: review <command> [options]");
        out.println();
        out.println("Global options:");
        out.println("  -v, --verbose               Enable verbose logging");
        out.println("  --version                   Show version");
        out.println();
        out.println("Commands:");
        out.println("  run    Execute a multi-agent code review");
        out.println("  list   List available agents");
        out.println("  skill  Execute a specific agent skill");
        out.println();
        out.println("Use 'review <command> --help' for command options.");
    }

    public static void printRun(PrintStream out) {
        out.println("Usage: review run [options]");
        out.println();
        out.println("Target options (required):");
        out.println("  -r, --repo <owner/repo>     Target GitHub repository");
        out.println("  -l, --local <path>          Target local directory");
        out.println();
        out.println("Agent options (required):");
        out.println("  --all                       Run all available agents");
        out.println("  -a, --agents <a,b,c>        Comma-separated agent names");
        out.println();
        out.println("Other options:");
        out.println("  -o, --output <path>         Output directory (default: ./report)");
        out.println("  --agents-dir <path...>      Additional agent definition directories");
        out.println("  --token <token>             GitHub token (default: GITHUB_TOKEN)");
        out.println("  --parallelism <n>           Number of agents to run in parallel");
        out.println("  --no-summary                Skip executive summary generation");
        out.println("  --review-model <model>      Model for review stage");
        out.println("  --report-model <model>      Model for report stage");
        out.println("  --summary-model <model>     Model for summary stage");
        out.println("  --model <model>             Default model for all stages");
        out.println("  --instructions <path...>    Custom instruction files (Markdown)");
        out.println("  --no-instructions           Disable automatic instructions");
    }

    public static void printList(PrintStream out) {
        out.println("Usage: review list [options]");
        out.println();
        out.println("Options:");
        out.println("  --agents-dir <path...>      Additional agent definition directories");
    }

    public static void printSkill(PrintStream out) {
        out.println("Usage: review skill [skill-id] [options]");
        out.println();
        out.println("Options:");
        out.println("  -p, --param <key=value>     Skill parameters (repeatable or comma-separated)");
        out.println("  --token <token>             GitHub token (default: GITHUB_TOKEN)");
        out.println("  --model <model>             Model for skill execution");
        out.println("  --agents-dir <path...>      Additional agent definition directories");
        out.println("  --list                       List available skills");
    }
}
