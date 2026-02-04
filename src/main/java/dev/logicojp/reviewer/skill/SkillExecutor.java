package dev.logicojp.reviewer.skill;

import dev.logicojp.reviewer.config.GithubMcpConfig;
import com.github.copilot.sdk.*;
import com.github.copilot.sdk.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Executes skills using the Copilot SDK.
 */
public class SkillExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SkillExecutor.class);

    private final CopilotClient client;
    private final String githubToken;
    private final GithubMcpConfig githubMcpConfig;
    private final String defaultModel;
    private final long timeoutMinutes;

    public SkillExecutor(CopilotClient client, String githubToken,
                         GithubMcpConfig githubMcpConfig, String defaultModel,
                         long timeoutMinutes) {
        this.client = client;
        this.githubToken = githubToken;
        this.githubMcpConfig = githubMcpConfig;
        this.defaultModel = defaultModel;
        this.timeoutMinutes = timeoutMinutes;
    }

    /**
     * Executes a skill with the given parameters.
     */
    public CompletableFuture<SkillResult> execute(SkillDefinition skill, Map<String, String> parameters) {
        return execute(skill, parameters, null);
    }

    /**
     * Executes a skill with the given parameters and system prompt.
     */
    public CompletableFuture<SkillResult> execute(SkillDefinition skill,
                                                   Map<String, String> parameters,
                                                   String systemPrompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeSync(skill, parameters, systemPrompt);
            } catch (Exception e) {
                logger.error("Skill execution failed for {}: {}", skill.id(), e.getMessage(), e);
                return SkillResult.failure(skill.id(), e.getMessage());
            }
        });
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
            logger.debug("Sending skill prompt: {}", skill.id());
            var response = session
                .sendAndWait(new MessageOptions().setPrompt(prompt), timeoutMs)
                .get(timeoutMinutes, TimeUnit.MINUTES);

            String content = response.getData().getContent();
            logger.info("Skill execution completed: {}", skill.id());

            return SkillResult.success(skill.id(), content);

        } finally {
            session.close();
        }
    }
}
