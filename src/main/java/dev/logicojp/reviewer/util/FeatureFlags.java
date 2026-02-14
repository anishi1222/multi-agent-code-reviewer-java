package dev.logicojp.reviewer.util;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.inject.Singleton;

/// Centralized feature flags backed by Micronaut configuration binding.
@Singleton
@ConfigurationProperties("reviewer.feature-flags")
public record FeatureFlags(
    boolean structuredConcurrency,
    boolean structuredConcurrencySkills
) {

    /// Returns {@code true} if global Structured Concurrency is enabled.
    public boolean isStructuredConcurrencyEnabled() {
        return structuredConcurrency;
    }

    /// Returns {@code true} if Structured Concurrency is enabled for skill execution.
    public boolean isStructuredConcurrencyEnabledForSkills() {
        return structuredConcurrencySkills;
    }
}
