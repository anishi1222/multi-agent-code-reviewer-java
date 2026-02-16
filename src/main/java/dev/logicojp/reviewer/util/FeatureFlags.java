package dev.logicojp.reviewer.util;

import io.micronaut.context.annotation.ConfigurationProperties;

/// Centralized feature flags backed by Micronaut configuration binding.
@ConfigurationProperties("reviewer.feature-flags")
public record FeatureFlags(
    boolean structuredConcurrency,
    boolean structuredConcurrencySkills
) {

    /// Returns {@code true} if global Structured Concurrency is enabled.
    public boolean isStructuredConcurrencyEnabled() {
        return isEnabled(structuredConcurrency);
    }

    /// Returns {@code true} if Structured Concurrency is enabled for skill execution.
    public boolean isStructuredConcurrencyEnabledForSkills() {
        return isEnabled(structuredConcurrencySkills);
    }

    private static boolean isEnabled(boolean value) {
        return value;
    }
}
