package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Configuration for the review orchestrator.
 */
@ConfigurationProperties("reviewer.orchestrator")
public class OrchestratorConfig {

    private int defaultParallelism = 4;
    private long timeoutMinutes = 10;

    public int getDefaultParallelism() {
        return defaultParallelism;
    }

    public void setDefaultParallelism(int defaultParallelism) {
        this.defaultParallelism = defaultParallelism;
    }

    public long getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public void setTimeoutMinutes(long timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }
}
