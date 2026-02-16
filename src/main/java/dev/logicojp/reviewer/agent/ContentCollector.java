package dev.logicojp.reviewer.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/// Collects content from Copilot session events.
/// Tracks both the last event content (preferred) and accumulated content (fallback).
/// Accumulation is capped at {@code MAX_ACCUMULATED_SIZE} to prevent OOM.
class ContentCollector {

    private static final Logger logger = LoggerFactory.getLogger(ContentCollector.class);
    private static final int MAX_ACCUMULATED_SIZE = 4 * 1024 * 1024; // 4MB

    private final CompletableFuture<String> future = new CompletableFuture<>();
    private final StringBuilder accumulatedBuilder = new StringBuilder(8192);
    private final Object accumulatedLock = new Object();
    private final AtomicInteger accumulatedSize = new AtomicInteger(0);
    private final AtomicReference<String> lastContent = new AtomicReference<>(null);
    private final AtomicLong lastActivityTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger toolCallCount = new AtomicInteger(0);
    private final AtomicInteger messageCount = new AtomicInteger(0);
    private final String agentName;

    private volatile String joinedCache;
    private volatile int joinedCacheSize;

    ContentCollector(String agentName) {
        this.agentName = agentName;
    }

    void onActivity() {
        lastActivityTime.set(System.currentTimeMillis());
    }

    void onMessage(String content, int toolCalls) {
        messageCount.incrementAndGet();
        appendMessageContent(content);
        if (toolCalls > 0) {
            toolCallCount.addAndGet(toolCalls);
        }
    }

    void onIdle() {
        if (!future.isDone()) {
            completeFromLatestContent();
        }
    }

    private void appendMessageContent(String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        lastContent.set(content);
        synchronized (accumulatedLock) {
            int nextSize = accumulatedSize.get() + content.length();
            if (nextSize <= MAX_ACCUMULATED_SIZE) {
                accumulatedBuilder.append(content);
                accumulatedSize.set(nextSize);
                invalidateJoinedCache();
            }
        }
    }

    private void completeFromLatestContent() {
        String last = lastContent.get();
        if (last != null && !last.isBlank()) {
            future.complete(last);
            return;
        }
        String accumulated = joinAccumulated();
        future.complete(accumulated.isBlank() ? null : accumulated);
    }

    private void invalidateJoinedCache() {
        joinedCache = null;
        joinedCacheSize = 0;
    }

    void onError(String message) {
        if (!future.isDone()) {
            future.completeExceptionally(new RuntimeException("Session error: " + message));
        }
    }

    void onIdleTimeout(long elapsed, long idleTimeoutMs) {
        if (future.isDone()) return;
        logger.warn("Agent {}: idle timeout — no events for {} ms ({} messages, {} tool calls)",
            agentName, elapsed, messageCount.get(), toolCallCount.get());
        String content = joinAccumulated();
        if (!content.isBlank()) {
            future.complete(content);
        } else {
            future.completeExceptionally(new TimeoutException(
                "No activity for " + elapsed + "ms (idle timeout: " + idleTimeoutMs + "ms)"));
        }
    }

    long getElapsedSinceLastActivity() {
        return System.currentTimeMillis() - lastActivityTime.get();
    }

    String getAccumulatedContent() {
        return joinAccumulated();
    }

    private String joinAccumulated() {
        int currentSize = accumulatedSize.get();
        if (currentSize == 0) {
            return "";
        }
        String cached = joinedCache;
        if (cached != null && joinedCacheSize == currentSize) {
            return cached;
        }
        synchronized (accumulatedLock) {
            currentSize = accumulatedSize.get();
            if (currentSize == 0) {
                joinedCache = "";
                joinedCacheSize = 0;
                return "";
            }
            String result = accumulatedBuilder.toString();
            joinedCache = result;
            joinedCacheSize = currentSize;
            return result;
        }
    }

    String awaitResult(long maxTimeoutMs) throws Exception {
        String result = future.get(maxTimeoutMs, TimeUnit.MILLISECONDS);
        logger.info("Agent {}: completed ({} chars, {} messages, {} tool calls)",
            agentName,
            result != null ? result.length() : 0,
            messageCount.get(), toolCallCount.get());
        return result;
    }
}
