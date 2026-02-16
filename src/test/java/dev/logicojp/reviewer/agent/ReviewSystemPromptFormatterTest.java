package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.instruction.InstructionSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewSystemPromptFormatter")
class ReviewSystemPromptFormatterTest {

    @Test
    @DisplayName("outputConstraintsとcustomInstructionsを順序通りに連結する")
    void formatsSystemPromptWithConstraintsAndInstructions() {
        var formatter = new ReviewSystemPromptFormatter();
        var instruction = new CustomInstruction(
            ".github/instructions/test.instructions.md",
            "Use project rule",
            InstructionSource.LOCAL_FILE,
            "**/*.java",
            "java rules"
        );

        String prompt = formatter.format(
            "BASE_PROMPT",
            "OUTPUT_CONSTRAINTS",
            List.of(instruction),
            _ -> {
            }
        );

        assertThat(prompt).contains("BASE_PROMPT");
        assertThat(prompt).contains("OUTPUT_CONSTRAINTS");
        assertThat(prompt).contains("--- BEGIN PROJECT INSTRUCTIONS ---");
        assertThat(prompt).contains("カスタムインストラクション");
        assertThat(prompt).contains("Use project rule");
        assertThat(prompt).contains("--- END PROJECT INSTRUCTIONS ---");
    }

    @Test
    @DisplayName("空のcustomInstructionは無視されlistenerは呼ばれない")
    void ignoresEmptyInstructionAndDoesNotNotifyListener() {
        var formatter = new ReviewSystemPromptFormatter();
        var emptyInstruction = new CustomInstruction(
            ".github/instructions/empty.instructions.md",
            "   ",
            InstructionSource.LOCAL_FILE,
            null,
            null
        );
        var called = new AtomicInteger(0);

        String prompt = formatter.format(
            "BASE_PROMPT",
            null,
            List.of(emptyInstruction),
            _ -> called.incrementAndGet()
        );

        assertThat(called.get()).isZero();
        assertThat(prompt).contains("BASE_PROMPT");
    }
}