package dev.logicojp.reviewer.report.finding;

import dev.logicojp.reviewer.report.core.ReviewResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewFindingParser")
class ReviewFindingParserTest {

    @Test
    @DisplayName("見出し付き内容からFindingBlockを抽出する")
    void extractsFindingBlocksFromMarkdown() {
        String content = """
            ### 1. SQLインジェクション

            | 項目 | 内容 |
            |------|------|
            | **Priority** | High |
            | **指摘の概要** | 未パラメータ化クエリ |
            | **該当箇所** | src/A.java L10 |

            ### 2. N+1クエリ

            | 項目 | 内容 |
            |------|------|
            | **Priority** | Medium |
            | **指摘の概要** | ループ内クエリ |
            | **該当箇所** | src/B.java L20 |
            """;

        List<ReviewFindingParser.FindingBlock> blocks = ReviewFindingParser.extractFindingBlocks(content);

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).title()).isEqualTo("SQLインジェクション");
        assertThat(blocks.get(1).title()).isEqualTo("N+1クエリ");
    }

    @Test
    @DisplayName("findingKeyはタイトル・優先度・場所・概要を使って安定キーを生成する")
    void findingKeyBuildsDeterministicCompositeKey() {
        var block = new ReviewFindingParser.FindingBlock(
            "ReviewCommand クラスの責務過多",
            """
            | 項目 | 内容 |
            |------|------|
            | **Priority** | High |
            | **指摘の概要** | CLI解析と実行が混在 |
            | **該当箇所** | src/main/java/dev/logicojp/reviewer/cli/ReviewCommand.java |
            """
        );

        String key = ReviewFindingParser.findingKey(block);

        assertThat(key).contains("reviewcommand クラスの責務過多");
        assertThat(key).contains("high");
        assertThat(key).contains("reviewcommand.java");
        assertThat(key).contains("cli解析と実行が混在");
    }

    @Test
    @DisplayName("extractTableValueは存在しない行で空文字を返す")
    void extractTableValueReturnsEmptyWhenMissing() {
        String value = ReviewFindingParser.extractTableValue("| **Priority** | High |", "該当箇所");
        assertThat(value).isEmpty();
    }
}