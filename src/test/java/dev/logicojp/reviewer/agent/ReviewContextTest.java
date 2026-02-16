package dev.logicojp.reviewer.agent;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.CopilotClientOptions;
import dev.logicojp.reviewer.config.LocalFileConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReviewContext")
class ReviewContextTest {

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("toStringは主要フィールドを含む")
        void toStringContainsContextSummary() {
            var context = new ReviewContext(
                null,
                5,
                3,
                List.of(),
                null,
                2,
                null,
                null,
                null,
                0,
                0,
                new LocalFileConfig(),
                null);

            String result = context.toString();

            assertThat(result).contains("ReviewContext");
            assertThat(result).contains("timeoutMinutes=5");
        }
    }

    @Nested
    @DisplayName("不変性")
    class Immutability {

        @Test
        @DisplayName("customInstructionsがnullの場合は空リストになる")
        void nullCustomInstructionsBecomesEmptyList() {
            var context = new ReviewContext(
                null,
                5,
                3,
                null,
                null,
                2,
                null,
                null,
                null,
                0,
                0,
                new LocalFileConfig(),
                null);

            assertThat(context.customInstructions()).isEmpty();
        }

        @Test
        @DisplayName("BuilderでReviewContextを構築できる")
        void buildWithBuilder() {
            var scheduler = Executors.newSingleThreadScheduledExecutor();
            var client = new CopilotClient(new CopilotClientOptions());
            var context = ReviewContext.builder()
                .client(client)
                .timeoutMinutes(5)
                .idleTimeoutMinutes(3)
                .maxRetries(2)
                .sharedScheduler(scheduler)
                .build();

            try {
                assertThat(context.timeoutMinutes()).isEqualTo(5);
                assertThat(context.idleTimeoutMinutes()).isEqualTo(3);
                assertThat(context.maxRetries()).isEqualTo(2);
                assertThat(context.customInstructions()).isEmpty();
                assertThat(context.localFileConfig()).isNotNull();
            } finally {
                scheduler.shutdownNow();
                client.close();
            }
        }

        @Test
        @DisplayName("Builderでclient未設定の場合は例外を投げる")
        void builderWithoutClientThrows() {
            var scheduler = Executors.newSingleThreadScheduledExecutor();
            try {
                assertThatThrownBy(() -> ReviewContext.builder()
                    .timeoutMinutes(5)
                    .idleTimeoutMinutes(3)
                    .sharedScheduler(scheduler)
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("client");
            } finally {
                scheduler.shutdownNow();
            }
        }

        @Test
        @DisplayName("BuilderでsharedScheduler未設定の場合は例外を投げる")
        void builderWithoutSchedulerThrows() {
            var client = new CopilotClient(new CopilotClientOptions());
            try {
                assertThatThrownBy(() -> ReviewContext.builder()
                    .client(client)
                    .timeoutMinutes(5)
                    .idleTimeoutMinutes(3)
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("sharedScheduler");
            } finally {
                client.close();
            }
        }
    }
}
