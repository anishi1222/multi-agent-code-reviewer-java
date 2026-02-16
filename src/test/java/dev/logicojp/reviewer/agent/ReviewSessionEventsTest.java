package dev.logicojp.reviewer.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewSessionEvents")
class ReviewSessionEventsTest {

    @Test
    @DisplayName("イベント購読をcollectorへ正しく接続する")
    void registerWiresEventsToCollector() throws Exception {
        ContentCollector collector = new ContentCollector("agent");

        List<java.util.function.Consumer<ReviewSessionEvents.EventData>> allEventHandlers = new ArrayList<>();
        List<java.util.function.Consumer<ReviewSessionEvents.EventData>> messageHandlers = new ArrayList<>();
        List<java.util.function.Consumer<ReviewSessionEvents.EventData>> idleHandlers = new ArrayList<>();
        List<java.util.function.Consumer<ReviewSessionEvents.EventData>> errorHandlers = new ArrayList<>();

        AtomicBoolean closedAll = new AtomicBoolean(false);
        AtomicBoolean closedMsg = new AtomicBoolean(false);
        AtomicBoolean closedIdle = new AtomicBoolean(false);
        AtomicBoolean closedErr = new AtomicBoolean(false);

        EventSubscriptions subscriptions = ReviewSessionEvents.register(
            "agent",
            collector,
            handler -> {
                allEventHandlers.add(handler);
                return () -> closedAll.set(true);
            },
            handler -> {
                messageHandlers.add(handler);
                return () -> closedMsg.set(true);
            },
            handler -> {
                idleHandlers.add(handler);
                return () -> closedIdle.set(true);
            },
            handler -> {
                errorHandlers.add(handler);
                return () -> closedErr.set(true);
            },
            _ -> {
            }
        );

        allEventHandlers.getFirst().accept(new ReviewSessionEvents.EventData("evt", null, 0, null));
        messageHandlers.getFirst().accept(new ReviewSessionEvents.EventData("assistant", "hello", 2, null));
        idleHandlers.getFirst().accept(new ReviewSessionEvents.EventData("idle", null, 0, null));

        assertThat(collector.awaitResult(1000)).isEqualTo("hello");

        subscriptions.closeAll();
        assertThat(closedAll).isTrue();
        assertThat(closedMsg).isTrue();
        assertThat(closedIdle).isTrue();
        assertThat(closedErr).isTrue();
    }
}
