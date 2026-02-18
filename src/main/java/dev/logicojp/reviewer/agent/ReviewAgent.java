package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.report.sanitize.ContentSanitizer;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.events.AssistantMessageEvent;
import com.github.copilot.sdk.events.SessionErrorEvent;
import com.github.copilot.sdk.events.SessionIdleEvent;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/// Executes a code review using the Copilot SDK with a specific agent configuration.
public class ReviewAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewAgent.class);

    /// Follow-up prompt sent when the primary send returns empty content.
    /// Used as an in-session retry — much faster than a full retry since MCP context is already loaded.
    private static final String FOLLOWUP_PROMPT =
        "Please provide the complete review results in the specified output format.";
    private static final String DEFAULT_LOCAL_REVIEW_RESULT_PROMPT =
        AgentPromptBuilder.DEFAULT_LOCAL_REVIEW_RESULT_PROMPT;

    record AgentCollaborators(
        ReviewTargetInstructionResolver reviewTargetInstructionResolver,
        ReviewSessionMessageSender reviewSessionMessageSender,
        ReviewRetryExecutor reviewRetryExecutor,
        ReviewSessionConfigFactory reviewSessionConfigFactory,
        ReviewResultFactory reviewResultFactory
    ) {
    }

    public record PromptTemplates(
        String focusAreasGuidance,
        String localSourceHeader,
        String localReviewResultPrompt
    ) {
        static final PromptTemplates DEFAULTS = new PromptTemplates(
            AgentPromptBuilder.DEFAULT_FOCUS_AREAS_GUIDANCE,
            AgentPromptBuilder.DEFAULT_LOCAL_SOURCE_HEADER,
            AgentPromptBuilder.DEFAULT_LOCAL_REVIEW_RESULT_PROMPT
        );
    }
    
    private final AgentConfig config;
    private final ReviewContext ctx;
    private final IdleTimeoutScheduler idleTimeoutScheduler;
    private final ReviewSystemPromptFormatter reviewSystemPromptFormatter;
    private final ReviewTargetInstructionResolver reviewTargetInstructionResolver;
    private final ReviewSessionMessageSender reviewSessionMessageSender;
    private final ReviewRetryExecutor reviewRetryExecutor;
    private final ReviewSessionConfigFactory reviewSessionConfigFactory;
    private final ReviewResultFactory reviewResultFactory;
    private final String focusAreasGuidance;
    private final String localSourceHeaderPrompt;
    private final String localReviewResultPrompt;

    static AgentCollaborators defaultCollaborators(AgentConfig config, ReviewContext ctx) {
        return new AgentCollaborators(
            new ReviewTargetInstructionResolver(
                config,
                ctx.localFileConfig(),
                () -> logger.debug("Computed source content locally for agent: {}", config.name())
            ),
            new ReviewSessionMessageSender(config.name()),
            new ReviewRetryExecutor(config.name(), ctx.maxRetries()),
            new ReviewSessionConfigFactory(),
            new ReviewResultFactory()
        );
    }

    public ReviewAgent(AgentConfig config, ReviewContext ctx) {
        this(config, ctx, PromptTemplates.DEFAULTS);
    }

    public ReviewAgent(AgentConfig config, ReviewContext ctx, PromptTemplates promptTemplates) {
        this(
            config,
            ctx,
            IdleTimeoutScheduler.defaultScheduler(),
            new ReviewSystemPromptFormatter(),
            promptTemplates.focusAreasGuidance(),
            promptTemplates.localSourceHeader(),
            promptTemplates.localReviewResultPrompt(),
            defaultCollaborators(config, ctx)
        );
    }

    /// Full-parameter constructor for testing — all collaborators are injectable.
    ReviewAgent(AgentConfig config,
                ReviewContext ctx,
                IdleTimeoutScheduler idleTimeoutScheduler,
                ReviewSystemPromptFormatter reviewSystemPromptFormatter,
                String focusAreasGuidance,
                String localSourceHeaderPrompt,
                String localReviewResultPrompt,
                AgentCollaborators collaborators) {
        this.config = config;
        this.ctx = ctx;
        this.idleTimeoutScheduler = idleTimeoutScheduler;
        this.reviewSystemPromptFormatter = reviewSystemPromptFormatter;
        this.focusAreasGuidance = focusAreasGuidance;
        this.localSourceHeaderPrompt = localSourceHeaderPrompt;
        this.localReviewResultPrompt = localReviewResultPrompt;
        this.reviewTargetInstructionResolver = collaborators.reviewTargetInstructionResolver();
        this.reviewSessionMessageSender = collaborators.reviewSessionMessageSender();
        this.reviewRetryExecutor = collaborators.reviewRetryExecutor();
        this.reviewSessionConfigFactory = collaborators.reviewSessionConfigFactory();
        this.reviewResultFactory = collaborators.reviewResultFactory();
    }
    
    /// Executes the review synchronously on the calling thread with retry support.
    /// Each attempt gets the full configured timeout — the timeout is per-attempt, not cumulative.
    /// @param target The target to review (GitHub repository or local directory)
    /// @return ReviewResult containing the review content
    public ReviewResult review(ReviewTarget target) {
        return reviewRetryExecutor.execute(
            () -> executeReview(target),
            e -> reviewResultFactory.fromException(config, target.displayName(), e)
        );
    }
    
    private ReviewResult executeReview(ReviewTarget target) throws Exception {
        logger.info("Starting review with agent: {} for target: {}", 
            config.name(), target.displayName());

        var resolvedInstruction = resolveTargetInstruction(target);

        return executeReviewCommon(
            target.displayName(),
            resolvedInstruction.instruction(),
            resolvedInstruction.localSourceContent(),
            resolvedInstruction.mcpServers()
        );
    }

    private ReviewTargetInstructionResolver.ResolvedInstruction resolveTargetInstruction(ReviewTarget target) {
        return reviewTargetInstructionResolver.resolve(
            target,
            ctx.cachedSourceContent(),
            ctx.cachedMcpServers()
        );
    }

    /// Common review execution: configures session, sends instruction, collects result.
    private ReviewResult executeReviewCommon(String displayName,
                                             String instruction,
                                             String localSourceContent,
                                             Map<String, Object> mcpServers) throws Exception {
        String systemPrompt = buildSystemPromptWithCustomInstruction();
        SessionConfig sessionConfig = reviewSessionConfigFactory.create(
            config,
            ctx,
            systemPrompt,
            mcpServers
        );

        try (var session = ctx.client().createSession(sessionConfig)
            .get(ctx.timeoutMinutes(), TimeUnit.MINUTES)) {
            String content = sendAndCollectContent(session, instruction, localSourceContent);

            if (content == null || content.isBlank()) {
                return reviewResultFactory.emptyContentFailure(config, displayName, mcpServers != null);
            }

            logger.info("Review completed for agent: {} (content length: {} chars)",
                config.name(), content.length());

            return reviewResultFactory.success(config, displayName, content);
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
        long idleTimeoutMs = resolveIdleTimeoutMs();
        long maxTimeoutMs = resolveMaxTimeoutMs();

        var messageFlow = createReviewMessageFlow();

        String content = messageFlow.execute(
            instruction,
            localSourceContent,
            prompt -> sendWithActivityTimeout(session, prompt, idleTimeoutMs, maxTimeoutMs)
        );

        return sanitizeReviewContent(content);
    }

    private long resolveIdleTimeoutMs() {
        return TimeUnit.MINUTES.toMillis(ctx.idleTimeoutMinutes());
    }

    private long resolveMaxTimeoutMs() {
        return TimeUnit.MINUTES.toMillis(ctx.timeoutMinutes());
    }

    private ReviewMessageFlow createReviewMessageFlow() {
        return new ReviewMessageFlow(
            config.name(),
            FOLLOWUP_PROMPT,
            localSourceHeaderPrompt,
            localReviewResultPrompt
        );
    }

    private String sanitizeReviewContent(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        return ContentSanitizer.sanitize(content);
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
        logger.debug("Agent {}: sending prompt asynchronously (idle timeout: {} min, max: {} min)",
            config.name(), ctx.idleTimeoutMinutes(), ctx.timeoutMinutes());
        return reviewSessionMessageSender.sendWithActivityTimeout(
            prompt,
            maxTimeoutMs,
            sendPrompt -> session.send(new MessageOptions().setPrompt(sendPrompt)),
            collector -> registerEventListeners(session, collector),
            collector -> {
                var scheduledTask = scheduleIdleTimeout(collector, idleTimeoutMs);
                return () -> scheduledTask.cancel(false);
            }
        );
    }

    /// Registers event listeners on the session, wiring them to the content collector.
    private EventSubscriptions registerEventListeners(CopilotSession session, ContentCollector collector) {
        return ReviewSessionEvents.register(
            config.name(),
            collector,
            handler -> session.on(event -> handler.accept(
                new ReviewSessionEvents.EventData(event.getType(), null, 0, null)
            )),
            handler -> session.on(AssistantMessageEvent.class, event -> {
                var data = event.getData();
                int toolCalls = data.toolRequests() != null ? data.toolRequests().size() : 0;
                handler.accept(new ReviewSessionEvents.EventData("assistant", data.content(), toolCalls, null));
            }),
            handler -> session.on(SessionIdleEvent.class, _ ->
                handler.accept(new ReviewSessionEvents.EventData("idle", null, 0, null))),
            handler -> session.on(SessionErrorEvent.class, event -> {
                var data = event.getData();
                handler.accept(new ReviewSessionEvents.EventData(
                    "error", null, 0, data != null ? data.message() : "session error"
                ));
            }),
            trace -> logger.trace("{}", trace)
        );
    }

    /// Schedules periodic idle-timeout checks using the shared scheduler.
    private java.util.concurrent.ScheduledFuture<?> scheduleIdleTimeout(
            ContentCollector collector, long idleTimeoutMs) {
        return idleTimeoutScheduler.schedule(ctx.sharedScheduler(), collector, idleTimeoutMs);
    }

    /// Builds the system prompt including output constraints and custom instructions.
    /// Output constraints (CoT suppression, language enforcement) are loaded from an external
    /// template and appended after the base system prompt.
    /// Custom instructions from .github/instructions/*.instructions.md are appended last.
    private String buildSystemPromptWithCustomInstruction() {
        return reviewSystemPromptFormatter.format(
            AgentPromptBuilder.buildFullSystemPrompt(config, focusAreasGuidance),
            ctx.outputConstraints(),
            ctx.customInstructions(),
            instruction -> logger.debug("Applied custom instruction from {} to agent: {}",
                instruction.sourcePath(), config.name())
        );
    }
}
