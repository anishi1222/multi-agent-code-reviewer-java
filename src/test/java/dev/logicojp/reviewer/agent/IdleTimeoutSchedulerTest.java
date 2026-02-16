package dev.logicojp.reviewer.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IdleTimeoutScheduler")
class IdleTimeoutSchedulerTest {

    @Test
    @DisplayName("チェック間隔は idleTimeout/4 と最小間隔の大きい方を使う")
    void computeCheckIntervalUsesMaxOfQuarterAndMinimum() {
        IdleTimeoutScheduler scheduler = IdleTimeoutScheduler.withMinInterval(100);

        assertThat(scheduler.computeCheckInterval(1000)).isEqualTo(250);
        assertThat(scheduler.computeCheckInterval(200)).isEqualTo(100);
    }

    @Test
    @DisplayName("アイドル状態が続くとcollectorをtimeout完了させる")
    void scheduleTriggersIdleTimeout() {
        var executor = Executors.newSingleThreadScheduledExecutor();
        try {
            ContentCollector collector = new ContentCollector("agent");
            IdleTimeoutScheduler scheduler = IdleTimeoutScheduler.withMinInterval(1);

            var task = scheduler.schedule(executor, collector, 20);

            assertThatThrownBy(() -> collector.awaitResult(TimeUnit.SECONDS.toMillis(2)))
                .hasMessageContaining("No activity");

            task.cancel(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdownNow();
        }
    }
}
