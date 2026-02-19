package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;

/// Configuration for execution settings (parallelism, timeouts).
@ConfigurationProperties("reviewer.execution")
public record ExecutionConfig(
    int parallelism,
    int reviewPasses,
    long orchestratorTimeoutMinutes,
    long agentTimeoutMinutes,
    long idleTimeoutMinutes,
    long skillTimeoutMinutes,
    long summaryTimeoutMinutes,
    long ghAuthTimeoutSeconds,
    int maxRetries,
    int maxAccumulatedSize,
    int initialAccumulatedCapacity,
    int instructionBufferExtraCapacity
) {

    public record ConcurrencySettings(int parallelism, int reviewPasses) {
    }

    public record TimeoutSettings(long orchestratorTimeoutMinutes,
                                  long agentTimeoutMinutes,
                                  long idleTimeoutMinutes,
                                  long skillTimeoutMinutes,
                                  long summaryTimeoutMinutes,
                                  long ghAuthTimeoutSeconds) {
    }

    public record RetrySettings(int maxRetries) {
    }

    public record BufferSettings(int maxAccumulatedSize,
                                 int initialAccumulatedCapacity,
                                 int instructionBufferExtraCapacity) {
    }

    public static final int DEFAULT_MAX_RETRIES = 2;
    public static final long DEFAULT_IDLE_TIMEOUT_MINUTES = 5;
    public static final int DEFAULT_REVIEW_PASSES = 1;
    private static final int DEFAULT_PARALLELISM = 4;
    private static final long DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES = 10;
    private static final long DEFAULT_AGENT_TIMEOUT_MINUTES = 5;
    private static final long DEFAULT_SKILL_TIMEOUT_MINUTES = 5;
    private static final long DEFAULT_SUMMARY_TIMEOUT_MINUTES = 5;
    private static final long DEFAULT_GH_AUTH_TIMEOUT_SECONDS = 10;
    public static final int DEFAULT_MAX_ACCUMULATED_SIZE = 4 * 1024 * 1024;
    public static final int DEFAULT_INITIAL_ACCUMULATED_CAPACITY = 4096;
    public static final int DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY = 32;

    public ExecutionConfig {
        parallelism = ConfigDefaults.defaultIfNonPositive(parallelism, DEFAULT_PARALLELISM);
        reviewPasses = ConfigDefaults.defaultIfNonPositive(reviewPasses, DEFAULT_REVIEW_PASSES);
        orchestratorTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(orchestratorTimeoutMinutes, DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES);
        agentTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(agentTimeoutMinutes, DEFAULT_AGENT_TIMEOUT_MINUTES);
        idleTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(idleTimeoutMinutes, DEFAULT_IDLE_TIMEOUT_MINUTES);
        skillTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(skillTimeoutMinutes, DEFAULT_SKILL_TIMEOUT_MINUTES);
        summaryTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(summaryTimeoutMinutes, DEFAULT_SUMMARY_TIMEOUT_MINUTES);
        ghAuthTimeoutSeconds = ConfigDefaults.defaultIfNonPositive(ghAuthTimeoutSeconds, DEFAULT_GH_AUTH_TIMEOUT_SECONDS);
        maxRetries = ConfigDefaults.defaultIfNegative(maxRetries, DEFAULT_MAX_RETRIES);
        maxAccumulatedSize = ConfigDefaults.defaultIfNonPositive(maxAccumulatedSize, DEFAULT_MAX_ACCUMULATED_SIZE);
        initialAccumulatedCapacity = ConfigDefaults.defaultIfNonPositive(initialAccumulatedCapacity, DEFAULT_INITIAL_ACCUMULATED_CAPACITY);
        instructionBufferExtraCapacity = ConfigDefaults.defaultIfNonPositive(instructionBufferExtraCapacity, DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY);
    }

    public static ExecutionConfig of(ConcurrencySettings concurrency,
                                     TimeoutSettings timeouts,
                                     RetrySettings retry,
                                     BufferSettings buffers) {
        return new ExecutionConfig(
            concurrency.parallelism(),
            concurrency.reviewPasses(),
            timeouts.orchestratorTimeoutMinutes(),
            timeouts.agentTimeoutMinutes(),
            timeouts.idleTimeoutMinutes(),
            timeouts.skillTimeoutMinutes(),
            timeouts.summaryTimeoutMinutes(),
            timeouts.ghAuthTimeoutSeconds(),
            retry.maxRetries(),
            buffers.maxAccumulatedSize(),
            buffers.initialAccumulatedCapacity(),
            buffers.instructionBufferExtraCapacity()
        );
    }

    public ConcurrencySettings concurrencySettings() {
        return new ConcurrencySettings(parallelism, reviewPasses);
    }

    public TimeoutSettings timeoutSettings() {
        return new TimeoutSettings(
            orchestratorTimeoutMinutes,
            agentTimeoutMinutes,
            idleTimeoutMinutes,
            skillTimeoutMinutes,
            summaryTimeoutMinutes,
            ghAuthTimeoutSeconds
        );
    }

    public RetrySettings retrySettings() {
        return new RetrySettings(maxRetries);
    }

    public BufferSettings bufferSettings() {
        return new BufferSettings(
            maxAccumulatedSize,
            initialAccumulatedCapacity,
            instructionBufferExtraCapacity
        );
    }

    /// Returns a copy of this config with the parallelism value replaced.
    /// @param newParallelism the new parallelism value
    /// @return a new ExecutionConfig with the updated parallelism
    public ExecutionConfig withParallelism(int newParallelism) {
        return Builder.from(this)
            .parallelism(newParallelism)
            .build();
    }

    public static final class Builder {
        private int parallelism;
        private int reviewPasses;
        private long orchestratorTimeoutMinutes;
        private long agentTimeoutMinutes;
        private long idleTimeoutMinutes;
        private long skillTimeoutMinutes;
        private long summaryTimeoutMinutes;
        private long ghAuthTimeoutSeconds;
        private int maxRetries;
        private int maxAccumulatedSize;
        private int initialAccumulatedCapacity;
        private int instructionBufferExtraCapacity;

        public static Builder from(ExecutionConfig source) {
            var b = new Builder();
            b.parallelism = source.parallelism;
            b.reviewPasses = source.reviewPasses;
            b.orchestratorTimeoutMinutes = source.orchestratorTimeoutMinutes;
            b.agentTimeoutMinutes = source.agentTimeoutMinutes;
            b.idleTimeoutMinutes = source.idleTimeoutMinutes;
            b.skillTimeoutMinutes = source.skillTimeoutMinutes;
            b.summaryTimeoutMinutes = source.summaryTimeoutMinutes;
            b.ghAuthTimeoutSeconds = source.ghAuthTimeoutSeconds;
            b.maxRetries = source.maxRetries;
            b.maxAccumulatedSize = source.maxAccumulatedSize;
            b.initialAccumulatedCapacity = source.initialAccumulatedCapacity;
            b.instructionBufferExtraCapacity = source.instructionBufferExtraCapacity;
            return b;
        }

        public Builder parallelism(int parallelism) { this.parallelism = parallelism; return this; }

        public Builder reviewPasses(int reviewPasses) { this.reviewPasses = reviewPasses; return this; }

        public Builder orchestratorTimeoutMinutes(long orchestratorTimeoutMinutes) {
            this.orchestratorTimeoutMinutes = orchestratorTimeoutMinutes;
            return this;
        }

        public Builder agentTimeoutMinutes(long agentTimeoutMinutes) {
            this.agentTimeoutMinutes = agentTimeoutMinutes;
            return this;
        }

        public Builder idleTimeoutMinutes(long idleTimeoutMinutes) {
            this.idleTimeoutMinutes = idleTimeoutMinutes;
            return this;
        }

        public Builder skillTimeoutMinutes(long skillTimeoutMinutes) {
            this.skillTimeoutMinutes = skillTimeoutMinutes;
            return this;
        }

        public Builder summaryTimeoutMinutes(long summaryTimeoutMinutes) {
            this.summaryTimeoutMinutes = summaryTimeoutMinutes;
            return this;
        }

        public Builder ghAuthTimeoutSeconds(long ghAuthTimeoutSeconds) {
            this.ghAuthTimeoutSeconds = ghAuthTimeoutSeconds;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder maxAccumulatedSize(int maxAccumulatedSize) {
            this.maxAccumulatedSize = maxAccumulatedSize;
            return this;
        }

        public Builder initialAccumulatedCapacity(int initialAccumulatedCapacity) {
            this.initialAccumulatedCapacity = initialAccumulatedCapacity;
            return this;
        }

        public Builder instructionBufferExtraCapacity(int instructionBufferExtraCapacity) {
            this.instructionBufferExtraCapacity = instructionBufferExtraCapacity;
            return this;
        }

        public ExecutionConfig build() {
            return ExecutionConfig.of(
                new ConcurrencySettings(parallelism, reviewPasses),
                new TimeoutSettings(
                    orchestratorTimeoutMinutes,
                    agentTimeoutMinutes,
                    idleTimeoutMinutes,
                    skillTimeoutMinutes,
                    summaryTimeoutMinutes,
                    ghAuthTimeoutSeconds
                ),
                new RetrySettings(maxRetries),
                new BufferSettings(
                    maxAccumulatedSize,
                    initialAccumulatedCapacity,
                    instructionBufferExtraCapacity
                )
            );
        }
    }
}
