package dev.logicojp.reviewer.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReviewSessionMessageSender")
class ReviewSessionMessageSenderTest {

    @Test
    @DisplayName("送信結果を正常に収集して返す")
    void returnsCollectedContent() throws Exception {
        var sender = new ReviewSessionMessageSender("security", 4 * 1024 * 1024, 4096);
        var collectorRef = new AtomicReference<ContentCollector>();

        String result = sender.sendWithActivityTimeout(
            "PROMPT",
            300,
            _ -> {
                collectorRef.get().onMessage("OK", 0);
                collectorRef.get().onIdle();
            },
            collector -> {
                collectorRef.set(collector);
                return noOpSubscriptions();
            },
            _ -> () -> {
            }
        );

        assertThat(result).isEqualTo("OK");
    }

    @Test
    @DisplayName("最大タイムアウト時は蓄積コンテンツを返す")
    void returnsAccumulatedContentOnTimeout() throws Exception {
        var sender = new ReviewSessionMessageSender("security", 4 * 1024 * 1024, 4096);
        var collectorRef = new AtomicReference<ContentCollector>();

        String result = sender.sendWithActivityTimeout(
            "PROMPT",
            50,
            _ -> collectorRef.get().onMessage("PARTIAL", 0),
            collector -> {
                collectorRef.set(collector);
                return noOpSubscriptions();
            },
            _ -> () -> {
            }
        );

        assertThat(result).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("タイムアウトかつ蓄積なしならTimeoutExceptionを送出する")
    void throwsTimeoutWhenNoAccumulatedContent() {
        var sender = new ReviewSessionMessageSender("security", 4 * 1024 * 1024, 4096);
        var collectorRef = new AtomicReference<ContentCollector>();

        assertThatThrownBy(() -> sender.sendWithActivityTimeout(
            "PROMPT",
            50,
            _ -> {
            },
            collector -> {
                collectorRef.set(collector);
                return noOpSubscriptions();
            },
            _ -> () -> {
            }
        )).isInstanceOf(TimeoutException.class);
    }

    @Test
    @DisplayName("例外時でもidleTaskとsubscriptionをクリーンアップする")
    void cleansUpOnFailure() {
        var sender = new ReviewSessionMessageSender("security", 4 * 1024 * 1024, 4096);
        AtomicBoolean canceled = new AtomicBoolean(false);
        AtomicInteger closedCount = new AtomicInteger(0);

        assertThatThrownBy(() -> sender.sendWithActivityTimeout(
            "PROMPT",
            100,
            _ -> {
                throw new IllegalStateException("send failed");
            },
            _ -> closeCountingSubscriptions(closedCount),
            _ -> () -> canceled.set(true)
        )).isInstanceOf(IllegalStateException.class);

        assertThat(canceled).isTrue();
        assertThat(closedCount.get()).isEqualTo(4);
    }

    private EventSubscriptions noOpSubscriptions() {
        return new EventSubscriptions(() -> {
        }, () -> {
        }, () -> {
        }, () -> {
        });
    }

    private EventSubscriptions closeCountingSubscriptions(AtomicInteger closedCount) {
        return new EventSubscriptions(
            closedCount::incrementAndGet,
            closedCount::incrementAndGet,
            closedCount::incrementAndGet,
            closedCount::incrementAndGet
        );
    }
}