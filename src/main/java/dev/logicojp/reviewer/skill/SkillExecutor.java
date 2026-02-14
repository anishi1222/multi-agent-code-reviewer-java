package dev.logicojp.reviewer.skill;

import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.util.FeatureFlags;
import com.github.copilot.sdk.*;
import com.github.copilot.sdk.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Executes skills using the Copilot SDK.
public class SkillExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SkillExecutor.class);

    private final CopilotClient client;
    private final String githubToken;
    private final GithubMcpConfig githubMcpConfig;
    private final String defaultModel;
    private final long timeoutMinutes;
    private final Executor executor;
    private final boolean structuredConcurrencyEnabled;

    public SkillExecutor(CopilotClient client, String githubToken,
                         GithubMcpConfig githubMcpConfig, String defaultModel,
                         long timeoutMinutes) {
        this(client, githubToken, githubMcpConfig, defaultModel, timeoutMinutes,
             Executors.newVirtualThreadPerTaskExecutor());
    }

    public SkillExecutor(CopilotClient client, String githubToken,
                         GithubMcpConfig githubMcpConfig, String defaultModel,
                         long timeoutMinutes, Executor executor) {
        this.client = client;
        this.githubToken = githubToken;
        this.githubMcpConfig = githubMcpConfig;
        this.defaultModel = defaultModel;
        this.timeoutMinutes = timeoutMinutes;
        this.executor = executor;
        this.structuredConcurrencyEnabled = isStructuredConcurrencyEnabledForSkills();
    }

    /// Executes a skill with the given parameters.
    public CompletableFuture<SkillResult> execute(SkillDefinition skill, Map<String, String> parameters) {
        return execute(skill, parameters, null);
    }

    /// Executes a skill with the given parameters and system prompt.
    public CompletableFuture<SkillResult> execute(SkillDefinition skill,
                                                  Map<String, String> parameters,
                                                  String systemPrompt) {
        return CompletableFuture.supplyAsync(() -> executeSafely(skill, parameters, systemPrompt), executor);
    }

    private SkillResult executeSafely(SkillDefinition skill,
                                      Map<String, String> parameters,
                                      String systemPrompt) {
        try {
            if (structuredConcurrencyEnabled) {
                return executeWithStructuredConcurrency(skill, parameters, systemPrompt);
            }
            return executeSync(skill, parameters, systemPrompt);
        } catch (Exception e) {
            logger.error("Skill execution failed for {}: {}", skill.id(), e.getMessage(), e);
            return SkillResult.failure(skill.id(), e.getMessage());
        }
    }

    private SkillResult executeWithStructuredConcurrency(SkillDefinition skill,
                                                         Map<String, String> parameters,
                                                         String systemPrompt) throws Exception {
        try (var scope = StructuredTaskScope.<SkillResult>open()) {
            var task = scope.fork(() -> executeSync(skill, parameters, systemPrompt));
            // Workaround: StructuredTaskScope.join() does not support timeout natively.
            // Wrapping in CompletableFuture.runAsync() to enforce a wall-clock deadline.
            // TODO: Replace with scope.joinUntil(Instant) when available in a future JDK release.
            var joinFuture = CompletableFuture.runAsync(() -> {
                try {
                    scope.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });
            try {
                joinFuture.get(timeoutMinutes, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                scope.close();
                return SkillResult.failure(skill.id(),
                    "Skill timed out after " + timeoutMinutes + " minutes");
            } catch (ExecutionException e) {
                return SkillResult.failure(skill.id(),
                    "Skill failed: " + e.getCause().getMessage());
            }

            return switch (task.state()) {
                case SUCCESS -> task.get();
                case FAILED -> SkillResult.failure(skill.id(),
                    "Skill failed: " + (task.exception() != null ? task.exception().getMessage() : "unknown"));
                case UNAVAILABLE -> SkillResult.failure(skill.id(),
                    "Skill cancelled after " + timeoutMinutes + " minutes");
            };
        }
    }

    private SkillResult executeSync(SkillDefinition skill,
                                     Map<String, String> parameters,
                                     String systemPrompt) throws Exception {
        logger.info("Executing skill: {} with parameters: {}", skill.id(), parameters.keySet());

        // Validate parameters
        skill.validateParameters(parameters);

        // Build the prompt with parameter substitution
        String prompt = skill.buildPrompt(parameters);

        // Configure GitHub MCP server for repository access
        Map<String, Object> githubMcp = githubMcpConfig.toMcpServer(githubToken);

        // Create session with skill configuration
        var sessionConfigBuilder = new SessionConfig()
            .setModel(defaultModel)
            .setMcpServers(Map.of("github", githubMcp));

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sessionConfigBuilder.setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt));
        }

        var session = client.createSession(sessionConfigBuilder).get(timeoutMinutes, TimeUnit.MINUTES);

        long timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes);
        
        try {
            logger.debug("Sending skill prompt: {} (timeout: {} min)", skill.id(), timeoutMinutes);
            // Pass the configured timeout to sendAndWait explicitly.
            // The SDK default (60s) is too short for skills involving MCP tool calls.
            var response = session
                .sendAndWait(new MessageOptions().setPrompt(prompt), timeoutMs)
                .get(timeoutMinutes, TimeUnit.MINUTES);

            String content = response.getData().getContent();
            
            if (content == null || content.isBlank()) {
                logger.warn("Skill {} returned empty content", skill.id());
                return SkillResult.failure(skill.id(), "Skill returned empty content");
            }
            
            logger.info("Skill execution completed: {} (content length: {} chars)", skill.id(), content.length());

            return SkillResult.success(skill.id(), content);

        } finally {
            session.close();
        }
    }

    private boolean isStructuredConcurrencyEnabledForSkills() {
        return FeatureFlags.isStructuredConcurrencyEnabledForSkills();
    }
}
