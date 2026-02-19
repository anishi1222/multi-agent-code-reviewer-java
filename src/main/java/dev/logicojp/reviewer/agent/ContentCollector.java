package dev.logicojp.reviewer.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/// Collects content from Copilot session events.
/// Tracks both the last event content (preferred) and accumulated content (fallback).
/// Accumulation is capped at {@code MAX_ACCUMULATED_SIZE} to prevent OOM.
class ContentCollector {

    private static final Logger logger = LoggerFactory.getLogger(ContentCollector.class);
    private static final long INVALID_CACHE_VERSION = -1L;

    private final int maxAccumulatedSize;
    private final CompletableFuture<String> future = new CompletableFuture<>();
    private final StringBuilder accumulatedBuilder;
    private final Object accumulatedLock = new Object();
    private int accumulatedSize;
    private long accumulatedVersion;
    private final AtomicReference<String> lastContent = new AtomicReference<>(null);
    private final AtomicLong lastActivityTime;
    private final AtomicInteger toolCallCount = new AtomicInteger(0);
    private final AtomicInteger messageCount = new AtomicInteger(0);
    private final String agentName;
    private final LongSupplier clockMillisSupplier;

    private String joinedCache;
    private long joinedCacheVersion;

    ContentCollector(String agentName) {
        this(agentName, System::currentTimeMillis,
            ReviewContext.AgentTuningConfig.DEFAULTS.maxAccumulatedSize(),
            ReviewContext.AgentTuningConfig.DEFAULTS.initialAccumulatedCapacity());
    }

    ContentCollector(String agentName, LongSupplier clockMillisSupplier) {
        this(agentName, clockMillisSupplier,
            ReviewContext.AgentTuningConfig.DEFAULTS.maxAccumulatedSize(),
            ReviewContext.AgentTuningConfig.DEFAULTS.initialAccumulatedCapacity());
    }

    ContentCollector(String agentName, LongSupplier clockMillisSupplier,
                     int maxAccumulatedSize, int initialAccumulatedCapacity) {
        this.agentName = agentName;
        this.clockMillisSupplier = clockMillisSupplier;
        this.maxAccumulatedSize = maxAccumulatedSize;
        this.accumulatedBuilder = new StringBuilder(initialAccumulatedCapacity);
        this.lastActivityTime = new AtomicLong(clockMillisSupplier.getAsLong());
    }

    void onActivity() {
        lastActivityTime.set(clockMillisSupplier.getAsLong());
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
            int nextSize = accumulatedSize + content.length();
            if (nextSize <= maxAccumulatedSize) {
                accumulatedBuilder.append(content);
                accumulatedSize = nextSize;
                accumulatedVersion++;
                joinedCache = null;
                joinedCacheVersion = INVALID_CACHE_VERSION;
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

    void onError(String message) {
        if (!future.isDone()) {
            future.completeExceptionally(new SessionEventException("Session error: " + message));
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
        return clockMillisSupplier.getAsLong() - lastActivityTime.get();
    }

    String getAccumulatedContent() {
        return joinAccumulated();
    }

    private String joinAccumulated() {
        synchronized (accumulatedLock) {
            if (accumulatedSize == 0) {
                joinedCache = "";
                joinedCacheVersion = accumulatedVersion;
                return "";
            }
            if (joinedCache != null && joinedCacheVersion == accumulatedVersion) {
                return joinedCache;
            }
            String result = accumulatedBuilder.toString();
            joinedCache = result;
            joinedCacheVersion = accumulatedVersion;
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
