package dev.logicojp.reviewer.cli;

public class CliValidationException extends RuntimeException {
    private final boolean showUsage;

    public CliValidationException(String message, boolean showUsage) {
        super(message);
        this.showUsage = showUsage;
    }

    public boolean showUsage() {
        return showUsage;
    }
}

