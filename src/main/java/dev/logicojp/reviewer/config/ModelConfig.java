package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Configuration for LLM models used in different stages of the review process.
 * Allows specifying different models for review, report generation, and summary.
 *
 * <p>{@code reasoningEffort} controls the effort level for reasoning models
 * (e.g. Claude Opus, o3). Valid values are {@code "low"}, {@code "medium"},
 * {@code "high"}. The value is only applied to models that support reasoning
 * effort; non-reasoning models ignore it.</p>
 */
@ConfigurationProperties("reviewer.models")
public record ModelConfig(
    String reviewModel,
    String reportModel,
    String summaryModel,
    String reasoningEffort
) {

    /** Default reasoning effort applied to reasoning models. */
    public static final String DEFAULT_REASONING_EFFORT = "high";

    public ModelConfig {
        reviewModel = (reviewModel == null || reviewModel.isBlank())
            ? "claude-sonnet-4" : reviewModel;
        reportModel = (reportModel == null || reportModel.isBlank())
            ? "claude-sonnet-4" : reportModel;
        summaryModel = (summaryModel == null || summaryModel.isBlank())
            ? "claude-sonnet-4" : summaryModel;
        reasoningEffort = (reasoningEffort == null || reasoningEffort.isBlank())
            ? DEFAULT_REASONING_EFFORT : reasoningEffort;
    }

    public ModelConfig() {
        this("claude-sonnet-4", "claude-sonnet-4", "claude-sonnet-4", DEFAULT_REASONING_EFFORT);
    }

    public ModelConfig(String defaultModel) {
        this(defaultModel, defaultModel, defaultModel, DEFAULT_REASONING_EFFORT);
    }

    public ModelConfig(String reviewModel, String reportModel, String summaryModel) {
        this(reviewModel, reportModel, summaryModel, DEFAULT_REASONING_EFFORT);
    }

    /**
     * Checks whether the given model is a reasoning model that requires
     * explicit {@code reasoningEffort} configuration.
     *
     * @param model the model identifier
     * @return {@code true} if effort should be set for this model
     */
    public static boolean isReasoningModel(String model) {
        if (model == null) {
            return false;
        }
        String lower = model.toLowerCase();
        return lower.contains("opus") || lower.startsWith("o3") || lower.startsWith("o4-mini");
    }

    /**
     * Gets the effective model for review, considering agent override.
     * @param agentModel The model specified in the agent config (may be null)
     * @return The model to use for this agent's review
     */
    public String getEffectiveReviewModel(String agentModel) {
        if (agentModel != null && !agentModel.isEmpty()) {
            return agentModel;
        }
        return reviewModel;
    }

    @Override
    public String toString() {
        return String.format("ModelConfig{review='%s', report='%s', summary='%s', reasoningEffort='%s'}",
            reviewModel, reportModel, summaryModel, reasoningEffort);
    }

    /**
     * Creates a builder for ModelConfig.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String reviewModel = "claude-sonnet-4";
        private String reportModel = "claude-sonnet-4";
        private String summaryModel = "claude-sonnet-4";
        private String reasoningEffort = DEFAULT_REASONING_EFFORT;

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

        public Builder defaultModel(String model) {
            this.reviewModel = model;
            this.reportModel = model;
            this.summaryModel = model;
            return this;
        }

        public ModelConfig build() {
            return new ModelConfig(reviewModel, reportModel, summaryModel, reasoningEffort);
        }
    }
}
