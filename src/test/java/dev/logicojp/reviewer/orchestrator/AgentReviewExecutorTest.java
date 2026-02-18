package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentReviewExecutor")
class AgentReviewExecutorTest {

    private AgentConfig agentConfig() {
        return new AgentConfig("security", "Security", "model", "system", "instruction", null, List.of(), List.of());
    }

    private ReviewContext context() {
        return ReviewContext.builder()
            .client(new com.github.copilot.sdk.CopilotClient(new com.github.copilot.sdk.json.CopilotClientOptions()))
            .timeoutMinutes(1)
            .idleTimeoutMinutes(1)
            .customInstructions(List.of())
            .maxRetries(0)
            .localFileConfig(new LocalFileConfig())
            .sharedScheduler(Executors.newSingleThreadScheduledExecutor())
            .build();
    }

    @Test
    @DisplayName("正常系ではレビュー結果を返す")
    void returnsSuccessResult() {
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        var ctx = context();
        try {
            var executor = new AgentReviewExecutor(
                new Semaphore(1),
                executorService,
                (config, context) -> target -> ReviewResult.builder()
                    .agentConfig(config)
                    .repository(target.displayName())
                    .content("ok")
                    .success(true)
                    .timestamp(Instant.now())
                    .build()
            );

            var result = executor.executeAgentSafely(
                agentConfig(),
                ReviewTarget.gitHub("owner/repo"),
                ctx,
                1
            );

            assertThat(result.success()).isTrue();
            assertThat(result.content()).isEqualTo("ok");
        } finally {
            executorService.close();
            ctx.client().close();
            ctx.sharedScheduler().close();
        }
    }

    @Test
    @DisplayName("実行例外は失敗結果に変換される")
    void mapsExecutionExceptionToFailureResult() {
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        var ctx = context();
        try {
            var executor = new AgentReviewExecutor(
                new Semaphore(1),
                executorService,
                (config, context) -> target -> {
                    throw new IllegalStateException("boom");
                }
            );

            var result = executor.executeAgentSafely(
                agentConfig(),
                ReviewTarget.gitHub("owner/repo"),
                ctx,
                1
            );

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("Review failed:");
        } finally {
            executorService.close();
            ctx.client().close();
            ctx.sharedScheduler().close();
        }
    }
}