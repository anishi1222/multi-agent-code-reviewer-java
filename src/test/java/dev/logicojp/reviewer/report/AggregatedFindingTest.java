package dev.logicojp.reviewer.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AggregatedFinding")
class AggregatedFindingTest {

    @Test
    @DisplayName("withPassは既存パスを保持したまま新規パスを追加する")
    void withPassAddsNewPassNumber() {
        var block = new ReviewFindingParser.FindingBlock(
            "SQLインジェクション",
            """
            | **Priority** | High |
            | **指摘の概要** | 未パラメータ化クエリ |
            | **該当箇所** | src/A.java L10 |
            """
        );
        var finding = AggregatedFinding.from(block, 1);

        var updated = finding.withPass(2);

        assertThat(updated.passNumbers()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("isNearDuplicateOfは類似したタイトルと概要を重複と判定する")
    void nearDuplicateCheckReturnsTrueForSimilarFinding() {
        var originalBlock = new ReviewFindingParser.FindingBlock(
            "ReviewCommand クラスの責務過多",
            """
            | **Priority** | High |
            | **指摘の概要** | CLI解析と実行が混在 |
            | **該当箇所** | src/main/java/dev/logicojp/reviewer/cli/ReviewCommand.java |
            """
        );
        var existing = AggregatedFinding.from(originalBlock, 1);

        String incomingTitle = ReviewFindingSimilarity.normalizeText("ReviewCommand の過剰な責務");
        String incomingPriority = ReviewFindingSimilarity.normalizeText("High");
        String incomingSummary = ReviewFindingSimilarity.normalizeText("CLI解析・実行・表示処理が1クラスに集約");
        String incomingLocation = ReviewFindingSimilarity.normalizeText(
            "src/main/java/dev/logicojp/reviewer/cli/ReviewCommand.java");

        boolean duplicated = existing.isNearDuplicateOf(
            incomingTitle,
            incomingPriority,
            incomingSummary,
            incomingLocation,
            ReviewFindingSimilarity.bigrams(incomingTitle),
            ReviewFindingSimilarity.bigrams(incomingSummary),
            ReviewFindingSimilarity.bigrams(incomingLocation)
        );

        assertThat(duplicated).isTrue();
    }
}