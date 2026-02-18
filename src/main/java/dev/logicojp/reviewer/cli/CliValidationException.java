package dev.logicojp.reviewer.cli;

/// CLI input validation error. Used exclusively within the {@code cli} package
/// to signal invalid command-line arguments or options.
final class CliValidationException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final boolean showUsage;

     CliValidationException(String message, boolean showUsage) {
        super(normalizeMessage(message));
        this.showUsage = showUsage;
    }

     CliValidationException(String message, boolean showUsage, Throwable cause) {
        super(normalizeMessage(message), cause);
        this.showUsage = showUsage;
    }

    private static String normalizeMessage(String message) {
        return message == null ? "" : message;
    }

    public boolean showUsage() {
        return showUsage;
    }
}

