package dev.logicojp.reviewer.cli;

public final class CliValidationException extends RuntimeException {
    private final boolean showUsage;

    public CliValidationException(String message, boolean showUsage) {
        super(normalizeMessage(message));
        this.showUsage = showUsage;
    }

    private static String normalizeMessage(String message) {
        return message == null ? "" : message;
    }

    public boolean showUsage() {
        return showUsage;
    }
}

