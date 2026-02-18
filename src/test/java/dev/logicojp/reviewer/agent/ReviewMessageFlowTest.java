package dev.logicojp.reviewer.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewMessageFlow")
class ReviewMessageFlowTest {

    private static ReviewMessageFlow newFlow() {
        return new ReviewMessageFlow(
            "security",
            "FOLLOWUP",
            "LOCAL_HEADER",
            "LOCAL_RESULT",
            32
        );
    }

    @Test
    @DisplayName("リモートレビューで初回応答があれば1回送信で終了する")
    void remoteSuccessOnFirstSend() throws Exception {
        ReviewMessageFlow flow = newFlow();
        List<String> prompts = new ArrayList<>();

        String result = flow.execute("INSTRUCTION", null, prompt -> {
            prompts.add(prompt);
            return "REMOTE_OK";
        });

        assertThat(result).isEqualTo("REMOTE_OK");
        assertThat(prompts).containsExactly("INSTRUCTION");
    }

    @Test
    @DisplayName("リモートレビューで空応答ならフォローアップを送信する")
    void remoteFallsBackToFollowUp() throws Exception {
        ReviewMessageFlow flow = newFlow();
        List<String> prompts = new ArrayList<>();
        AtomicInteger count = new AtomicInteger();

        String result = flow.execute("INSTRUCTION", null, prompt -> {
            prompts.add(prompt);
            return count.getAndIncrement() == 0 ? "" : "FOLLOWUP_OK";
        });

        assertThat(result).isEqualTo("FOLLOWUP_OK");
        assertThat(prompts).containsExactly("INSTRUCTION", "FOLLOWUP");
    }

    @Test
    @DisplayName("ローカルレビューでは instruction/header と source を分割送信する")
    void localSendsInstructionAndSourceSeparately() throws Exception {
        ReviewMessageFlow flow = newFlow();
        List<String> prompts = new ArrayList<>();

        String result = flow.execute("INSTRUCTION", "SOURCE", prompt -> {
            prompts.add(prompt);
            return "SOURCE".equals(prompt) ? "LOCAL_OK" : "";
        });

        assertThat(result).isEqualTo("LOCAL_OK");
        assertThat(prompts).hasSize(2);
        assertThat(prompts.getFirst()).contains("INSTRUCTION");
        assertThat(prompts.getFirst()).contains("LOCAL_HEADER");
        assertThat(prompts.get(1)).isEqualTo("SOURCE");
    }

    @Test
    @DisplayName("ローカル初回が空なら結果要求プロンプトを送る")
    void localFallsBackToResultRequest() throws Exception {
        ReviewMessageFlow flow = newFlow();
        List<String> prompts = new ArrayList<>();

        String result = flow.execute("INSTRUCTION", "SOURCE", prompt -> {
            prompts.add(prompt);
            if ("LOCAL_RESULT".equals(prompt)) {
                return "LOCAL_RESULT_OK";
            }
            return "";
        });

        assertThat(result).isEqualTo("LOCAL_RESULT_OK");
        assertThat(prompts).hasSize(3);
        assertThat(prompts.get(1)).isEqualTo("SOURCE");
        assertThat(prompts.get(2)).isEqualTo("LOCAL_RESULT");
    }

    @Test
    @DisplayName("ローカルで結果要求も空ならフォローアップに進む")
    void localFallsBackToFollowUpWhenStillEmpty() throws Exception {
        ReviewMessageFlow flow = newFlow();
        List<String> prompts = new ArrayList<>();

        String result = flow.execute("INSTRUCTION", "SOURCE", prompt -> {
            prompts.add(prompt);
            if (!"FOLLOWUP".equals(prompt)) {
                return " ";
            }
            return "FOLLOWUP_OK";
        });

        assertThat(result).isEqualTo("FOLLOWUP_OK");
        assertThat(prompts).hasSize(4);
        assertThat(prompts.getFirst()).contains("INSTRUCTION");
        assertThat(prompts.get(1)).isEqualTo("SOURCE");
        assertThat(prompts.get(2)).isEqualTo("LOCAL_RESULT");
        assertThat(prompts.get(3)).isEqualTo("FOLLOWUP");
    }

    @Test
    @DisplayName("差し替えた応答判定戦略を利用する")
    void usesInjectedResponseEvaluator() throws Exception {
        ReviewMessageFlow flow = newFlow();
        AtomicBoolean evaluatorCalled = new AtomicBoolean(false);

        String result = flow.execute(
            "INSTRUCTION",
            null,
            _ -> "   ",
            content -> {
                evaluatorCalled.set(true);
                return true;
            }
        );

        assertThat(evaluatorCalled).isTrue();
        assertThat(result).isEqualTo("   ");
    }
}
