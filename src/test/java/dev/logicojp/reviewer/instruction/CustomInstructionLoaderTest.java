package dev.logicojp.reviewer.instruction;

import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CustomInstructionLoader.
 */
class CustomInstructionLoaderTest {

    @TempDir
    Path tempDir;

    private CustomInstructionLoader loader;

    @BeforeEach
    void setUp() {
        loader = new CustomInstructionLoader();
    }

    // === Load from Local Directory Tests ===

    @Test
    void loadFromLocalDirectory_shouldReturnEmptyForEmptyDirectory() {
        Optional<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);
        assertThat(result).isEmpty();
    }

    @Test
    void loadFromLocalDirectory_shouldReturnEmptyForNullDirectory() {
        Optional<CustomInstruction> result = loader.loadFromLocalDirectory(null);
        assertThat(result).isEmpty();
    }

    @Test
    void loadFromLocalDirectory_shouldLoadFromGitHubCopilotInstructions() throws IOException {
        Path githubDir = tempDir.resolve(".github");
        Files.createDirectories(githubDir);
        Files.writeString(githubDir.resolve("copilot-instructions.md"), "# Instructions\nDo this");

        Optional<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

        assertThat(result).isPresent();
        assertThat(result.get().content()).contains("Do this");
        assertThat(result.get().source()).isEqualTo(InstructionSource.LOCAL_FILE);
    }

    @Test
    void loadFromLocalDirectory_shouldLoadFromCopilotDirectory() throws IOException {
        Path copilotDir = tempDir.resolve(".copilot");
        Files.createDirectories(copilotDir);
        Files.writeString(copilotDir.resolve("instructions.md"), "Use this style");

        Optional<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

        assertThat(result).isPresent();
        assertThat(result.get().content()).contains("Use this style");
    }

    @Test
    void loadFromLocalDirectory_shouldLoadFromRootCopilotInstructions() throws IOException {
        Files.writeString(tempDir.resolve("copilot-instructions.md"), "Root instructions");

        Optional<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

        assertThat(result).isPresent();
        assertThat(result.get().content()).contains("Root instructions");
    }

    @Test
    void loadFromLocalDirectory_shouldLoadFromInstructionsMd() throws IOException {
        Files.writeString(tempDir.resolve("INSTRUCTIONS.md"), "Project instructions");

        Optional<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

        assertThat(result).isPresent();
        assertThat(result.get().content()).contains("Project instructions");
    }

    @Test
    void loadFromLocalDirectory_shouldLoadFromDotInstructions() throws IOException {
        Files.writeString(tempDir.resolve(".instructions.md"), "Hidden instructions");

        Optional<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

        assertThat(result).isPresent();
        assertThat(result.get().content()).contains("Hidden instructions");
    }

    @Test
    void loadFromLocalDirectory_shouldMergeMultipleInstructionFiles() throws IOException {
        Path githubDir = tempDir.resolve(".github");
        Files.createDirectories(githubDir);
        Files.writeString(githubDir.resolve("copilot-instructions.md"), "GitHub instruction");
        Files.writeString(tempDir.resolve("INSTRUCTIONS.md"), "Root instruction");

        Optional<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

        assertThat(result).isPresent();
        assertThat(result.get().content()).contains("GitHub instruction");
        assertThat(result.get().content()).contains("Root instruction");
        assertThat(result.get().source()).isEqualTo(InstructionSource.MERGED);
    }

    @Test
    void loadFromLocalDirectory_shouldSkipEmptyFiles() throws IOException {
        Files.writeString(tempDir.resolve("copilot-instructions.md"), "   ");
        Files.writeString(tempDir.resolve("INSTRUCTIONS.md"), "Valid content");

        Optional<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

        assertThat(result).isPresent();
        assertThat(result.get().content()).contains("Valid content");
        assertThat(result.get().content()).doesNotContain("   ");
    }

    // === Load with Additional Paths Tests ===

    @Test
    void loadFromLocalDirectory_shouldLoadFromAdditionalPaths() throws IOException {
        Path additionalFile = tempDir.resolve("custom/my-instructions.md");
        Files.createDirectories(additionalFile.getParent());
        Files.writeString(additionalFile, "Custom instructions");

        CustomInstructionLoader loaderWithPaths = new CustomInstructionLoader(
            List.of(Path.of("custom/my-instructions.md"))
        );

        Optional<CustomInstruction> result = loaderWithPaths.loadFromLocalDirectory(tempDir);

        assertThat(result).isPresent();
        assertThat(result.get().content()).contains("Custom instructions");
    }

    @Test
    void loadFromLocalDirectory_shouldLoadFromAbsoluteAdditionalPath() throws IOException {
        Path additionalFile = tempDir.resolve("external.md");
        Files.writeString(additionalFile, "External content");

        CustomInstructionLoader loaderWithPaths = new CustomInstructionLoader(
            List.of(additionalFile)
        );

        Optional<CustomInstruction> result = loaderWithPaths.loadFromLocalDirectory(tempDir);

        assertThat(result).isPresent();
        assertThat(result.get().content()).contains("External content");
    }

    // === Load for Target Tests ===

    @Test
    void loadForTarget_shouldLoadForLocalTarget() throws IOException {
        Files.writeString(tempDir.resolve("copilot-instructions.md"), "Local target instructions");
        ReviewTarget target = ReviewTarget.local(tempDir);

        Optional<CustomInstruction> result = loader.loadForTarget(target);

        assertThat(result).isPresent();
        assertThat(result.get().content()).contains("Local target instructions");
    }

    @Test
    void loadForTarget_shouldReturnEmptyForGitHubTarget() {
        ReviewTarget target = ReviewTarget.gitHub("owner/repo");

        Optional<CustomInstruction> result = loader.loadForTarget(target);

        assertThat(result).isEmpty();
    }

    // === CustomInstruction Record Tests ===

    @Test
    void customInstruction_isEmpty_shouldReturnTrueForNullContent() {
        CustomInstruction instruction = new CustomInstruction("path", null, InstructionSource.LOCAL_FILE);
        assertThat(instruction.isEmpty()).isTrue();
    }

    @Test
    void customInstruction_isEmpty_shouldReturnTrueForBlankContent() {
        CustomInstruction instruction = new CustomInstruction("path", "   ", InstructionSource.LOCAL_FILE);
        assertThat(instruction.isEmpty()).isTrue();
    }

    @Test
    void customInstruction_isEmpty_shouldReturnFalseForValidContent() {
        CustomInstruction instruction = new CustomInstruction("path", "content", InstructionSource.LOCAL_FILE);
        assertThat(instruction.isEmpty()).isFalse();
    }

    // === toPromptSection Tests ===

    @Test
    void toPromptSection_shouldFormatCorrectly() {
        CustomInstruction instruction = new CustomInstruction("path", "Do this thing", InstructionSource.LOCAL_FILE);

        String formatted = instruction.toPromptSection();

        assertThat(formatted).contains("カスタムインストラクション");
        assertThat(formatted).contains("Do this thing");
    }
}
