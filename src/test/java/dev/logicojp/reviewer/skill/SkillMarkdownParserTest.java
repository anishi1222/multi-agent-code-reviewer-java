package dev.logicojp.reviewer.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SkillMarkdownParser")
class SkillMarkdownParserTest {

    private final SkillMarkdownParser parser = new SkillMarkdownParser();

    @Nested
    @DisplayName("parseContent - 正常系（レガシー形式）")
    class ParseContentLegacySuccess {

        @Test
        @DisplayName("フロントマターとプロンプトを正しくパースする")
        void parsesFullSkillFile() {
            String content = """
                ---
                name: 技術スタック分析
                description: プロジェクトの技術スタックを分析します
                parameters:
                  - name: repository
                    required: true
                    description: 対象リポジトリ
                ---
                
                以下のリポジトリの技術スタックを分析してください。
                
                **対象リポジトリ**: ${repository}
                """;

            SkillDefinition skill = parser.parseContent(content, "tech-stack-analysis.skill.md");

            assertThat(skill.id()).isEqualTo("tech-stack-analysis");
            assertThat(skill.name()).isEqualTo("技術スタック分析");
            assertThat(skill.description()).isEqualTo("プロジェクトの技術スタックを分析します");
            assertThat(skill.prompt()).contains("技術スタックを分析してください");
            assertThat(skill.prompt()).contains("${repository}");
            assertThat(skill.parameters()).hasSize(1);

            SkillParameter param = skill.parameters().getFirst();
            assertThat(param.name()).isEqualTo("repository");
            assertThat(param.required()).isTrue();
            assertThat(param.description()).isEqualTo("対象リポジトリ");
        }

        @Test
        @DisplayName("複数パラメータを正しくパースする")
        void parsesMultipleParameters() {
            String content = """
                ---
                name: Test Skill
                description: A test
                parameters:
                  - name: target
                    required: true
                    description: Target file
                  - name: format
                    required: false
                    description: Output format
                    default: json
                ---
                
                Prompt content here.
                """;

            SkillDefinition skill = parser.parseContent(content, "test.skill.md");

            assertThat(skill.parameters()).hasSize(2);

            SkillParameter first = skill.parameters().get(0);
            assertThat(first.name()).isEqualTo("target");
            assertThat(first.required()).isTrue();

            SkillParameter second = skill.parameters().get(1);
            assertThat(second.name()).isEqualTo("format");
            assertThat(second.required()).isFalse();
            assertThat(second.defaultValue()).isEqualTo("json");
        }

        @Test
        @DisplayName("パラメータがない場合も正しくパースする")
        void parsesWithoutParameters() {
            String content = """
                ---
                name: Simple Skill
                description: No parameters
                ---
                
                Simple prompt content.
                """;

            SkillDefinition skill = parser.parseContent(content, "simple.skill.md");

            assertThat(skill.id()).isEqualTo("simple");
            assertThat(skill.name()).isEqualTo("Simple Skill");
            assertThat(skill.parameters()).isEmpty();
            assertThat(skill.prompt()).contains("Simple prompt content");
        }

        @Test
        @DisplayName("フロントマターなしの場合、全体をプロンプトとして扱う")
        void parsesWithoutFrontmatter() {
            String content = "This is just a prompt without frontmatter.";

            SkillDefinition skill = parser.parseContent(content, "raw.skill.md");

            assertThat(skill.id()).isEqualTo("raw");
            assertThat(skill.name()).isEqualTo("raw");
            assertThat(skill.prompt()).isEqualTo("This is just a prompt without frontmatter.");
        }

        @Test
        @DisplayName("クォートで囲まれた値を正しく処理する")
        void handlesQuotedValues() {
            String content = """
                ---
                name: "Quoted Name"
                description: 'Quoted Description'
                ---
                
                Prompt.
                """;

            SkillDefinition skill = parser.parseContent(content, "quoted.skill.md");

            assertThat(skill.name()).isEqualTo("Quoted Name");
            assertThat(skill.description()).isEqualTo("Quoted Description");
        }
    }

    @Nested
    @DisplayName("Agent Skills仕様 - SKILL.md形式")
    class AgentSkillsSpec {

        @Test
        @DisplayName("SKILL.mdファイルをディレクトリ名からIDを取得してパースする")
        void parsesSkillMdFromDirectory(@TempDir Path tempDir) throws IOException {
            Path skillDir = tempDir.resolve("code-review");
            Files.createDirectories(skillDir);

            String content = """
                ---
                name: code-review
                description: Performs comprehensive code review for quality and best practices.
                metadata:
                  agent: code-quality
                ---
                
                # Code Review
                
                Review the code for quality issues.
                """;

            Path skillFile = skillDir.resolve("SKILL.md");
            Files.writeString(skillFile, content);

            SkillDefinition skill = parser.parse(skillFile);

            assertThat(skill.id()).isEqualTo("code-review");
            assertThat(skill.name()).isEqualTo("code-review");
            assertThat(skill.description()).contains("comprehensive code review");
            assertThat(skill.prompt()).contains("# Code Review");
            assertThat(skill.metadata()).containsEntry("agent", "code-quality");
        }

        @Test
        @DisplayName("metadataブロックを正しくパースする")
        void parsesMetadataBlock() {
            String content = """
                ---
                name: my-skill
                description: A skill with metadata
                metadata:
                  agent: best-practices
                  version: "1.0"
                ---
                
                Skill instructions.
                """;

            SkillDefinition skill = parser.parseContent(content, "my-skill", true);

            assertThat(skill.id()).isEqualTo("my-skill");
            assertThat(skill.metadata()).containsEntry("agent", "best-practices");
            assertThat(skill.metadata()).containsEntry("version", "1.0");
        }

        @Test
        @DisplayName("metadataがない場合は空のマップを返す")
        void emptyMetadataWhenNotPresent() {
            String content = """
                ---
                name: simple
                description: No metadata
                ---
                
                Instructions.
                """;

            SkillDefinition skill = parser.parseContent(content, "simple", true);

            assertThat(skill.metadata()).isEmpty();
        }

        @Test
        @DisplayName("Agent Skills仕様ではIDがディレクトリ名と一致する")
        void idMatchesDirectoryName() {
            String content = """
                ---
                name: tech-stack-analysis
                description: Analyze technology stack
                ---
                
                Analyze the tech stack.
                """;

            SkillDefinition skill = parser.parseContent(content, "tech-stack-analysis", true);

            assertThat(skill.id()).isEqualTo("tech-stack-analysis");
        }
    }

    @Nested
    @DisplayName("parseContent - 異常系")
    class ParseContentErrors {

        @Test
        @DisplayName("プロンプトが空の場合は例外を投げる")
        void throwsWhenPromptEmpty() {
            String content = """
                ---
                name: Empty Prompt
                description: Has no body
                ---
                
                """;

            assertThatThrownBy(() -> parser.parseContent(content, "empty.skill.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no prompt content");
        }
    }

    @Nested
    @DisplayName("parse - ファイルからの読み込み")
    class ParseFile {

        @Test
        @DisplayName("レガシー .skill.md ファイルからスキルを正しく読み込む")
        void parsesFromLegacyFile(@TempDir Path tempDir) throws IOException {
            String content = """
                ---
                name: File Skill
                description: Loaded from file
                parameters:
                  - name: repo
                    required: true
                    description: Repository
                ---
                
                Analyze ${repo} for issues.
                """;

            Path skillFile = tempDir.resolve("file-test.skill.md");
            Files.writeString(skillFile, content);

            SkillDefinition skill = parser.parse(skillFile);

            assertThat(skill.id()).isEqualTo("file-test");
            assertThat(skill.name()).isEqualTo("File Skill");
            assertThat(skill.parameters()).hasSize(1);
            assertThat(skill.prompt()).contains("${repo}");
        }

        @Test
        @DisplayName("SKILL.md ファイルからスキルを正しく読み込む")
        void parsesFromSkillMdFile(@TempDir Path tempDir) throws IOException {
            Path skillDir = tempDir.resolve("my-skill");
            Files.createDirectories(skillDir);

            String content = """
                ---
                name: my-skill
                description: A test skill following Agent Skills spec
                ---
                
                # My Skill
                
                Do something useful.
                """;

            Path skillFile = skillDir.resolve("SKILL.md");
            Files.writeString(skillFile, content);

            SkillDefinition skill = parser.parse(skillFile);

            assertThat(skill.id()).isEqualTo("my-skill");
            assertThat(skill.name()).isEqualTo("my-skill");
            assertThat(skill.description()).contains("Agent Skills spec");
            assertThat(skill.prompt()).contains("# My Skill");
        }
    }

    @Nested
    @DisplayName("discoverSkills - スキルディレクトリの探索")
    class DiscoverSkills {

        @Test
        @DisplayName("SKILL.mdを含むディレクトリを発見する")
        void discoversSkillDirectories(@TempDir Path tempDir) throws IOException {
            // Create two valid skill directories
            Path skill1Dir = tempDir.resolve("skill-a");
            Files.createDirectories(skill1Dir);
            Files.writeString(skill1Dir.resolve("SKILL.md"), """
                ---
                name: skill-a
                description: First skill
                ---
                
                Instructions for skill A.
                """);

            Path skill2Dir = tempDir.resolve("skill-b");
            Files.createDirectories(skill2Dir);
            Files.writeString(skill2Dir.resolve("SKILL.md"), """
                ---
                name: skill-b
                description: Second skill
                ---
                
                Instructions for skill B.
                """);

            // Create a directory without SKILL.md (should be ignored)
            Path noSkillDir = tempDir.resolve("not-a-skill");
            Files.createDirectories(noSkillDir);
            Files.writeString(noSkillDir.resolve("README.md"), "Not a skill.");

            List<Path> discovered = parser.discoverSkills(tempDir);

            assertThat(discovered).hasSize(2);
            assertThat(discovered).allMatch(p -> p.getFileName().toString().equals("SKILL.md"));
        }

        @Test
        @DisplayName("存在しないディレクトリの場合は空リストを返す")
        void returnsEmptyForNonExistentDir(@TempDir Path tempDir) {
            Path noSuchDir = tempDir.resolve("nonexistent");
            List<Path> discovered = parser.discoverSkills(noSuchDir);
            assertThat(discovered).isEmpty();
        }

        @Test
        @DisplayName("nullの場合は空リストを返す")
        void returnsEmptyForNull() {
            List<Path> discovered = parser.discoverSkills(null);
            assertThat(discovered).isEmpty();
        }
    }

    @Nested
    @DisplayName("isSkillFile")
    class IsSkillFile {

        @Test
        @DisplayName(".skill.mdファイルはスキルファイルとして認識される")
        void recognizesLegacySkillFile(@TempDir Path tempDir) {
            Path skillFile = tempDir.resolve("my-skill.skill.md");
            assertThat(parser.isSkillFile(skillFile)).isTrue();
        }

        @Test
        @DisplayName("SKILL.mdファイルはスキルファイルとして認識される")
        void recognizesAgentSkillsSpecFile(@TempDir Path tempDir) {
            Path skillFile = tempDir.resolve("SKILL.md");
            assertThat(parser.isSkillFile(skillFile)).isTrue();
        }

        @Test
        @DisplayName(".agent.mdファイルはスキルファイルとして認識されない")
        void doesNotRecognizeAgentFile(@TempDir Path tempDir) {
            Path agentFile = tempDir.resolve("my-agent.agent.md");
            assertThat(parser.isSkillFile(agentFile)).isFalse();
        }

        @Test
        @DisplayName("通常の.mdファイルはスキルファイルとして認識されない")
        void doesNotRecognizePlainMd(@TempDir Path tempDir) {
            Path mdFile = tempDir.resolve("readme.md");
            assertThat(parser.isSkillFile(mdFile)).isFalse();
        }

        @Test
        @DisplayName("nullパスはfalseを返す")
        void nullPathReturnsFalse() {
            assertThat(parser.isSkillFile(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("extractIdFromFilename")
    class ExtractId {

        @Test
        @DisplayName(".skill.mdサフィックスを除去する")
        void removesSkillMdSuffix() {
            assertThat(parser.extractIdFromFilename("tech-stack-analysis.skill.md"))
                .isEqualTo("tech-stack-analysis");
        }

        @Test
        @DisplayName(".mdサフィックスを除去する")
        void removesMdSuffix() {
            assertThat(parser.extractIdFromFilename("simple.md"))
                .isEqualTo("simple");
        }

        @Test
        @DisplayName("サフィックスがない場合はそのまま返す")
        void returnsAsIsWithoutSuffix() {
            assertThat(parser.extractIdFromFilename("no-suffix"))
                .isEqualTo("no-suffix");
        }
    }
}
