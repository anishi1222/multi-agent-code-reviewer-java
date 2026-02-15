package dev.logicojp.reviewer.cli;

import jakarta.inject.Singleton;

import java.io.PrintStream;

/// CLI output abstraction to avoid direct dependency on System.out/System.err in commands.
@Singleton
public class CliOutput {

    private final PrintStream out;
    private final PrintStream err;

    public CliOutput() {
        this(System.out, System.err);
    }

    CliOutput(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public PrintStream out() {
        return out;
    }

    public PrintStream err() {
        return err;
    }

    public void println(String message) {
        out.println(message);
    }

    public void errorln(String message) {
        err.println(message);
    }
}
