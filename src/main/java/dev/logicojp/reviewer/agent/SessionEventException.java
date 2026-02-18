package dev.logicojp.reviewer.agent;

/// Domain-specific exception for session-level event processing failures.
final class SessionEventException extends RuntimeException {

    SessionEventException(String message) {
        super(message);
    }
}
