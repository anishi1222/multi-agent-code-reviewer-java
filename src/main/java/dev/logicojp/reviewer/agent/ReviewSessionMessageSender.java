package dev.logicojp.reviewer.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeoutException;

/// Sends a prompt and collects session output with activity-based timeout control.
final class ReviewSessionMessageSender {

    @FunctionalInterface
    interface PromptSendAction {
        void send(String prompt) throws Exception;
    }

    @FunctionalInterface
    interface EventRegistrar {
        EventSubscriptions register(ContentCollector collector);
    }

    @FunctionalInterface
    interface IdleTaskScheduler {
        IdleTask schedule(ContentCollector collector);
    }

    @FunctionalInterface
    interface IdleTask {
        void cancel();
    }

    private static final Logger logger = LoggerFactory.getLogger(ReviewSessionMessageSender.class);

    private final String agentName;

    ReviewSessionMessageSender(String agentName) {
        this.agentName = agentName;
    }

    String sendWithActivityTimeout(String prompt,
                                   long maxTimeoutMs,
                                   PromptSendAction sendAction,
                                   EventRegistrar eventRegistrar,
                                   IdleTaskScheduler idleTaskScheduler) throws Exception {
        var collector = new ContentCollector(agentName);
        var subscriptions = eventRegistrar.register(collector);
        var idleTask = idleTaskScheduler.schedule(collector);
        try {
            sendAction.send(prompt);
            return collector.awaitResult(maxTimeoutMs);
        } catch (TimeoutException e) {
            return handleTimeout(collector, e);
        } finally {
            cleanup(idleTask, subscriptions);
        }
    }

    private String handleTimeout(ContentCollector collector, TimeoutException timeoutException) throws TimeoutException {
        String content = collector.getAccumulatedContent();
        if (!content.isBlank()) {
            logger.warn("Agent {}: max timeout reached, returning accumulated content ({} chars)",
                agentName, content.length());
            return content;
        }
        throw timeoutException;
    }

    private void cleanup(IdleTask idleTask, EventSubscriptions subscriptions) {
        idleTask.cancel();
        subscriptions.closeAll();
    }
}