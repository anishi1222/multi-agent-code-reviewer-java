package dev.logicojp.reviewer.agent;

import java.util.function.Consumer;

/// Binds session events to a {@link ContentCollector} in a transport-agnostic way.
final class ReviewSessionEvents {

    @FunctionalInterface
    interface SessionSubscription {
        AutoCloseable subscribe(Consumer<EventData> handler);
    }

    @FunctionalInterface
    interface TypedSessionSubscription<T> {
        AutoCloseable subscribe(Consumer<T> handler);
    }

    record EventData(String type, String content, int toolCalls, String errorMessage) {
    }

    private ReviewSessionEvents() {
    }

    static EventSubscriptions register(String agentName,
                                       ContentCollector collector,
                                       SessionSubscription allEvents,
                                       TypedSessionSubscription<EventData> messages,
                                       TypedSessionSubscription<EventData> idle,
                                       TypedSessionSubscription<EventData> error,
                                       java.util.function.Consumer<String> traceLogger) {
        var allEventsSub = subscribeAllEvents(agentName, collector, allEvents, traceLogger);
        var messageSub = subscribeMessages(collector, messages);
        var idleSub = subscribeIdle(collector, idle);
        var errorSub = subscribeError(collector, error);

        return new EventSubscriptions(allEventsSub, messageSub, idleSub, errorSub);
    }

    private static AutoCloseable subscribeAllEvents(String agentName,
                                                    ContentCollector collector,
                                                    SessionSubscription allEvents,
                                                    java.util.function.Consumer<String> traceLogger) {
        return allEvents.subscribe(event -> {
            collector.onActivity();
            traceLogger.accept("Agent " + agentName + ": event received — " + event.type());
        });
    }

    private static AutoCloseable subscribeMessages(ContentCollector collector,
                                                   TypedSessionSubscription<EventData> messages) {
        return messages.subscribe(event -> collector.onMessage(event.content(), Math.max(0, event.toolCalls())));
    }

    private static AutoCloseable subscribeIdle(ContentCollector collector,
                                               TypedSessionSubscription<EventData> idle) {
        return idle.subscribe(_ -> collector.onIdle());
    }

    private static AutoCloseable subscribeError(ContentCollector collector,
                                                TypedSessionSubscription<EventData> error) {
        return error.subscribe(event -> collector.onError(errorMessageOrDefault(event)));
    }

    private static String errorMessageOrDefault(EventData event) {
        return event.errorMessage() != null ? event.errorMessage() : "session error";
    }
}
