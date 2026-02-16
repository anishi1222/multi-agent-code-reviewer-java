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
    int maxRetries
) {

    public static final int DEFAULT_MAX_RETRIES = 2;
    public static final long DEFAULT_IDLE_TIMEOUT_MINUTES = 5;
    public static final int DEFAULT_REVIEW_PASSES = 1;
    private static final int DEFAULT_PARALLELISM = 4;
    private static final long DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES = 10;
    private static final long DEFAULT_AGENT_TIMEOUT_MINUTES = 5;
    private static final long DEFAULT_SKILL_TIMEOUT_MINUTES = 5;
    private static final long DEFAULT_SUMMARY_TIMEOUT_MINUTES = 5;
    private static final long DEFAULT_GH_AUTH_TIMEOUT_SECONDS = 10;

    public ExecutionConfig {
        parallelism = defaultIfNonPositive(parallelism, DEFAULT_PARALLELISM);
        reviewPasses = defaultIfNonPositive(reviewPasses, DEFAULT_REVIEW_PASSES);
        orchestratorTimeoutMinutes = defaultIfNonPositive(orchestratorTimeoutMinutes, DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES);
        agentTimeoutMinutes = defaultIfNonPositive(agentTimeoutMinutes, DEFAULT_AGENT_TIMEOUT_MINUTES);
        idleTimeoutMinutes = defaultIfNonPositive(idleTimeoutMinutes, DEFAULT_IDLE_TIMEOUT_MINUTES);
        skillTimeoutMinutes = defaultIfNonPositive(skillTimeoutMinutes, DEFAULT_SKILL_TIMEOUT_MINUTES);
        summaryTimeoutMinutes = defaultIfNonPositive(summaryTimeoutMinutes, DEFAULT_SUMMARY_TIMEOUT_MINUTES);
        ghAuthTimeoutSeconds = defaultIfNonPositive(ghAuthTimeoutSeconds, DEFAULT_GH_AUTH_TIMEOUT_SECONDS);
        maxRetries = defaultIfNegative(maxRetries, DEFAULT_MAX_RETRIES);
    }

    private static int defaultIfNonPositive(int value, int defaultValue) {
        return value <= 0 ? defaultValue : value;
    }

    private static long defaultIfNonPositive(long value, long defaultValue) {
        return value <= 0 ? defaultValue : value;
    }

    private static int defaultIfNegative(int value, int defaultValue) {
        return value < 0 ? defaultValue : value;
    }

    /// Returns a copy of this config with the parallelism value replaced.
    /// @param newParallelism the new parallelism value
    /// @return a new ExecutionConfig with the updated parallelism
    public ExecutionConfig withParallelism(int newParallelism) {
        return new ExecutionConfig(newParallelism, reviewPasses, orchestratorTimeoutMinutes,
            agentTimeoutMinutes, idleTimeoutMinutes, skillTimeoutMinutes,
            summaryTimeoutMinutes, ghAuthTimeoutSeconds, maxRetries);
    }
}
