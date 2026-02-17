package dev.logicojp.reviewer.instruction;

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

/// Unit tests for PromptLoader.
class PromptLoaderTest {

    @TempDir
    Path tempDir;

    private PromptLoader loader;

    @BeforeEach
    void setUp() {
        loader = new PromptLoader();
    }

    // === Basic loading tests ===

    @Test
    @DisplayName("ディレクトリがnullの場合は空リストを返す")
    void returnsEmptyForNullDirectory() {
        List<CustomInstruction> result = loader.loadFromPromptsDirectory(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName(".github/promptsディレクトリが存在しない場合は空リストを返す")
    void returnsEmptyWhenPromptsDirectoryNotExists() {
        List<CustomInstruction> result = loader.loadFromPromptsDirectory(tempDir);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("空の.github/promptsディレクトリの場合は空リストを返す")
    void returnsEmptyForEmptyPromptsDirectory() throws IOException {
        Files.createDirectories(tempDir.resolve(".github/prompts"));
        List<CustomInstruction> result = loader.loadFromPromptsDirectory(tempDir);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName(".prompt.mdファイルを読み込む")
    void loadsPromptFile() throws IOException {
        Path promptsDir = tempDir.resolve(".github/prompts");
        Files.createDirectories(promptsDir);
        Files.writeString(promptsDir.resolve("java-junit.prompt.md"), """
            ---
            description: 'JUnit 5 best practices'
            agent: 'agent'
            ---
            # JUnit 5 Best Practices
            Write effective unit tests.""");

        List<CustomInstruction> result = loader.loadFromPromptsDirectory(tempDir);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().content()).contains("JUnit 5 Best Practices");
        assertThat(result.getFirst().content()).contains("Write effective unit tests.");
        assertThat(result.getFirst().description()).isEqualTo("JUnit 5 best practices");
        assertThat(result.getFirst().source()).isEqualTo(InstructionSource.LOCAL_FILE);
        assertThat(result.getFirst().applyTo()).isNull();
    }

    @Test
    @DisplayName("複数の.prompt.mdファイルを読み込む")
    void loadsMultiplePromptFiles() throws IOException {
        Path promptsDir = tempDir.resolve(".github/prompts");
        Files.createDirectories(promptsDir);
        Files.writeString(promptsDir.resolve("java-docs.prompt.md"), """
            ---
            description: 'Javadoc best practices'
            ---
            Document public members.""");
        Files.writeString(promptsDir.resolve("java-junit.prompt.md"), """
            ---
            description: 'JUnit best practices'
            ---
            Write unit tests.""");

        List<CustomInstruction> result = loader.loadFromPromptsDirectory(tempDir);

        assertThat(result).hasSize(2);
        // Sorted alphabetically by path
        assertThat(result.get(0).description()).isEqualTo("Javadoc best practices");
        assertThat(result.get(1).description()).isEqualTo("JUnit best practices");
    }

    @Test
    @DisplayName("拡張子が.prompt.md以外のファイルは無視する")
    void ignoresNonPromptFiles() throws IOException {
        Path promptsDir = tempDir.resolve(".github/prompts");
        Files.createDirectories(promptsDir);
        Files.writeString(promptsDir.resolve("java-junit.prompt.md"), """
            ---
            description: 'JUnit best practices'
            ---
            Write unit tests.""");
        Files.writeString(promptsDir.resolve("README.md"), "Not a prompt file");
        Files.writeString(promptsDir.resolve("notes.txt"), "Not a prompt file");
        Files.writeString(promptsDir.resolve("java.instructions.md"), "Not a prompt file");

        List<CustomInstruction> result = loader.loadFromPromptsDirectory(tempDir);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().description()).isEqualTo("JUnit best practices");
    }

    @Test
    @DisplayName("空の.prompt.mdファイルはスキップする")
    void skipsEmptyFiles() throws IOException {
        Path promptsDir = tempDir.resolve(".github/prompts");
        Files.createDirectories(promptsDir);
        Files.writeString(promptsDir.resolve("empty.prompt.md"), "   ");
        Files.writeString(promptsDir.resolve("valid.prompt.md"), """
            ---
            description: 'Valid prompt'
            ---
            Valid content.""");

        List<CustomInstruction> result = loader.loadFromPromptsDirectory(tempDir);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().content()).isEqualTo("Valid content.");
    }

    @Test
    @DisplayName("フロントマターなしの.prompt.mdファイルも読み込む")
    void loadsFilesWithoutFrontmatter() throws IOException {
        Path promptsDir = tempDir.resolve(".github/prompts");
        Files.createDirectories(promptsDir);
        Files.writeString(promptsDir.resolve("plain.prompt.md"),
            "Plain prompt content without frontmatter");

        List<CustomInstruction> result = loader.loadFromPromptsDirectory(tempDir);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().content()).isEqualTo("Plain prompt content without frontmatter");
        assertThat(result.getFirst().description()).isNull();
    }

    // === Frontmatter parsing tests ===

    @Nested
    @DisplayName("parseFrontmatter")
    class ParseFrontmatter {

        @Test
        @DisplayName("フロントマターなしの場合はそのまま返す")
        void noFrontmatterReturnsRawContent() {
            String raw = "Just plain content\nwith multiple lines";
            var parsed = PromptLoader.parseFrontmatter(raw);

            assertThat(parsed.content()).isEqualTo(raw);
            assertThat(parsed.description()).isNull();
            assertThat(parsed.agent()).isNull();
        }

        @Test
        @DisplayName("descriptionとagentを解析する")
        void parsesDescriptionAndAgent() {
            String raw = """
                ---
                description: 'JUnit 5 best practices'
                agent: 'agent'
                ---
                # JUnit 5
                Write tests.""";
            var parsed = PromptLoader.parseFrontmatter(raw.stripIndent());

            assertThat(parsed.content()).contains("# JUnit 5");
            assertThat(parsed.content()).contains("Write tests.");
            assertThat(parsed.description()).isEqualTo("JUnit 5 best practices");
            assertThat(parsed.agent()).isEqualTo("agent");
        }

        @Test
        @DisplayName("descriptionのみの場合")
        void parsesDescriptionOnly() {
            String raw = """
                ---
                description: 'Some best practices'
                ---
                Follow these rules.""";
            var parsed = PromptLoader.parseFrontmatter(raw.stripIndent());

            assertThat(parsed.content()).isEqualTo("Follow these rules.");
            assertThat(parsed.description()).isEqualTo("Some best practices");
            assertThat(parsed.agent()).isNull();
        }

        @Test
        @DisplayName("ダブルクォートの値を解析")
        void parsesDoubleQuotedValues() {
            String raw = """
                ---
                description: "Double quoted description"
                agent: "my-agent"
                ---
                Content here.""";
            var parsed = PromptLoader.parseFrontmatter(raw.stripIndent());

            assertThat(parsed.description()).isEqualTo("Double quoted description");
            assertThat(parsed.agent()).isEqualTo("my-agent");
        }

        @Test
        @DisplayName("クォートなしの値を解析")
        void parsesUnquotedValues() {
            String raw = """
                ---
                description: Unquoted description
                agent: my-agent
                ---
                Content here.""";
            var parsed = PromptLoader.parseFrontmatter(raw.stripIndent());

            assertThat(parsed.description()).isEqualTo("Unquoted description");
            assertThat(parsed.agent()).isEqualTo("my-agent");
        }

        @Test
        @DisplayName("閉じ区切りがない場合はフロントマターとして扱わない")
        void noClosingDelimiterTreatsAsContent() {
            String raw = """
                ---
                description: 'Some description'
                Content without closing delimiter.""";
            var parsed = PromptLoader.parseFrontmatter(raw.stripIndent());

            assertThat(parsed.content()).isEqualTo(raw.stripIndent());
            assertThat(parsed.description()).isNull();
        }

        @Test
        @DisplayName("nullの場合はnullコンテンツを返す")
        void nullInputReturnsNull() {
            var parsed = PromptLoader.parseFrontmatter(null);

            assertThat(parsed.content()).isNull();
            assertThat(parsed.description()).isNull();
            assertThat(parsed.agent()).isNull();
        }

        @Test
        @DisplayName("toolsフィールドを含むフロントマターを解析")
        void parsesWithToolsField() {
            String raw = """
                ---
                agent: 'agent'
                tools: ['changes', 'search/codebase']
                description: 'With tools'
                ---
                Content with tools.""";
            var parsed = PromptLoader.parseFrontmatter(raw.stripIndent());

            assertThat(parsed.description()).isEqualTo("With tools");
            assertThat(parsed.agent()).isEqualTo("agent");
            assertThat(parsed.content()).isEqualTo("Content with tools.");
        }

        @Test
        @DisplayName("空のフロントマター値はnullとして扱う")
        void emptyFrontmatterValuesAreNull() {
            String raw = """
                ---
                description:
                agent:
                ---
                Content here.""";
            var parsed = PromptLoader.parseFrontmatter(raw.stripIndent());

            assertThat(parsed.description()).isNull();
            assertThat(parsed.agent()).isNull();
            assertThat(parsed.content()).isEqualTo("Content here.");
        }
    }

    // === Integration with CustomInstructionLoader ===

    @Nested
    @DisplayName("CustomInstructionLoaderとの統合テスト")
    class IntegrationWithCustomInstructionLoader {

        @Test
        @DisplayName("instructionsとpromptsの両方を読み込む")
        void loadsBothInstructionsAndPrompts() throws IOException {
            // Create .github/instructions/ file
            Path instructionsDir = tempDir.resolve(".github/instructions");
            Files.createDirectories(instructionsDir);
            Files.writeString(instructionsDir.resolve("java.instructions.md"), """
                ---
                applyTo: '**/*.java'
                description: 'Java rules'
                ---
                Java coding standards.""");

            // Create .github/prompts/ file
            Path promptsDir = tempDir.resolve(".github/prompts");
            Files.createDirectories(promptsDir);
            Files.writeString(promptsDir.resolve("java-junit.prompt.md"), """
                ---
                description: 'JUnit best practices'
                ---
                Write unit tests with JUnit 5.""");

            CustomInstructionLoader instructionLoader = new CustomInstructionLoader(null, true, new PromptLoader(), new ScopedInstructionLoader());
            List<CustomInstruction> result = instructionLoader.loadFromLocalDirectory(tempDir);

            assertThat(result).hasSize(2);
            // Instructions come first, then prompts
            assertThat(result.get(0).applyTo()).isEqualTo("**/*.java");
            assertThat(result.get(0).description()).isEqualTo("Java rules");
            assertThat(result.get(1).description()).isEqualTo("JUnit best practices");
            assertThat(result.get(1).applyTo()).isNull();
        }

        @Test
        @DisplayName("loadPrompts=falseの場合はプロンプトを読み込まない")
        void doesNotLoadPromptsWhenDisabled() throws IOException {
            // Create .github/prompts/ file
            Path promptsDir = tempDir.resolve(".github/prompts");
            Files.createDirectories(promptsDir);
            Files.writeString(promptsDir.resolve("java-junit.prompt.md"), """
                ---
                description: 'JUnit best practices'
                ---
                Write unit tests with JUnit 5.""");

            // Create instruction file too
            Path instructionsDir = tempDir.resolve(".github/instructions");
            Files.createDirectories(instructionsDir);
            Files.writeString(instructionsDir.resolve("java.instructions.md"), """
                ---
                applyTo: '**/*.java'
                ---
                Java rules.""");

            CustomInstructionLoader instructionLoader = new CustomInstructionLoader(null, false, new PromptLoader(), new ScopedInstructionLoader());
            List<CustomInstruction> result = instructionLoader.loadFromLocalDirectory(tempDir);

            // Only instruction loaded, not the prompt
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().applyTo()).isEqualTo("**/*.java");
        }

        @Test
        @DisplayName("copilot-instructions.mdとinstructionsとprompts全てを読み込む")
        void loadsAllSourceTypes() throws IOException {
            // Create copilot-instructions.md
            Path githubDir = tempDir.resolve(".github");
            Files.createDirectories(githubDir);
            Files.writeString(githubDir.resolve("copilot-instructions.md"), "Global instructions");

            // Create .github/instructions/ file
            Path instructionsDir = githubDir.resolve("instructions");
            Files.createDirectories(instructionsDir);
            Files.writeString(instructionsDir.resolve("java.instructions.md"), """
                ---
                applyTo: '**/*.java'
                ---
                Java rules.""");

            // Create .github/prompts/ file
            Path promptsDir = githubDir.resolve("prompts");
            Files.createDirectories(promptsDir);
            Files.writeString(promptsDir.resolve("spring.prompt.md"), """
                ---
                description: 'Spring Boot best practices'
                ---
                Follow Spring Boot conventions.""");

            CustomInstructionLoader instructionLoader = new CustomInstructionLoader(null, true, new PromptLoader(), new ScopedInstructionLoader());
            List<CustomInstruction> result = instructionLoader.loadFromLocalDirectory(tempDir);

            assertThat(result).hasSize(3);
            // Order: copilot-instructions.md, .github/instructions/*, .github/prompts/*
            assertThat(result.get(0).content()).isEqualTo("Global instructions");
            assertThat(result.get(1).applyTo()).isEqualTo("**/*.java");
            assertThat(result.get(2).description()).isEqualTo("Spring Boot best practices");
        }
    }
}
