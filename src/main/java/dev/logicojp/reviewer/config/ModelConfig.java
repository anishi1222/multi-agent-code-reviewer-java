package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Configuration for LLM models used in different stages of the review process.
 * Allows specifying different models for review, report generation, and summary.
 */
@ConfigurationProperties("reviewer.models")
public record ModelConfig(
    String reviewModel,
    String reportModel,
    String summaryModel
) {

    public ModelConfig {
        reviewModel = (reviewModel == null || reviewModel.isBlank())
            ? "claude-sonnet-4" : reviewModel;
        reportModel = (reportModel == null || reportModel.isBlank())
            ? "claude-sonnet-4" : reportModel;
        summaryModel = (summaryModel == null || summaryModel.isBlank())
            ? "claude-sonnet-4" : summaryModel;
    }

    public ModelConfig() {
        this("claude-sonnet-4", "claude-sonnet-4", "claude-sonnet-4");
    }

    public ModelConfig(String defaultModel) {
        this(defaultModel, defaultModel, defaultModel);
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
        return String.format("ModelConfig{review='%s', report='%s', summary='%s'}",
            reviewModel, reportModel, summaryModel);
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

        public Builder defaultModel(String model) {
            this.reviewModel = model;
            this.reportModel = model;
            this.summaryModel = model;
            return this;
        }

        public ModelConfig build() {
            return new ModelConfig(reviewModel, reportModel, summaryModel);
        }
    }
}
