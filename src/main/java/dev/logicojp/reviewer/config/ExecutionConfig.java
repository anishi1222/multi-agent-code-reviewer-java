package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Configuration for execution settings (parallelism, timeouts).
 */
@ConfigurationProperties("reviewer.execution")
public record ExecutionConfig(
    int parallelism,
    long orchestratorTimeoutMinutes,
    long agentTimeoutMinutes,
    long skillTimeoutMinutes,
    long summaryTimeoutMinutes,
    long ghAuthTimeoutSeconds,
    int maxRetries
) {

    public static final int DEFAULT_MAX_RETRIES = 2;

    public ExecutionConfig {
        parallelism = (parallelism <= 0) ? 4 : parallelism;
        orchestratorTimeoutMinutes = (orchestratorTimeoutMinutes <= 0) ? 10 : orchestratorTimeoutMinutes;
        agentTimeoutMinutes = (agentTimeoutMinutes <= 0) ? 5 : agentTimeoutMinutes;
        skillTimeoutMinutes = (skillTimeoutMinutes <= 0) ? 5 : skillTimeoutMinutes;
        summaryTimeoutMinutes = (summaryTimeoutMinutes <= 0) ? 5 : summaryTimeoutMinutes;
        ghAuthTimeoutSeconds = (ghAuthTimeoutSeconds <= 0) ? 10 : ghAuthTimeoutSeconds;
        maxRetries = (maxRetries < 0) ? DEFAULT_MAX_RETRIES : maxRetries;
    }
}
