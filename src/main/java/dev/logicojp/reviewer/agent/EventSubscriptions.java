package dev.logicojp.reviewer.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Holds all event subscriptions and provides bulk close.
record EventSubscriptions(
    AutoCloseable allEvents,
    AutoCloseable messages,
    AutoCloseable idle,
    AutoCloseable error
) {
    private static final Logger logger = LoggerFactory.getLogger(EventSubscriptions.class);

    void closeAll() {
        for (AutoCloseable sub : new AutoCloseable[]{allEvents, messages, idle, error}) {
            try {
                sub.close();
            } catch (Exception e) {
                logger.debug("Failed to close event subscription: {}", e.getMessage());
            }
        }
    }
}
