package dev.logicojp.reviewer.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
                null);

            assertThat(context.customInstructions()).isEmpty();
        }

        @Test
        @DisplayName("BuilderでReviewContextを構築できる")
        void buildWithBuilder() {
            var context = ReviewContext.builder()
                .timeoutMinutes(5)
                .idleTimeoutMinutes(3)
                .maxRetries(2)
                .build();

            assertThat(context.timeoutMinutes()).isEqualTo(5);
            assertThat(context.idleTimeoutMinutes()).isEqualTo(3);
            assertThat(context.maxRetries()).isEqualTo(2);
            assertThat(context.customInstructions()).isEmpty();
        }
    }
}
