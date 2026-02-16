package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.instruction.CustomInstructionLoader;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewCustomInstructionResolver")
class ReviewCustomInstructionResolverTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("--no-instructions指定時は空を返す")
    void returnsEmptyWhenNoInstructionsIsEnabled() {
        var resolver = newResolver();

        var result = resolver.resolve(
            ReviewTarget.local(tempDir),
            new ReviewCustomInstructionResolver.InstructionOptions(List.of(), true, false, false)
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("明示パスの安全な命令を読み込む")
    void loadsSafeInstructionFromExplicitPath() throws Exception {
        Path instruction = tempDir.resolve("custom.md");
        Files.writeString(instruction, "Follow project coding standards.");
        var outputCapture = new ByteArrayOutputStream();
        var resolver = newResolver(outputCapture);

        var result = resolver.resolve(
            ReviewTarget.gitHub("owner/repo"),
            new ReviewCustomInstructionResolver.InstructionOptions(List.of(instruction), false, false, false)
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().sourcePath()).isEqualTo(instruction.toString());
        assertThat(outputCapture.toString()).contains("✓ Loaded instructions");
    }

    @Test
    @DisplayName("未trustのローカルターゲットではターゲット命令を読み込まない")
    void skipsTargetInstructionsWithoutTrustFlag() throws Exception {
        Path targetInstruction = tempDir.resolve(".github/copilot-instructions.md");
        Files.createDirectories(targetInstruction.getParent());
        Files.writeString(targetInstruction, "Local target instruction");
        var outputCapture = new ByteArrayOutputStream();
        var resolver = newResolver(outputCapture);

        var result = resolver.resolve(
            ReviewTarget.local(tempDir),
            new ReviewCustomInstructionResolver.InstructionOptions(List.of(), false, false, false)
        );

        assertThat(result).isEmpty();
        assertThat(outputCapture.toString()).contains("Target instructions skipped");
    }

    @Test
    @DisplayName("--trust指定時はローカルターゲット命令を読み込む")
    void loadsTargetInstructionsWhenTrusted() throws Exception {
        Path targetInstruction = tempDir.resolve(".github/copilot-instructions.md");
        Files.createDirectories(targetInstruction.getParent());
        Files.writeString(targetInstruction, "Use strict review policy");
        var outputCapture = new ByteArrayOutputStream();
        var resolver = newResolver(outputCapture);

        var result = resolver.resolve(
            ReviewTarget.local(tempDir),
            new ReviewCustomInstructionResolver.InstructionOptions(List.of(), false, true, true)
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().sourcePath()).isEqualTo(targetInstruction.toString());
        assertThat(outputCapture.toString()).contains("--trust enabled");
    }

    private ReviewCustomInstructionResolver newResolver() {
        return newResolver(new ByteArrayOutputStream());
    }

    private ReviewCustomInstructionResolver newResolver(ByteArrayOutputStream outBuffer) {
        var loader = new CustomInstructionLoader(null, false);
        var output = new CliOutput(new PrintStream(outBuffer), new PrintStream(new ByteArrayOutputStream()));
        return new ReviewCustomInstructionResolver(loader, output);
    }
}
