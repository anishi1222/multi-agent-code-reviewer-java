package dev.logicojp.reviewer.report.core;

import dev.logicojp.reviewer.agent.AgentConfig;
import io.micronaut.core.annotation.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.stream.IntStream;

/// Holds the result of a review performed by an agent.
public record ReviewResult(
    @Nullable AgentConfig agentConfig,
    @Nullable String repository,
    @Nullable String content,
    Instant timestamp,
    boolean success,
    @Nullable String errorMessage
) {
    public ReviewResult {
        timestamp = (timestamp == null) ? Instant.now() : timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    /// Creates a builder with a custom clock for testability.
    public static Builder builder(Clock clock) {
        return new Builder(clock);
    }

    public static java.util.List<ReviewResult> failedResults(AgentConfig config,
                                                              String repository,
                                                              int count,
                                                              String errorMessage) {
        int safeCount = Math.max(0, count);
        return IntStream.range(0, safeCount)
            .mapToObj(_ -> ReviewResult.builder()
                .agentConfig(config)
                .repository(repository)
                .success(false)
                .errorMessage(errorMessage)
                .build())
            .toList();
    }

    public static final class Builder {
        private AgentConfig agentConfig;
        private String repository;
        private String content;
        private Instant timestamp;
        private boolean success = true;
        private String errorMessage;

        Builder() {
            this(Clock.systemUTC());
        }

        Builder(Clock clock) {
            this.timestamp = Instant.now(clock);
        }

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

        public Builder timestamp(Instant timestamp) {
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
