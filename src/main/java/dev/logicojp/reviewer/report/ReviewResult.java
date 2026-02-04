package dev.logicojp.reviewer.report;

import dev.logicojp.reviewer.agent.AgentConfig;

import java.time.LocalDateTime;

/**
 * Holds the result of a review performed by an agent.
 */
public class ReviewResult {
    
    private final AgentConfig agentConfig;
    private final String repository;
    private final String content;
    private final LocalDateTime timestamp;
    private final boolean success;
    private final String errorMessage;
    
    private ReviewResult(Builder builder) {
        this.agentConfig = builder.agentConfig;
        this.repository = builder.repository;
        this.content = builder.content;
        this.timestamp = builder.timestamp;
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
    }
    
    public AgentConfig getAgentConfig() {
        return agentConfig;
    }
    
    public String getRepository() {
        return repository;
    }
    
    public String getContent() {
        return content;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private AgentConfig agentConfig;
        private String repository;
        private String content;
        private LocalDateTime timestamp = LocalDateTime.now();
        private boolean success = true;
        private String errorMessage;
        
        public Builder agentConfig(AgentConfig agentConfig) {
            this.agentConfig = agentConfig;
            return this;
        }
        
        public Builder repository(String repository) {
            this.repository = repository;
            return this;
        }
        
        public Builder content(String content) {
            this.content = content;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public ReviewResult build() {
            return new ReviewResult(this);
        }
    }
}
