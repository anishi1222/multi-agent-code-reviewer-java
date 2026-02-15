package dev.logicojp.reviewer.agent;

/// Holds all event subscriptions and provides bulk close.
record EventSubscriptions(
    AutoCloseable allEvents,
    AutoCloseable messages,
    AutoCloseable idle,
    AutoCloseable error
) {
    void closeAll() {
        for (AutoCloseable sub : new AutoCloseable[]{allEvents, messages, idle, error}) {
            try {
                sub.close();
            } catch (Exception _) {
            }
        }
    }
}
