package dev.logicojp.reviewer.service;

/// Exception thrown when the Copilot CLI is not found, not authenticated,
/// or fails health checks.
///
/// Replaces improper usage of {@link java.util.concurrent.ExecutionException}
/// in CLI validation paths, providing clear semantics for CLI-related failures.
public final class CopilotCliException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public CopilotCliException(String message) {
        super(message);
    }

    public CopilotCliException(String message, Throwable cause) {
        super(message, cause);
    }
}
