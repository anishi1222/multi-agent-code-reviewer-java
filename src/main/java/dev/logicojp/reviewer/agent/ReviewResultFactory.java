package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.report.core.ReviewResult;

import java.time.LocalDateTime;

final class ReviewResultFactory {

    ReviewResult fromException(AgentConfig config, String repository, Exception e) {
        return baseBuilder(config, repository)
            .success(false)
            .errorMessage(e.getMessage())
            .timestamp(now())
            .build();
    }

    ReviewResult emptyContentFailure(AgentConfig config, String repository, boolean usedMcp) {
        String errorMsg = usedMcp
            ? "Agent returned empty review content — model may have timed out during MCP tool calls"
            : "Agent returned empty review content";
        return baseBuilder(config, repository)
            .success(false)
            .errorMessage(errorMsg)
            .timestamp(now())
            .build();
    }

    ReviewResult success(AgentConfig config, String repository, String content) {
        return baseBuilder(config, repository)
            .content(content)
            .success(true)
            .timestamp(now())
            .build();
    }

    private ReviewResult.Builder baseBuilder(AgentConfig config, String repository) {
        return ReviewResult.builder()
            .agentConfig(config)
            .repository(repository);
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }
}