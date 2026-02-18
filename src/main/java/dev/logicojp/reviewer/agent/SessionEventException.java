package dev.logicojp.reviewer.agent;

/// Domain-specific exception for session-level event processing failures.
final class SessionEventException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    SessionEventException(String message) {
        super(message);
    }
}
