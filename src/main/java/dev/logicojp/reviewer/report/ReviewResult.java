package dev.logicojp.reviewer.report;

import dev.logicojp.reviewer.agent.AgentConfig;

import java.time.LocalDateTime;

/**
 * Holds the result of a review performed by an agent.
 */
public record ReviewResult(
    AgentConfig agentConfig,
    String repository,
    String content,
    LocalDateTime timestamp,
    boolean success,
    String errorMessage
) {

    public ReviewResult {
        timestamp = (timestamp == null) ? LocalDateTime.now() : timestamp;
    }

    public AgentConfig getAgentConfig() {
        return agentConfig;
    }

    public String getRepository() {
        return repository;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isSuccess() {
        return success;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
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
