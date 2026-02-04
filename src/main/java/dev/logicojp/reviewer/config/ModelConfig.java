package dev.logicojp.reviewer.config;

/**
 * Configuration for LLM models used in different stages of the review process.
 * Allows specifying different models for review, report generation, and summary.
 */
public class ModelConfig {
    
    private String reviewModel;
    private String reportModel;
    private String summaryModel;
    
    public ModelConfig() {
        // Default models
        this.reviewModel = "claude-sonnet-4";
        this.reportModel = "claude-sonnet-4";
        this.summaryModel = "claude-sonnet-4";
    }
    
    public ModelConfig(String defaultModel) {
        this.reviewModel = defaultModel;
        this.reportModel = defaultModel;
        this.summaryModel = defaultModel;
    }
    
    /**
     * Model used for code review by agents.
     * This is the main model that analyzes the code.
     */
    public String getReviewModel() {
        return reviewModel;
    }
    
    public void setReviewModel(String reviewModel) {
        this.reviewModel = reviewModel;
    }
    
    /**
     * Model used for generating individual review reports.
     * If not specified, uses the agent's configured model.
     */
    public String getReportModel() {
        return reportModel;
    }
    
    public void setReportModel(String reportModel) {
        this.reportModel = reportModel;
    }
    
    /**
     * Model used for generating the executive summary.
     */
    public String getSummaryModel() {
        return summaryModel;
    }
    
    public void setSummaryModel(String summaryModel) {
        this.summaryModel = summaryModel;
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
            ModelConfig config = new ModelConfig();
            config.setReviewModel(reviewModel);
            config.setReportModel(reportModel);
            config.setSummaryModel(summaryModel);
            return config;
        }
    }
}
