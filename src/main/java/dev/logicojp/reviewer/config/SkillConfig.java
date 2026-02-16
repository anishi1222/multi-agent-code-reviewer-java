package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

/// Configuration for skill file loading.
@ConfigurationProperties("reviewer.skills")
public record SkillConfig(
    @Nullable String filename,
    @Nullable String directory
) {

    private static final String DEFAULT_FILENAME = "SKILL.md";
    private static final String DEFAULT_DIRECTORY = ".github/skills";

    public SkillConfig {
        filename = defaultIfBlank(filename, DEFAULT_FILENAME);
        directory = defaultIfBlank(directory, DEFAULT_DIRECTORY);
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
