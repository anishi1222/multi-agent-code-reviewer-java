package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.List;

/// Configuration for LLM models used in different stages of the review process.
/// Allows specifying different models for review, report generation, and summary.
///
/// `reasoningEffort` controls the effort level for reasoning models
/// (e.g. Claude Opus, o3). Valid values are `"low"`, `"medium"`,
/// `"high"`. The value is only applied to models that support reasoning
/// effort; non-reasoning models ignore it.
///
/// `defaultModel` provides a single YAML-configurable fallback
/// (`reviewer.models.default-model`). When `reviewModel`, `reportModel`,
/// or `summaryModel` is not explicitly configured, `defaultModel` is used.
/// If `defaultModel` itself is not set, `DEFAULT_MODEL` is applied.
@ConfigurationProperties("reviewer.models")
public record ModelConfig(
    String reviewModel,
    String reportModel,
    String summaryModel,
    String reasoningEffort,
    String defaultModel
) {

    /// Hardcoded last-resort default model, used when no configuration is provided.
    public static final String DEFAULT_MODEL = "claude-sonnet-4.5";

    /// Default reasoning effort applied to reasoning models.
    public static final String DEFAULT_REASONING_EFFORT = "high";

    /// Patterns matched via `String.contains` to identify reasoning models.
    private static final List<String> REASONING_CONTAINS_PATTERNS = List.of("opus");

    /// Prefixes matched via `String.startsWith` to identify reasoning models.
    private static final List<String> REASONING_PREFIX_PATTERNS = List.of("o3", "o4-mini");

    public ModelConfig {
        defaultModel = resolveDefaultModel(defaultModel);
        reviewModel = resolveStageModel(reviewModel, defaultModel);
        reportModel = resolveStageModel(reportModel, defaultModel);
        summaryModel = resolveStageModel(summaryModel, defaultModel);
        reasoningEffort = resolveReasoningEffortValue(reasoningEffort);
    }

    private static String resolveDefaultModel(String defaultModel) {
        return (defaultModel == null || defaultModel.isBlank())
            ? DEFAULT_MODEL : defaultModel;
    }

    private static String resolveStageModel(String stageModel, String defaultModel) {
        return (stageModel == null || stageModel.isBlank())
            ? defaultModel : stageModel;
    }

    private static String resolveReasoningEffortValue(String reasoningEffort) {
        return (reasoningEffort == null || reasoningEffort.isBlank())
            ? DEFAULT_REASONING_EFFORT : reasoningEffort;
    }

    public ModelConfig() {
        this(null, null, null, DEFAULT_REASONING_EFFORT, DEFAULT_MODEL);
    }

    /// Determines the appropriate reasoning effort for a model.
    /// If the model is a reasoning model, returns the configured
    /// {@code reasoningEffort} value. Otherwise returns {@code null}.
    ///
    /// @param model the model name
    /// @param configuredEffort the configured reasoning effort value
    /// @return the reasoning effort level, or null if not a reasoning model
    public static String resolveReasoningEffort(String model, String configuredEffort) {
        if (isReasoningModel(model)) {
            return configuredEffort != null ? configuredEffort : DEFAULT_REASONING_EFFORT;
        }
        return null;
    }

    /// Checks whether the given model is a reasoning model that requires
    /// explicit `reasoningEffort` configuration.
    ///
    /// @param model the model identifier
    /// @return `true` if effort should be set for this model
    public static boolean isReasoningModel(String model) {
        if (model == null) {
            return false;
        }
        String lower = model.toLowerCase();
        return REASONING_CONTAINS_PATTERNS.stream().anyMatch(lower::contains)
            || REASONING_PREFIX_PATTERNS.stream().anyMatch(lower::startsWith);
    }

    @Override
    public String toString() {
        return "ModelConfig{review='%s', report='%s', summary='%s', reasoningEffort='%s', default='%s'}"
            .formatted(reviewModel, reportModel, summaryModel, reasoningEffort, defaultModel);
    }

    /// Creates a builder for ModelConfig.
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String reviewModel;
        private String reportModel;
        private String summaryModel;
        private String reasoningEffort = DEFAULT_REASONING_EFFORT;
        private String defaultModelField;

        public Builder reviewModel(String model) {
            this.reviewModel = model;
            return this;
        }

        public Builder reportModel(String model) {
            this.reportModel = model;
            return this;
        }

        public Builder summaryModel(String model) {
            this.summaryModel = model;
            return this;
        }

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        /// Sets all three model fields (`reviewModel`, `reportModel`,
        /// `summaryModel`) to the same value and records it as the
        /// default model.
        public Builder allModels(String model) {
            this.defaultModelField = model;
            this.reviewModel = model;
            this.reportModel = model;
            this.summaryModel = model;
            return this;
        }

        public ModelConfig build() {
            return new ModelConfig(reviewModel, reportModel, summaryModel, reasoningEffort, defaultModelField);
        }
    }
}
