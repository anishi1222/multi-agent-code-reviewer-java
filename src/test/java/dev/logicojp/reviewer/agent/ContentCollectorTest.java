package dev.logicojp.reviewer.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContentCollector")
class ContentCollectorTest {

    @Test
    @DisplayName("メッセージを蓄積して取得できる")
    void accumulatesMessages() {
        ContentCollector collector = new ContentCollector("agent");
        collector.onMessage("part1", 0);
        collector.onMessage("part2", 1);

        assertThat(collector.getAccumulatedContent()).isEqualTo("part1part2");
    }

    @Test
    @DisplayName("onIdleで最後のコンテンツが優先される")
    void onIdlePrefersLastContent() throws Exception {
        ContentCollector collector = new ContentCollector("agent");
        collector.onMessage("first", 0);
        collector.onMessage("last", 0);

        collector.onIdle();

        assertThat(collector.awaitResult(1000)).isEqualTo("last");
    }

    @Test
    @DisplayName("キャッシュされた連結結果を再利用する")
    void joinedCacheIsReused() {
        ContentCollector collector = new ContentCollector("agent");
        collector.onMessage("abc", 0);

        String first = collector.getAccumulatedContent();
        String second = collector.getAccumulatedContent();

        assertThat(second).isSameAs(first);
    }
}
