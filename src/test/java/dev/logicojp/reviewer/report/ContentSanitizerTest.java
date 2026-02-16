package dev.logicojp.reviewer.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContentSanitizer")
class ContentSanitizerTest {

    @Nested
    @DisplayName("CoTブロック除去")
    class ThinkingBlockRemoval {

        @Test
        @DisplayName("thinkingブロックを除去する")
        void removesThinkingBlock() {
            String input = "Before <thinking>This is internal reasoning</thinking> After";
            assertThat(ContentSanitizer.sanitize(input)).isEqualTo("Before  After");
        }

        @Test
        @DisplayName("antThinkingブロックを除去する")
        void removesAntThinkingBlock() {
            String input = "Start <antThinking>Deep thought here</antThinking> End";
            assertThat(ContentSanitizer.sanitize(input)).isEqualTo("Start  End");
        }

        @Test
        @DisplayName("reflectionブロックを除去する")
        void removesReflectionBlock() {
            String input = "A <reflection>Let me reconsider</reflection> B";
            assertThat(ContentSanitizer.sanitize(input)).isEqualTo("A  B");
        }

        @Test
        @DisplayName("複数行のthinkingブロックを除去する")
        void removesMultilineThinkingBlock() {
            String input = """
                Review result:
                <thinking>
                Let me think about this...
                Multiple lines of reasoning.
                </thinking>
                ### Finding 1""";
            String result = ContentSanitizer.sanitize(input);
            assertThat(result).doesNotContain("<thinking>");
            assertThat(result).contains("### Finding 1");
        }

        @Test
        @DisplayName("大文字小文字を区別しない")
        void caseInsensitive() {
            String input = "A <THINKING>LOUD THOUGHTS</THINKING> B";
            assertThat(ContentSanitizer.sanitize(input)).isEqualTo("A  B");
        }
    }

    @Nested
    @DisplayName("detailsブロック除去")
    class DetailsRemoval {

        @Test
        @DisplayName("Thinkingサマリーのdetailsブロックを除去する")
        void removesThinkingDetails() {
            String input = """
                Before
                <details><summary>Thinking</summary>Hidden reasoning</details>
                After""";
            String result = ContentSanitizer.sanitize(input);
            assertThat(result).doesNotContain("<details>");
            assertThat(result).contains("Before");
            assertThat(result).contains("After");
        }

        @Test
        @DisplayName("思考サマリーのdetailsブロックを除去する")
        void removesJapaneseThinkingDetails() {
            String input = """
                前
                <details><summary>思考プロセス</summary>推論内容</details>
                後""";
            String result = ContentSanitizer.sanitize(input);
            assertThat(result).doesNotContain("<details>");
        }
    }

    @Nested
    @DisplayName("空白行の正規化")
    class BlankLineNormalization {

        @Test
        @DisplayName("3行以上の連続空行を2行に圧縮する")
        void collapsesExcessiveBlankLines() {
            String input = "A\n\n\n\n\nB";
            assertThat(ContentSanitizer.sanitize(input)).isEqualTo("A\n\nB");
        }

        @Test
        @DisplayName("2行の空行はそのまま保持する")
        void preservesDoubleBlankLines() {
            String input = "A\n\nB";
            assertThat(ContentSanitizer.sanitize(input)).isEqualTo("A\n\nB");
        }
    }

    @Nested
    @DisplayName("エッジケース")
    class EdgeCases {

        @Test
        @DisplayName("nullを入力するとnullを返す")
        void returnsNullForNull() {
            assertThat(ContentSanitizer.sanitize(null)).isNull();
        }

        @Test
        @DisplayName("空文字を入力すると空文字を返す")
        void returnsEmptyForEmpty() {
            assertThat(ContentSanitizer.sanitize("")).isEmpty();
        }

        @Test
        @DisplayName("サニタイズ不要のコンテンツはそのまま返す")
        void preservesCleanContent() {
            String input = "### Finding 1\n\n| Priority | High |";
            assertThat(ContentSanitizer.sanitize(input)).isEqualTo(input);
        }

        @Test
        @DisplayName("差し替え戦略を利用してsanitizeできる")
        void usesInjectedSanitizationStrategy() {
            var strategyCalled = new AtomicBoolean(false);

            String result = ContentSanitizer.sanitize("input", content -> {
                strategyCalled.set(true);
                return "  INJECTED  ";
            });

            assertThat(strategyCalled).isTrue();
            assertThat(result).isEqualTo("INJECTED");
        }
    }
}
