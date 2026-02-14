package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.report.ContentSanitizer;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.target.LocalFileProvider;
import dev.logicojp.reviewer.target.ReviewTarget;
import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.SystemMessageMode;
import com.github.copilot.sdk.events.AssistantMessageEvent;
import com.github.copilot.sdk.events.SessionErrorEvent;
import com.github.copilot.sdk.events.SessionIdleEvent;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SystemMessageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/// Executes a code review using the Copilot SDK with a specific agent configuration.
public class ReviewAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewAgent.class);

    /// Follow-up prompt sent when the primary send returns empty content.
    /// Used as an in-session retry — much faster than a full retry since MCP context is already loaded.
    private static final String FOLLOWUP_PROMPT =
        "Please provide the complete review results in the specified output format.";
    
    private final AgentConfig config;
    private final ReviewContext ctx;

    public ReviewAgent(AgentConfig config, ReviewContext ctx) {
        this.config = config;
        this.ctx = ctx;
    }
    
    /// Executes the review synchronously on the calling thread with retry support.
    /// Each attempt gets the full configured timeout — the timeout is per-attempt, not cumulative.
    /// @param target The target to review (GitHub repository or local directory)
    /// @return ReviewResult containing the review content
    public ReviewResult review(ReviewTarget target) {
        int totalAttempts = ctx.maxRetries() + 1;
        ReviewResult lastResult = null;
        
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                lastResult = executeReview(target);
                
                if (lastResult.isSuccess()) {
                    if (attempt > 1) {
                        logger.info("Agent {} succeeded on attempt {}/{}", 
                            config.name(), attempt, totalAttempts);
                    }
                    return lastResult;
                }
                
                // Review returned failure (e.g. empty content) — retry if attempts remain
                if (attempt < totalAttempts) {
                    logger.warn("Agent {} failed on attempt {}/{}: {}. Retrying...", 
                        config.name(), attempt, totalAttempts, lastResult.errorMessage());
                } else {
                    logger.error("Agent {} failed on final attempt {}/{}: {}", 
                        config.name(), attempt, totalAttempts, lastResult.errorMessage());
                }
                
            } catch (Exception e) {
                lastResult = ReviewResult.builder()
                    .agentConfig(config)
                    .repository(target.displayName())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
                
                if (attempt < totalAttempts) {
                    logger.warn("Agent {} threw exception on attempt {}/{}: {}. Retrying...", 
                        config.name(), attempt, totalAttempts, e.getMessage());
                } else {
                    logger.error("Agent {} threw exception on final attempt {}/{}: {}", 
                        config.name(), attempt, totalAttempts, e.getMessage(), e);
                }
            }
        }
        
        return lastResult;
    }
    
    private ReviewResult executeReview(ReviewTarget target) throws Exception {
        logger.info("Starting review with agent: {} for target: {}", 
            config.name(), target.displayName());
        
        // Prepare target-specific instruction and optional MCP server configuration
        String instruction;
        String localSourceContent = null;
        Map<String, Object> mcpServers;
        switch (target) {
            case ReviewTarget.LocalTarget(Path directory) -> {
                // Use cached source content from ReviewContext (shared across agents)
                String sourceContent = ctx.cachedSourceContent();
                if (sourceContent == null) {
                    // Fallback: compute locally if not cached (shouldn't happen in normal flow)
                    LocalFileProvider fileProvider = new LocalFileProvider(
                        directory, ctx.maxFileSize(), ctx.maxTotalSize());
                    var collectionResult = fileProvider.collectAndGenerate();
                    sourceContent = collectionResult.reviewContent();
                    logger.debug("Computed source content locally for agent: {}", config.name());
                }
                instruction = config.buildLocalInstructionBase(target.displayName());
                localSourceContent = sourceContent;
                mcpServers = null;
            }
            case ReviewTarget.GitHubTarget(String repository) -> {
                instruction = config.buildInstruction(repository);
                mcpServers = ctx.cachedMcpServers();
            }
        }

        return executeReviewCommon(target.displayName(), instruction, localSourceContent, mcpServers);
    }

    /// Common review execution: configures session, sends instruction, collects result.
    private ReviewResult executeReviewCommon(String displayName,
                                             String instruction,
                                             String localSourceContent,
                                             Map<String, Object> mcpServers) throws Exception {
        String systemPrompt = buildSystemPromptWithCustomInstruction();
        var sessionConfig = new SessionConfig()
            .setModel(config.model())
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt));

        if (mcpServers != null) {
            sessionConfig.setMcpServers(mcpServers);
        }

        String effort = ModelConfig.resolveReasoningEffort(config.model(), ctx.reasoningEffort());
        if (effort != null) {
            logger.info("Setting reasoning effort '{}' for model: {}", effort, config.model());
            sessionConfig.setReasoningEffort(effort);
        }

        var session = ctx.client().createSession(sessionConfig)
            .get(ctx.timeoutMinutes(), TimeUnit.MINUTES);

        try {
            String content = sendAndCollectContent(session, instruction, localSourceContent);

            if (content == null || content.isBlank()) {
                String errorMsg = mcpServers != null
                    ? "Agent returned empty review content — model may have timed out during MCP tool calls"
                    : "Agent returned empty review content";
                return ReviewResult.builder()
                    .agentConfig(config)
                    .repository(displayName)
                    .success(false)
                    .errorMessage(errorMsg)
                    .timestamp(LocalDateTime.now())
                    .build();
            }

            logger.info("Review completed for agent: {} (content length: {} chars)",
                config.name(), content.length());

            return ReviewResult.builder()
                .agentConfig(config)
                .repository(displayName)
                .content(content)
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();
        } finally {
            session.close();
        }
    }
    
    /// Sends an instruction asynchronously and collects the response content using
    /// activity-based idle timeout instead of a fixed wall-clock deadline.
    ///
    /// Unlike `sendAndWait(options, timeoutMs)` which imposes a hard deadline from the
    /// moment the message is sent, this method uses `send()` (fire-and-forget) combined
    /// with event listeners and an idle timer that resets on every incoming event.
    /// This means MCP tool calls that take a long time but are actively progressing will
    /// NOT cause a timeout — the timeout only fires after `idleTimeoutMinutes` of
    /// complete silence (no events at all).
    ///
    /// Fallback strategies when the final event's content is empty:
    /// 1. Use accumulated content from intermediate `AssistantMessageEvent`s
    /// 2. Send an in-session follow-up prompt — much faster than a full retry
    ///    since MCP context is already loaded
    ///
    /// @param session     the active Copilot session
    /// @param instruction the review instruction to send
    /// @param localSourceContent local source content for local-review targets (nullable)
    /// @return the review content, or null if all strategies failed
    private String sendAndCollectContent(CopilotSession session,
                                         String instruction,
                                         String localSourceContent) throws Exception {
        long idleTimeoutMs = TimeUnit.MINUTES.toMillis(ctx.idleTimeoutMinutes());
        long maxTimeoutMs = TimeUnit.MINUTES.toMillis(ctx.timeoutMinutes());

        String initialPrompt = instruction;
        if (localSourceContent != null) {
            initialPrompt = new StringBuilder(instruction.length() + 64)
                .append(instruction)
                .append("\n\n以下は対象ディレクトリのソースコードです。読み込んだらレビューを開始してください。")
                .toString();
        }

        String content;
        if (localSourceContent != null) {
            sendWithActivityTimeout(session, initialPrompt, idleTimeoutMs, maxTimeoutMs);
            sendWithActivityTimeout(session, localSourceContent, idleTimeoutMs, maxTimeoutMs);
            content = sendWithActivityTimeout(session,
                "ソースコードを読み込んだ内容に基づいて、指定された出力形式でレビュー結果を返してください。",
                idleTimeoutMs, maxTimeoutMs);
        } else {
            content = sendWithActivityTimeout(session, initialPrompt, idleTimeoutMs, maxTimeoutMs);
        }

        if (content != null && !content.isBlank()) {
            return ContentSanitizer.sanitize(content);
        }

        // Fallback: In-session follow-up prompt — MCP context is already loaded
        logger.info("Agent {}: primary send returned empty content. Sending follow-up prompt...", config.name());
        content = sendWithActivityTimeout(session, FOLLOWUP_PROMPT,
            idleTimeoutMs, maxTimeoutMs);
        if (content != null && !content.isBlank()) {
            logger.info("Agent {}: follow-up prompt produced content ({} chars)", config.name(), content.length());
            return ContentSanitizer.sanitize(content);
        }

        logger.warn("Agent {}: no content after follow-up", config.name());
        return null;
    }

    /// Sends a prompt via the async `send()` API and waits for completion using
    /// an activity-based idle timeout.
    ///
    /// Timeout semantics:
    /// - **Idle timeout** (`idleTimeoutMs`): Resets on every incoming event.
    ///   Only fires if there are no events for this duration. Prevents false timeouts
    ///   during active MCP tool processing.
    /// - **Max timeout** (`maxTimeoutMs`): Absolute wall-clock safety net.
    ///   Prevents runaway sessions regardless of activity.
    private String sendWithActivityTimeout(CopilotSession session, String prompt,
                                           long idleTimeoutMs, long maxTimeoutMs) throws Exception {
        var collector = new ContentCollector(config.name());
        var subscriptions = registerEventListeners(session, collector);
        var scheduledTask = scheduleIdleTimeout(collector, idleTimeoutMs);
        try {
            logger.debug("Agent {}: sending prompt asynchronously (idle timeout: {} min, max: {} min)",
                config.name(), ctx.idleTimeoutMinutes(), ctx.timeoutMinutes());
            session.send(new MessageOptions().setPrompt(prompt));
            return collector.awaitResult(maxTimeoutMs);
        } catch (TimeoutException e) {
            // Max wall-clock timeout hit — try accumulated content before failing
            String content = collector.getAccumulatedContent();
            if (!content.isBlank()) {
                logger.warn("Agent {}: max timeout reached, returning accumulated content ({} chars)",
                    config.name(), content.length());
                return content;
            }
            throw e;
        } finally {
            scheduledTask.cancel(false);
            subscriptions.closeAll();
        }
    }

    /// Collects content from Copilot session events.
    /// Tracks both the last event content (preferred) and accumulated content (fallback).
    /// Accumulation is capped at {@code MAX_ACCUMULATED_SIZE} to prevent OOM.
    static class ContentCollector {
        private static final int MAX_ACCUMULATED_SIZE = 4 * 1024 * 1024; // 4MB

        private final CompletableFuture<String> future = new CompletableFuture<>();
        private final StringBuilder accumulatedContent = new StringBuilder();
        private final Object contentLock = new Object();
        private final AtomicReference<String> lastContent = new AtomicReference<>(null);
        private final AtomicLong lastActivityTime = new AtomicLong(System.currentTimeMillis());
        private final AtomicInteger toolCallCount = new AtomicInteger(0);
        private final AtomicInteger messageCount = new AtomicInteger(0);
        private final String agentName;

        ContentCollector(String agentName) {
            this.agentName = agentName;
        }

        void onActivity() {
            lastActivityTime.set(System.currentTimeMillis());
        }

        void onMessage(String content, int toolCalls) {
            messageCount.incrementAndGet();
            if (content != null && !content.isBlank()) {
                lastContent.set(content);
                synchronized (contentLock) {
                    if (accumulatedContent.length() + content.length() <= MAX_ACCUMULATED_SIZE) {
                        accumulatedContent.append(content);
                    }
                }
            }
            if (toolCalls > 0) {
                toolCallCount.addAndGet(toolCalls);
            }
        }

        void onIdle() {
            if (!future.isDone()) {
                String last = lastContent.get();
                if (last != null && !last.isBlank()) {
                    future.complete(last);
                } else {
                    String accumulated;
                    synchronized (contentLock) {
                        accumulated = accumulatedContent.toString();
                    }
                    future.complete(accumulated.isBlank() ? null : accumulated);
                }
            }
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
            String content;
            synchronized (contentLock) {
                content = accumulatedContent.isEmpty() ? "" : accumulatedContent.toString();
            }
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
            synchronized (contentLock) {
                return accumulatedContent.isEmpty() ? "" : accumulatedContent.toString();
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

    /// Holds all event subscriptions and provides bulk close.
    private record EventSubscriptions(
        AutoCloseable allEvents,
        AutoCloseable messages,
        AutoCloseable idle,
        AutoCloseable error
    ) {
        void closeAll() {
            for (AutoCloseable sub : new AutoCloseable[]{allEvents, messages, idle, error}) {
                try { sub.close(); } catch (Exception _) {}
            }
        }
    }

    /// Registers event listeners on the session, wiring them to the content collector.
    private EventSubscriptions registerEventListeners(CopilotSession session, ContentCollector collector) {
        var allEvents = session.on(event -> {
            collector.onActivity();
            logger.trace("Agent {}: event received — {}", config.name(), event.getType());
        });

        var messages = session.on(AssistantMessageEvent.class, event -> {
            var data = event.getData();
            int toolCalls = data.getToolRequests() != null ? data.getToolRequests().size() : 0;
            collector.onMessage(data.getContent(), toolCalls);
        });

        var idle = session.on(SessionIdleEvent.class, _ -> collector.onIdle());

        var error = session.on(SessionErrorEvent.class, event -> {
            var data = event.getData();
            collector.onError(data != null ? data.getMessage() : "session error");
        });

        return new EventSubscriptions(allEvents, messages, idle, error);
    }

    /// Schedules periodic idle-timeout checks using the shared scheduler.
    private java.util.concurrent.ScheduledFuture<?> scheduleIdleTimeout(
            ContentCollector collector, long idleTimeoutMs) {
        long checkInterval = Math.max(idleTimeoutMs / 4, 5000);
        return ctx.sharedScheduler().scheduleAtFixedRate(() -> {
            long elapsed = collector.getElapsedSinceLastActivity();
            if (elapsed >= idleTimeoutMs) {
                collector.onIdleTimeout(elapsed, idleTimeoutMs);
            }
        }, checkInterval, checkInterval, TimeUnit.MILLISECONDS);
    }

    /// Builds the system prompt including output constraints and custom instructions.
    /// Output constraints (CoT suppression, language enforcement) are loaded from an external
    /// template and appended after the base system prompt.
    /// Custom instructions from .github/instructions/*.instructions.md are appended last.
    private String buildSystemPromptWithCustomInstruction() {
        StringBuilder sb = new StringBuilder();
        sb.append(config.buildFullSystemPrompt());
        
        // Append output constraints loaded from template
        if (ctx.outputConstraints() != null && !ctx.outputConstraints().isBlank()) {
            sb.append("\n");
            sb.append(ctx.outputConstraints().trim());
            sb.append("\n");
        }
        
        if (!ctx.customInstructions().isEmpty()) {
            for (CustomInstruction instruction : ctx.customInstructions()) {
                if (!instruction.isEmpty()) {
                    sb.append("\n\n");
                    sb.append(instruction.toPromptSection());
                    logger.debug("Applied custom instruction from {} to agent: {}", 
                        instruction.sourcePath(), config.name());
                }
            }
        }
        
        return sb.toString();
    }
}
