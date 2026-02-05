package dev.logicojp.reviewer.instruction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomInstruction")
class CustomInstructionTest {

    @Nested
    @DisplayName("isEmpty")
    class IsEmpty {

        @Test
        @DisplayName("contentがnullの場合はtrueを返す")
        void nullContentReturnsTrue() {
            CustomInstruction instruction = new CustomInstruction(
                "/path/to/file.md", null, InstructionSource.LOCAL_FILE
            );
            
            assertThat(instruction.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("contentが空文字列の場合はtrueを返す")
        void emptyContentReturnsTrue() {
            CustomInstruction instruction = new CustomInstruction(
                "/path/to/file.md", "", InstructionSource.LOCAL_FILE
            );
            
            assertThat(instruction.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("contentが空白のみの場合はtrueを返す")
        void blankContentReturnsTrue() {
            CustomInstruction instruction = new CustomInstruction(
                "/path/to/file.md", "   \t\n  ", InstructionSource.LOCAL_FILE
            );
            
            assertThat(instruction.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("contentに内容がある場合はfalseを返す")
        void nonBlankContentReturnsFalse() {
            CustomInstruction instruction = new CustomInstruction(
                "/path/to/file.md", "Some instruction content", InstructionSource.LOCAL_FILE
            );
            
            assertThat(instruction.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("contentに空白を含む内容がある場合はfalseを返す")
        void contentWithWhitespaceReturnsFalse() {
            CustomInstruction instruction = new CustomInstruction(
                "/path/to/file.md", "  Content with spaces  ", InstructionSource.LOCAL_FILE
            );
            
            assertThat(instruction.isEmpty()).isFalse();
        }
    }

    @Nested
    @DisplayName("toPromptSection")
    class ToPromptSection {

        @Test
        @DisplayName("カスタムインストラクションヘッダーを含む")
        void includesHeader() {
            CustomInstruction instruction = new CustomInstruction(
                "/path/to/file.md", "Follow these rules", InstructionSource.LOCAL_FILE
            );
            
            String result = instruction.toPromptSection();
            
            assertThat(result).contains("## カスタムインストラクション");
        }

        @Test
        @DisplayName("指示文を含む")
        void includesInstructions() {
            CustomInstruction instruction = new CustomInstruction(
                "/path/to/file.md", "Custom content", InstructionSource.LOCAL_FILE
            );
            
            String result = instruction.toPromptSection();
            
            assertThat(result).contains("以下のプロジェクト固有の指示に従ってください");
        }

        @Test
        @DisplayName("コンテンツを含む")
        void includesContent() {
            String content = "Use TypeScript for all new code.";
            CustomInstruction instruction = new CustomInstruction(
                "/path/to/file.md", content, InstructionSource.LOCAL_FILE
            );
            
            String result = instruction.toPromptSection();
            
            assertThat(result).contains(content);
        }

        @Test
        @DisplayName("複数行のコンテンツを正しくフォーマットする")
        void formatsMultilineContent() {
            String content = """
                Rule 1: Use meaningful variable names.
                Rule 2: Add comments for complex logic.
                Rule 3: Follow coding standards.
                """;
            CustomInstruction instruction = new CustomInstruction(
                "/path/to/file.md", content.trim(), InstructionSource.LOCAL_FILE
            );
            
            String result = instruction.toPromptSection();
            
            assertThat(result).contains("Rule 1:");
            assertThat(result).contains("Rule 2:");
            assertThat(result).contains("Rule 3:");
        }
    }

    @Nested
    @DisplayName("レコードアクセサ")
    class RecordAccessors {

        @Test
        @DisplayName("sourcePathを取得できる")
        void canGetSourcePath() {
            CustomInstruction instruction = new CustomInstruction(
                "/my/path/instructions.md", "content", InstructionSource.GITHUB_REPOSITORY
            );
            
            assertThat(instruction.sourcePath()).isEqualTo("/my/path/instructions.md");
        }

        @Test
        @DisplayName("contentを取得できる")
        void canGetContent() {
            CustomInstruction instruction = new CustomInstruction(
                "/path", "My instruction content", InstructionSource.LOCAL_FILE
            );
            
            assertThat(instruction.content()).isEqualTo("My instruction content");
        }

        @Test
        @DisplayName("sourceを取得できる")
        void canGetSource() {
            CustomInstruction instruction = new CustomInstruction(
                "/path", "content", InstructionSource.MERGED
            );
            
            assertThat(instruction.source()).isEqualTo(InstructionSource.MERGED);
        }
    }

    @Nested
    @DisplayName("レコードの等価性")
    class RecordEquality {

        @Test
        @DisplayName("同じ値を持つレコードは等価である")
        void sameValuesAreEqual() {
            CustomInstruction inst1 = new CustomInstruction(
                "/path", "content", InstructionSource.LOCAL_FILE
            );
            CustomInstruction inst2 = new CustomInstruction(
                "/path", "content", InstructionSource.LOCAL_FILE
            );
            
            assertThat(inst1).isEqualTo(inst2);
            assertThat(inst1.hashCode()).isEqualTo(inst2.hashCode());
        }

        @Test
        @DisplayName("異なるsourcePathを持つレコードは等価でない")
        void differentPathsNotEqual() {
            CustomInstruction inst1 = new CustomInstruction(
                "/path1", "content", InstructionSource.LOCAL_FILE
            );
            CustomInstruction inst2 = new CustomInstruction(
                "/path2", "content", InstructionSource.LOCAL_FILE
            );
            
            assertThat(inst1).isNotEqualTo(inst2);
        }

        @Test
        @DisplayName("異なるcontentを持つレコードは等価でない")
        void differentContentNotEqual() {
            CustomInstruction inst1 = new CustomInstruction(
                "/path", "content1", InstructionSource.LOCAL_FILE
            );
            CustomInstruction inst2 = new CustomInstruction(
                "/path", "content2", InstructionSource.LOCAL_FILE
            );
            
            assertThat(inst1).isNotEqualTo(inst2);
        }

        @Test
        @DisplayName("異なるsourceを持つレコードは等価でない")
        void differentSourceNotEqual() {
            CustomInstruction inst1 = new CustomInstruction(
                "/path", "content", InstructionSource.LOCAL_FILE
            );
            CustomInstruction inst2 = new CustomInstruction(
                "/path", "content", InstructionSource.GITHUB_REPOSITORY
            );
            
            assertThat(inst1).isNotEqualTo(inst2);
        }
    }
}
