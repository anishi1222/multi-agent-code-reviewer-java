package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;

/// Configuration for executive summary generation settings.
@ConfigurationProperties("reviewer.summary")
public record SummaryConfig(
    int maxContentPerAgent,
    int maxTotalPromptContent,
    int fallbackExcerptLength
) {

    public static final int DEFAULT_MAX_CONTENT_PER_AGENT = 50_000;
    public static final int DEFAULT_MAX_TOTAL_PROMPT_CONTENT = 200_000;
    public static final int DEFAULT_FALLBACK_EXCERPT_LENGTH = 180;

    public SummaryConfig {
        maxContentPerAgent = ConfigDefaults.defaultIfNonPositive(maxContentPerAgent, DEFAULT_MAX_CONTENT_PER_AGENT);
        maxTotalPromptContent = ConfigDefaults.defaultIfNonPositive(
            maxTotalPromptContent,
            DEFAULT_MAX_TOTAL_PROMPT_CONTENT
        );
        fallbackExcerptLength = ConfigDefaults.defaultIfNonPositive(
            fallbackExcerptLength,
            DEFAULT_FALLBACK_EXCERPT_LENGTH
        );
    }
}