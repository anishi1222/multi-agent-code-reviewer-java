package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.report.ContentSanitizer;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.target.LocalFileProvider;
import dev.logicojp.reviewer.target.ReviewTarget;
import com.github.copilot.sdk.*;
import com.github.copilot.sdk.events.AssistantMessageEvent;
import com.github.copilot.sdk.events.SessionErrorEvent;
import com.github.copilot.sdk.events.SessionIdleEvent;
import com.github.copilot.sdk.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static dev.logicojp.reviewer.report.SummaryGenerator.resolveReasoningEffort;

/// Executes a code review using the Copilot SDK with a specific agent configuration.
public class ReviewAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewAgent.class);
    
    private final AgentConfig config;
    private final CopilotClient client;
    private final String githubToken;
    private final GithubMcpConfig githubMcpConfig;
    private final long timeoutMinutes;
    private final long idleTimeoutMinutes;
    private final List<CustomInstruction> customInstructions;
    private final String reasoningEffort;
    private final int maxRetries;
    private final String outputConstraints;

    public ReviewAgent(AgentConfig config,
                       CopilotClient client,
                       String githubToken,
                       GithubMcpConfig githubMcpConfig,
                       long timeoutMinutes,
                       long idleTimeoutMinutes,
                       List<CustomInstruction> customInstructions,
                       String reasoningEffort,
                       int maxRetries,
                       String outputConstraints) {
        this.config = config;
        this.client = client;
        this.githubToken = githubToken;
        this.githubMcpConfig = githubMcpConfig;
        this.timeoutMinutes = timeoutMinutes;
        this.idleTimeoutMinutes = idleTimeoutMinutes;
        this.customInstructions = customInstructions != null ? List.copyOf(customInstructions) : List.of();
        this.reasoningEffort = reasoningEffort;
        this.maxRetries = maxRetries;
        this.outputConstraints = outputConstraints;
    }
    
    /// Executes the review synchronously on the calling thread with retry support.
    /// Each attempt gets the full configured timeout — the timeout is per-attempt, not cumulative.
    /// @param target The target to review (GitHub repository or local directory)
    /// @return ReviewResult containing the review content
    public ReviewResult review(ReviewTarget target) {
        int totalAttempts = maxRetries + 1;
        ReviewResult lastResult = null;
        
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                lastResult = executeReview(target);
                
                if (lastResult.isSuccess()) {
                    if (attempt > 1) {
                        logger.info("Agent {} succeeded on attempt {}/{}", 
                            config.getName(), attempt, totalAttempts);
                    }
                    return lastResult;
                }
                
                // Review returned failure (e.g. empty content) — retry if attempts remain
                if (attempt < totalAttempts) {
                    logger.warn("Agent {} failed on attempt {}/{}: {}. Retrying...", 
                        config.getName(), attempt, totalAttempts, lastResult.getErrorMessage());
                } else {
                    logger.error("Agent {} failed on final attempt {}/{}: {}", 
                        config.getName(), attempt, totalAttempts, lastResult.getErrorMessage());
                }
                
            } catch (Exception e) {
                lastResult = ReviewResult.builder()
                    .agentConfig(config)
                    .repository(target.getDisplayName())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
                
                if (attempt < totalAttempts) {
                    logger.warn("Agent {} threw exception on attempt {}/{}: {}. Retrying...", 
                        config.getName(), attempt, totalAttempts, e.getMessage());
                } else {
                    logger.error("Agent {} threw exception on final attempt {}/{}: {}", 
                        config.getName(), attempt, totalAttempts, e.getMessage(), e);
                }
            }
        }
        
        return lastResult;
    }
    
    private ReviewResult executeReview(ReviewTarget target) throws Exception {
        logger.info("Starting review with agent: {} for target: {}", 
            config.getName(), target.getDisplayName());
        
        // Java 21+: Pattern matching for switch with record patterns
        return switch (target) {
            case ReviewTarget.LocalTarget(Path directory) -> executeLocalReview(directory, target);
            case ReviewTarget.GitHubTarget(String repository) -> executeGitHubReview(repository, target);
        };
    }
    
    private ReviewResult executeGitHubReview(String repository, ReviewTarget target) throws Exception {
        
        // Configure GitHub MCP server for repository access
        Map<String, Object> githubMcp = githubMcpConfig.toMcpServer(githubToken);
        
        // Create session with agent configuration
        String systemPrompt = buildSystemPromptWithCustomInstruction();
        var sessionConfig = new SessionConfig()
            .setModel(config.getModel())
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt))
            .setMcpServers(Map.of("github", githubMcp));

        // Explicitly set reasoning effort for reasoning models (e.g. Claude Opus)
        String effort = resolveReasoningEffort(config.getModel(), reasoningEffort);
        if (effort != null) {
            logger.info("Setting reasoning effort '{}' for model: {}", effort, config.getModel());
            sessionConfig.setReasoningEffort(effort);
        }

        var session = client.createSession(sessionConfig).get(timeoutMinutes, TimeUnit.MINUTES);
        
        try {
            // Build the instruction
            String instruction = config.buildInstruction(repository);
            
            // Send instruction and collect content with activity-based idle timeout
            String content = sendAndCollectContent(session, instruction);
            
            if (content == null || content.isBlank()) {
                return ReviewResult.builder()
                    .agentConfig(config)
                    .repository(repository)
                    .success(false)
                    .errorMessage("Agent returned empty review content — model may have timed out during MCP tool calls")
                    .timestamp(LocalDateTime.now())
                    .build();
            }
            
            logger.info("Review completed for agent: {} (content length: {} chars)", 
                config.getName(), content.length());
            
            return ReviewResult.builder()
                .agentConfig(config)
                .repository(repository)
                .content(content)
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();
                
        } finally {
            session.close();
        }
    }
    
    private ReviewResult executeLocalReview(Path localPath, ReviewTarget target) throws Exception {
        
        // Collect files from local directory
        LocalFileProvider fileProvider = new LocalFileProvider(localPath);
        var localFiles = fileProvider.collectFiles();
        String sourceContent = fileProvider.generateReviewContent(localFiles);
        String directorySummary = fileProvider.generateDirectorySummary(localFiles);
        
        logger.info("Collected source files from local directory: {}", localPath);
        logger.debug("Directory summary:\n{}", directorySummary);
        
        // Create session without MCP servers (no external tools needed for local review)
        String systemPrompt = buildSystemPromptWithCustomInstruction();
        var sessionConfig = new SessionConfig()
            .setModel(config.getModel())
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt));

        // Explicitly set reasoning effort for reasoning models (e.g. Claude Opus)
        String effort = resolveReasoningEffort(config.getModel(), reasoningEffort);
        if (effort != null) {
            logger.info("Setting reasoning effort '{}' for model: {}", effort, config.getModel());
            sessionConfig.setReasoningEffort(effort);
        }

        var session = client.createSession(sessionConfig).get(timeoutMinutes, TimeUnit.MINUTES);
        
        try {
            // Build the instruction with embedded source code
            String instruction = config.buildLocalInstruction(target.getDisplayName(), sourceContent);
            
            // Send instruction and collect content with activity-based idle timeout
            String content = sendAndCollectContent(session, instruction);
            
            if (content == null || content.isBlank()) {
                return ReviewResult.builder()
                    .agentConfig(config)
                    .repository(target.getDisplayName())
                    .success(false)
                    .errorMessage("Agent returned empty review content")
                    .timestamp(LocalDateTime.now())
                    .build();
            }
            
            logger.info("Local review completed for agent: {} (content length: {} chars)", 
                config.getName(), content.length());
            
            return ReviewResult.builder()
                .agentConfig(config)
                .repository(target.getDisplayName())
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
    /// @return the review content, or null if all strategies failed
    private String sendAndCollectContent(CopilotSession session, String instruction) throws Exception {
        long idleTimeoutMs = TimeUnit.MINUTES.toMillis(idleTimeoutMinutes);
        long maxTimeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes);

        // Primary attempt
        String content = sendWithActivityTimeout(session, instruction, idleTimeoutMs, maxTimeoutMs);
        if (content != null && !content.isBlank()) {
            return ContentSanitizer.sanitize(content);
        }

        // Fallback: In-session follow-up prompt — MCP context is already loaded
        logger.info("Agent {}: primary send returned empty content. Sending follow-up prompt...", config.getName());
        content = sendWithActivityTimeout(session,
            "Please provide the complete review results in the specified output format.",
            idleTimeoutMs, maxTimeoutMs);
        if (content != null && !content.isBlank()) {
            logger.info("Agent {}: follow-up prompt produced content ({} chars)", config.getName(), content.length());
            return ContentSanitizer.sanitize(content);
        }

        logger.warn("Agent {}: no content after follow-up", config.getName());
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
        var future = new CompletableFuture<String>();
        var accumulatedContent = new StringBuilder();
        var lastContent = new AtomicReference<String>(null);
        var lastActivityTime = new AtomicLong(System.currentTimeMillis());
        var toolCallCount = new AtomicInteger(0);
        var messageCount = new AtomicInteger(0);

        // Listen for ALL events to reset idle timer
        var allEventsSubscription = session.on(event -> {
            lastActivityTime.set(System.currentTimeMillis());
            logger.trace("Agent {}: event received — {}", config.getName(), event.getType());
        });

        // Track content from AssistantMessageEvents across multi-turn interactions.
        // During MCP tool calls, the model produces intermediate "thinking" messages
        // (e.g. "Let me read the files...") between tool calls. These are NOT part of
        // the final review output. We track each event's content in lastContent so that
        // only the FINAL event's content is used, avoiding Chain of Thought leakage.
        // accumulatedContent is kept as a fallback for timeout scenarios.
        var messageSubscription = session.on(AssistantMessageEvent.class, event -> {
            messageCount.incrementAndGet();
            var data = event.getData();
            if (data.getContent() != null && !data.getContent().isBlank()) {
                lastContent.set(data.getContent());
                accumulatedContent.append(data.getContent());
            }
            if (data.getToolRequests() != null) {
                toolCallCount.addAndGet(data.getToolRequests().size());
            }
        });

        // SessionIdleEvent signals the agent has finished processing.
        // Prefer the last event's content (the final review output) over the
        // accumulated content (which includes intermediate CoT messages).
        var idleSubscription = session.on(SessionIdleEvent.class, _ -> {
            if (!future.isDone()) {
                String last = lastContent.get();
                if (last != null && !last.isBlank()) {
                    future.complete(last);
                } else {
                    // Fallback to accumulated content if the last event had no content
                    String accumulated = accumulatedContent.toString();
                    future.complete(accumulated.isBlank() ? null : accumulated);
                }
            }
        });

        // SessionErrorEvent signals an error
        var errorSubscription = session.on(SessionErrorEvent.class, event -> {
            if (!future.isDone()) {
                var data = event.getData();
                String msg = data != null ? data.getMessage() : "session error";
                future.completeExceptionally(new RuntimeException("Session error: " + msg));
            }
        });

        // Activity-based idle timeout: checks periodically if no events have arrived
        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "idle-timeout-" + config.getName());
            t.setDaemon(true);
            return t;
        });
        // Check every 1/4 of idle timeout interval for responsiveness
        long checkInterval = Math.max(idleTimeoutMs / 4, 5000);
        scheduler.scheduleAtFixedRate(() -> {
            if (future.isDone()) return;
            long elapsed = System.currentTimeMillis() - lastActivityTime.get();
            if (elapsed >= idleTimeoutMs) {
                logger.warn("Agent {}: idle timeout — no events for {} ms ({} messages, {} tool calls)",
                    config.getName(), elapsed, messageCount.get(), toolCallCount.get());
                // Complete with accumulated content if available, otherwise timeout
                String content = accumulatedContent.toString();
                if (!content.isBlank()) {
                    future.complete(content);
                } else {
                    future.completeExceptionally(new TimeoutException(
                        "No activity for " + elapsed + "ms (idle timeout: " + idleTimeoutMs + "ms)"));
                }
            }
        }, checkInterval, checkInterval, TimeUnit.MILLISECONDS);

        try {
            // Send asynchronously — no internal SDK timeout!
            logger.debug("Agent {}: sending prompt asynchronously (idle timeout: {} min, max: {} min)",
                config.getName(), idleTimeoutMinutes, timeoutMinutes);
            session.send(new MessageOptions().setPrompt(prompt));

            // Wait with maximum wall-clock safety net
            String result = future.get(maxTimeoutMs, TimeUnit.MILLISECONDS);
            logger.info("Agent {}: completed ({} chars, {} messages, {} tool calls)",
                config.getName(),
                result != null ? result.length() : 0,
                messageCount.get(), toolCallCount.get());
            return result;
        } catch (TimeoutException e) {
            // Max wall-clock timeout hit — try accumulated content before failing
            String content = accumulatedContent.toString();
            if (!content.isBlank()) {
                logger.warn("Agent {}: max timeout reached, returning accumulated content ({} chars)",
                    config.getName(), content.length());
                return content;
            }
            throw e;
        } finally {
            scheduler.shutdown();
            allEventsSubscription.close();
            messageSubscription.close();
            idleSubscription.close();
            errorSubscription.close();
        }
    }

    /// Builds the system prompt including output constraints and custom instructions.
    /// Output constraints (CoT suppression, language enforcement) are loaded from an external
    /// template and appended after the base system prompt.
    /// Custom instructions from .github/instructions/*.instructions.md are appended last.
    private String buildSystemPromptWithCustomInstruction() {
        StringBuilder sb = new StringBuilder();
        sb.append(config.buildFullSystemPrompt());
        
        // Append output constraints loaded from template
        if (outputConstraints != null && !outputConstraints.isBlank()) {
            sb.append("\n");
            sb.append(outputConstraints.trim());
            sb.append("\n");
        }
        
        if (!customInstructions.isEmpty()) {
            for (CustomInstruction instruction : customInstructions) {
                if (!instruction.isEmpty()) {
                    sb.append("\n\n");
                    sb.append(instruction.toPromptSection());
                    logger.debug("Applied custom instruction from {} to agent: {}", 
                        instruction.sourcePath(), config.getName());
                }
            }
        }
        
        return sb.toString();
    }
}
