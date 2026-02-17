package dev.logicojp.reviewer.report.core;

import dev.logicojp.reviewer.agent.AgentConfig;
import io.micronaut.core.annotation.Nullable;

import java.time.LocalDateTime;

/// Holds the result of a review performed by an agent.
public record ReviewResult(
    AgentConfig agentConfig,
    String repository,
    @Nullable String content,
    LocalDateTime timestamp,
    boolean success,
    @Nullable String errorMessage
) {

    public ReviewResult {
        timestamp = (timestamp == null) ? LocalDateTime.now() : timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private AgentConfig agentConfig;
        private String repository;
        private String content;
        private LocalDateTime timestamp = LocalDateTime.now();
        private boolean success = true;
        private String errorMessage;

        public Builder agentConfig(AgentConfig agentConfig) {
            this.agentConfig = agentConfig;
            return this;
        }

        public Builder repository(String repository) {
            this.repository = repository;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public ReviewResult build() {
            return new ReviewResult(agentConfig, repository, content, timestamp, success, errorMessage);
        }
    }
}
