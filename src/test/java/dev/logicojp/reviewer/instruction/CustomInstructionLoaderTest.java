package dev.logicojp.reviewer.instruction;

import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/// Unit tests for CustomInstructionLoader.
class CustomInstructionLoaderTest {

    @TempDir
    Path tempDir;

    private CustomInstructionLoader loader;

    @BeforeEach
    void setUp() {
        loader = new CustomInstructionLoader(null, true, new PromptLoader(), new ScopedInstructionLoader());
    }

    // === Load from Local Directory Tests ===

    @Test
    void loadFromLocalDirectory_shouldReturnEmptyForEmptyDirectory() {
        List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);
        assertThat(result).isEmpty();
    }

    @Test
    void loadFromLocalDirectory_shouldReturnEmptyForNullDirectory() {
        List<CustomInstruction> result = loader.loadFromLocalDirectory(null);
        assertThat(result).isEmpty();
    }

    @Test
    void loadFromLocalDirectory_shouldLoadFromGitHubCopilotInstructions() throws IOException {
        Path githubDir = tempDir.resolve(".github");
        Files.createDirectories(githubDir);
        Files.writeString(githubDir.resolve("copilot-instructions.md"), "# Instructions\nDo this");

        List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().content()).contains("Do this");
        assertThat(result.getFirst().source()).isEqualTo(InstructionSource.LOCAL_FILE);
    }

    @Test
    void loadFromLocalDirectory_shouldLoadFromCopilotDirectory() throws IOException {
        Path copilotDir = tempDir.resolve(".copilot");
        Files.createDirectories(copilotDir);
        Files.writeString(copilotDir.resolve("instructions.md"), "Use this style");

        List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().content()).contains("Use this style");
    }

    @Test
    void loadFromLocalDirectory_shouldLoadFromRootCopilotInstructions() throws IOException {
        Files.writeString(tempDir.resolve("copilot-instructions.md"), "Root instructions");

        List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().content()).contains("Root instructions");
    }

    @Test
    void loadFromLocalDirectory_shouldLoadFromInstructionsMd() throws IOException {
        Files.writeString(tempDir.resolve("INSTRUCTIONS.md"), "Project instructions");

        List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().content()).contains("Project instructions");
    }

    @Test
    void loadFromLocalDirectory_shouldLoadFromDotInstructions() throws IOException {
        Files.writeString(tempDir.resolve(".instructions.md"), "Hidden instructions");

        List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().content()).contains("Hidden instructions");
    }

    @Test
    void loadFromLocalDirectory_shouldReturnMultipleInstructionFiles() throws IOException {
        Path githubDir = tempDir.resolve(".github");
        Files.createDirectories(githubDir);
        Files.writeString(githubDir.resolve("copilot-instructions.md"), "GitHub instruction");
        Files.writeString(tempDir.resolve("INSTRUCTIONS.md"), "Root instruction");

        List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CustomInstruction::content)
            .containsExactly("GitHub instruction", "Root instruction");
        assertThat(result).allMatch(i -> i.source() == InstructionSource.LOCAL_FILE);
    }

    @Test
    void loadFromLocalDirectory_shouldSkipEmptyFiles() throws IOException {
        Files.writeString(tempDir.resolve("copilot-instructions.md"), "   ");
        Files.writeString(tempDir.resolve("INSTRUCTIONS.md"), "Valid content");

        List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().content()).contains("Valid content");
    }

    // === Load with Additional Paths Tests ===

    @Test
    void loadFromLocalDirectory_shouldLoadFromAdditionalPaths() throws IOException {
        Path additionalFile = tempDir.resolve("custom/my-instructions.md");
        Files.createDirectories(additionalFile.getParent());
        Files.writeString(additionalFile, "Custom instructions");

        CustomInstructionLoader loaderWithPaths = new CustomInstructionLoader(
            List.of(Path.of("custom/my-instructions.md")), true,
            new PromptLoader(), new ScopedInstructionLoader()
        );

        List<CustomInstruction> result = loaderWithPaths.loadFromLocalDirectory(tempDir);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().content()).contains("Custom instructions");
    }

    @Test
    void loadFromLocalDirectory_shouldLoadFromAbsoluteAdditionalPath() throws IOException {
        Path additionalFile = tempDir.resolve("external.md");
        Files.writeString(additionalFile, "External content");

        CustomInstructionLoader loaderWithPaths = new CustomInstructionLoader(
            List.of(additionalFile), true,
            new PromptLoader(), new ScopedInstructionLoader()
        );

        List<CustomInstruction> result = loaderWithPaths.loadFromLocalDirectory(tempDir);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().content()).contains("External content");
    }

    // === Load for Target Tests ===

    @Test
    void loadForTarget_shouldLoadForLocalTarget() throws IOException {
        Files.writeString(tempDir.resolve("copilot-instructions.md"), "Local target instructions");
        ReviewTarget target = ReviewTarget.local(tempDir);

        List<CustomInstruction> result = loader.loadForTarget(target);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().content()).contains("Local target instructions");
    }

    @Test
    void loadForTarget_shouldReturnEmptyForGitHubTarget() {
        ReviewTarget target = ReviewTarget.gitHub("owner/repo");

        List<CustomInstruction> result = loader.loadForTarget(target);

        assertThat(result).isEmpty();
    }

    // === CustomInstruction Record Tests ===

    @Test
    void customInstruction_isEmpty_shouldReturnTrueForNullContent() {
        CustomInstruction instruction = new CustomInstruction(
            "path", null, InstructionSource.LOCAL_FILE, null, null);
        assertThat(instruction.isEmpty()).isTrue();
    }

    @Test
    void customInstruction_isEmpty_shouldReturnTrueForBlankContent() {
        CustomInstruction instruction = new CustomInstruction(
            "path", "   ", InstructionSource.LOCAL_FILE, null, null);
        assertThat(instruction.isEmpty()).isTrue();
    }

    @Test
    void customInstruction_isEmpty_shouldReturnFalseForValidContent() {
        CustomInstruction instruction = new CustomInstruction(
            "path", "content", InstructionSource.LOCAL_FILE, null, null);
        assertThat(instruction.isEmpty()).isFalse();
    }

    // === toPromptSection Tests ===

    @Test
    void toPromptSection_shouldFormatCorrectly() {
        CustomInstruction instruction = new CustomInstruction(
            "path", "Do this thing", InstructionSource.LOCAL_FILE, null, null);

        String formatted = instruction.toPromptSection();

        assertThat(formatted).contains("カスタムインストラクション");
        assertThat(formatted).contains("Do this thing");
    }

    // === Frontmatter Parsing Tests ===

    @Nested
    @DisplayName("parseFrontmatter")
    class ParseFrontmatter {

        @Test
        @DisplayName("フロントマターなしの場合はそのまま返す")
        void noFrontmatterReturnsRawContent() {
            String raw = "Just plain content\nwith multiple lines";
            var parsed = CustomInstructionLoader.parseFrontmatter(raw);

            assertThat(parsed.content()).isEqualTo(raw);
            assertThat(parsed.applyTo()).isNull();
            assertThat(parsed.description()).isNull();
        }

        @Test
        @DisplayName("applyToのみのフロントマターを解析")
        void parsesApplyToOnly() {
            String raw = """
                ---
                applyTo: '**/*.java'
                ---
                Follow Java coding standards.""";
            var parsed = CustomInstructionLoader.parseFrontmatter(raw.stripIndent());

            assertThat(parsed.content()).isEqualTo("Follow Java coding standards.");
            assertThat(parsed.applyTo()).isEqualTo("**/*.java");
            assertThat(parsed.description()).isNull();
        }

        @Test
        @DisplayName("descriptionのみのフロントマターを解析")
        void parsesDescriptionOnly() {
            String raw = """
                ---
                description: 'Java standards'
                ---
                Follow Java coding standards.""";
            var parsed = CustomInstructionLoader.parseFrontmatter(raw.stripIndent());

            assertThat(parsed.content()).isEqualTo("Follow Java coding standards.");
            assertThat(parsed.applyTo()).isNull();
            assertThat(parsed.description()).isEqualTo("Java standards");
        }

        @Test
        @DisplayName("applyToとdescription両方のフロントマターを解析")
        void parsesBothFields() {
            String raw = """
                ---
                applyTo: '**/*.java'
                description: 'Java coding standards'
                ---
                Follow these rules for Java files.""";
            var parsed = CustomInstructionLoader.parseFrontmatter(raw.stripIndent());

            assertThat(parsed.content()).isEqualTo("Follow these rules for Java files.");
            assertThat(parsed.applyTo()).isEqualTo("**/*.java");
            assertThat(parsed.description()).isEqualTo("Java coding standards");
        }

        @Test
        @DisplayName("ダブルクォートの値を解析")
        void parsesDoubleQuotedValues() {
            String raw = """
                ---
                applyTo: "src/**/*.ts"
                description: "TypeScript rules"
                ---
                Use strict mode.""";
            var parsed = CustomInstructionLoader.parseFrontmatter(raw.stripIndent());

            assertThat(parsed.applyTo()).isEqualTo("src/**/*.ts");
            assertThat(parsed.description()).isEqualTo("TypeScript rules");
        }

        @Test
        @DisplayName("クォートなしの値を解析")
        void parsesUnquotedValues() {
            String raw = """
                ---
                applyTo: **/*.py
                ---
                Python rules here.""";
            var parsed = CustomInstructionLoader.parseFrontmatter(raw.stripIndent());

            assertThat(parsed.applyTo()).isEqualTo("**/*.py");
        }

        @Test
        @DisplayName("閉じ区切りがない場合はフロントマターとして扱わない")
        void noClosingDelimiterTreatsAsContent() {
            String raw = """
                ---
                applyTo: '**/*.java'
                Some content without closing delimiter.""";
            var parsed = CustomInstructionLoader.parseFrontmatter(raw.stripIndent());

            assertThat(parsed.content()).isEqualTo(raw.stripIndent());
            assertThat(parsed.applyTo()).isNull();
        }

        @Test
        @DisplayName("nullの場合はnullコンテンツを返す")
        void nullInputReturnsNull() {
            var parsed = CustomInstructionLoader.parseFrontmatter(null);

            assertThat(parsed.content()).isNull();
            assertThat(parsed.applyTo()).isNull();
            assertThat(parsed.description()).isNull();
        }

        @Test
        @DisplayName("applyToが**の場合は全ファイル対象")
        void wildcardApplyTo() {
            String raw = """
                ---
                applyTo: '**'
                ---
                Global instructions.""";
            var parsed = CustomInstructionLoader.parseFrontmatter(raw.stripIndent());

            assertThat(parsed.applyTo()).isEqualTo("**");
            assertThat(parsed.content()).isEqualTo("Global instructions.");
        }

        @Test
        @DisplayName("空のフロントマター値はnullとして扱う")
        void emptyFrontmatterValuesAreNull() {
            String raw = """
                ---
                applyTo:
                description:
                ---
                Content here.""";
            var parsed = CustomInstructionLoader.parseFrontmatter(raw.stripIndent());

            assertThat(parsed.applyTo()).isNull();
            assertThat(parsed.description()).isNull();
            assertThat(parsed.content()).isEqualTo("Content here.");
        }

        @Test
        @DisplayName("複数行のコンテンツをフロントマター後に保持")
        void preservesMultilineContent() {
            String raw = """
                ---
                applyTo: '**/*.java'
                ---
                Rule 1: Use meaningful names.
                Rule 2: Add comments.
                Rule 3: Follow standards.""";
            var parsed = CustomInstructionLoader.parseFrontmatter(raw.stripIndent());

            assertThat(parsed.content()).contains("Rule 1:");
            assertThat(parsed.content()).contains("Rule 2:");
            assertThat(parsed.content()).contains("Rule 3:");
        }
    }

    // === Instructions Directory Tests ===

    @Nested
    @DisplayName("loadFromInstructionsDirectory")
    class LoadFromInstructionsDirectory {

        @Test
        @DisplayName("ディレクトリが存在しない場合は空リストを返す")
        void noDirectoryReturnsEmptyList() {
            List<CustomInstruction> result = loader.loadFromInstructionsDirectory(tempDir);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName(".instructions.mdファイルを読み込む")
        void loadsInstructionFiles() throws IOException {
            Path instructionsDir = tempDir.resolve(".github/instructions");
            Files.createDirectories(instructionsDir);
            Files.writeString(instructionsDir.resolve("java.instructions.md"), """
                ---
                applyTo: '**/*.java'
                description: 'Java coding standards'
                ---
                Follow Java standards.""");

            List<CustomInstruction> result = loader.loadFromInstructionsDirectory(tempDir);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().content()).isEqualTo("Follow Java standards.");
            assertThat(result.getFirst().applyTo()).isEqualTo("**/*.java");
            assertThat(result.getFirst().description()).isEqualTo("Java coding standards");
            assertThat(result.getFirst().source()).isEqualTo(InstructionSource.LOCAL_FILE);
        }

        @Test
        @DisplayName("複数の.instructions.mdファイルを読み込む")
        void loadsMultipleFiles() throws IOException {
            Path instructionsDir = tempDir.resolve(".github/instructions");
            Files.createDirectories(instructionsDir);
            Files.writeString(instructionsDir.resolve("java.instructions.md"), """
                ---
                applyTo: '**/*.java'
                ---
                Java rules.""");
            Files.writeString(instructionsDir.resolve("typescript.instructions.md"), """
                ---
                applyTo: '**/*.ts'
                ---
                TypeScript rules.""");

            List<CustomInstruction> result = loader.loadFromInstructionsDirectory(tempDir);

            assertThat(result).hasSize(2);
            // Sorted alphabetically by path
            assertThat(result.get(0).applyTo()).isEqualTo("**/*.java");
            assertThat(result.get(1).applyTo()).isEqualTo("**/*.ts");
        }

        @Test
        @DisplayName("拡張子が.instructions.md以外のファイルは無視する")
        void ignoresNonInstructionFiles() throws IOException {
            Path instructionsDir = tempDir.resolve(".github/instructions");
            Files.createDirectories(instructionsDir);
            Files.writeString(instructionsDir.resolve("java.instructions.md"), """
                ---
                applyTo: '**/*.java'
                ---
                Java rules.""");
            Files.writeString(instructionsDir.resolve("README.md"), "Not an instruction file");
            Files.writeString(instructionsDir.resolve("notes.txt"), "Not an instruction file");

            List<CustomInstruction> result = loader.loadFromInstructionsDirectory(tempDir);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().applyTo()).isEqualTo("**/*.java");
        }

        @Test
        @DisplayName("空の.instructions.mdファイルはスキップする")
        void skipsEmptyFiles() throws IOException {
            Path instructionsDir = tempDir.resolve(".github/instructions");
            Files.createDirectories(instructionsDir);
            Files.writeString(instructionsDir.resolve("empty.instructions.md"), "   ");
            Files.writeString(instructionsDir.resolve("valid.instructions.md"), """
                ---
                applyTo: '**/*.java'
                ---
                Valid content.""");

            List<CustomInstruction> result = loader.loadFromInstructionsDirectory(tempDir);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().content()).isEqualTo("Valid content.");
        }

        @Test
        @DisplayName("フロントマターなしの.instructions.mdファイルも読み込む")
        void loadsFilesWithoutFrontmatter() throws IOException {
            Path instructionsDir = tempDir.resolve(".github/instructions");
            Files.createDirectories(instructionsDir);
            Files.writeString(instructionsDir.resolve("general.instructions.md"), 
                "General instructions without frontmatter");

            List<CustomInstruction> result = loader.loadFromInstructionsDirectory(tempDir);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().content()).isEqualTo("General instructions without frontmatter");
            assertThat(result.getFirst().applyTo()).isNull();
            assertThat(result.getFirst().description()).isNull();
        }
    }

    // === Integration: Load Directory with Instructions Directory ===

    @Nested
    @DisplayName("loadFromLocalDirectory with .github/instructions/")
    class LoadFromLocalDirectoryWithInstructionsDir {

        @Test
        @DisplayName("copilot-instructions.mdと.github/instructions/の両方を返す")
        void returnsBothCopilotAndScopedInstructions() throws IOException {
            Path githubDir = tempDir.resolve(".github");
            Files.createDirectories(githubDir);
            Files.writeString(githubDir.resolve("copilot-instructions.md"), "Global instructions");

            Path instructionsDir = githubDir.resolve("instructions");
            Files.createDirectories(instructionsDir);
            Files.writeString(instructionsDir.resolve("java.instructions.md"), """
                ---
                applyTo: '**/*.java'
                description: 'Java rules'
                ---
                Java specific rules.""");

            List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

            assertThat(result).hasSize(2);
            // First: copilot-instructions.md (global)
            assertThat(result.get(0).content()).isEqualTo("Global instructions");
            assertThat(result.get(0).source()).isEqualTo(InstructionSource.LOCAL_FILE);
            assertThat(result.get(0).applyTo()).isNull();
            // Second: scoped instruction
            assertThat(result.get(1).content()).isEqualTo("Java specific rules.");
            assertThat(result.get(1).applyTo()).isEqualTo("**/*.java");
            assertThat(result.get(1).description()).isEqualTo("Java rules");
        }

        @Test
        @DisplayName(".github/instructions/のみの場合も読み込む")
        void loadsScopedInstructionsOnly() throws IOException {
            Path instructionsDir = tempDir.resolve(".github/instructions");
            Files.createDirectories(instructionsDir);
            Files.writeString(instructionsDir.resolve("python.instructions.md"), """
                ---
                applyTo: '**/*.py'
                ---
                Python specific rules.""");

            List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().content()).isEqualTo("Python specific rules.");
            assertThat(result.getFirst().applyTo()).isEqualTo("**/*.py");
            assertThat(result.getFirst().source()).isEqualTo(InstructionSource.LOCAL_FILE);
        }
    }
}
