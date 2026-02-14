package dev.logicojp.reviewer.util;

/// Centralized feature flag resolution for the application.
///
/// Checks environment variables first, then JVM system properties.
/// This utility consolidates flag detection that was previously
/// duplicated in {@code ReviewOrchestrator} and {@code SkillExecutor}.
public final class FeatureFlags {

    private FeatureFlags() {}

    /// Returns {@code true} if global Structured Concurrency is enabled.
    public static boolean isStructuredConcurrencyEnabled() {
        return resolveFlag("REVIEWER_STRUCTURED_CONCURRENCY",
                           "reviewer.structuredConcurrency");
    }

    /// Returns {@code true} if Structured Concurrency is enabled for skill execution.
    /// Falls back to the global flag if the skill-specific flag is not set.
    public static boolean isStructuredConcurrencyEnabledForSkills() {
        String env = System.getenv("REVIEWER_STRUCTURED_CONCURRENCY_SKILLS");
        if (env != null && !env.isBlank()) {
            return Boolean.parseBoolean(env);
        }
        if (System.getProperties().containsKey("reviewer.structuredConcurrency.skills")) {
            return Boolean.getBoolean("reviewer.structuredConcurrency.skills");
        }
        // Fall back to global flag
        return isStructuredConcurrencyEnabled();
    }

    /// Resolves a boolean flag from an environment variable and a JVM system property.
    private static boolean resolveFlag(String envVar, String sysProp) {
        String env = System.getenv(envVar);
        if (env != null && !env.isBlank()) {
            return Boolean.parseBoolean(env);
        }
        return Boolean.getBoolean(sysProp);
    }
}
