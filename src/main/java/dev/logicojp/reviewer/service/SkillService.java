package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.util.FeatureFlags;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.skill.SkillDefinition;
import dev.logicojp.reviewer.skill.SkillExecutor;
import dev.logicojp.reviewer.skill.SkillRegistry;
import dev.logicojp.reviewer.skill.SkillResult;
import dev.logicojp.reviewer.util.ExecutorUtils;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/// Service for managing and executing skills.
@Singleton
public class SkillService {

    private static final Logger logger = LoggerFactory.getLogger(SkillService.class);

    private final SkillRegistry skillRegistry;
    private final CopilotService copilotService;
    private final GithubMcpConfig githubMcpConfig;
    private final ExecutionConfig executionConfig;
    private final FeatureFlags featureFlags;
    private final ExecutorService executorService;
    private final Map<ExecutorCacheKey, SkillExecutor> executorCache;

    @Inject
    public SkillService(SkillRegistry skillRegistry,
                        CopilotService copilotService,
                        GithubMcpConfig githubMcpConfig,
                        ExecutionConfig executionConfig,
                        FeatureFlags featureFlags) {
        this.skillRegistry = skillRegistry;
        this.copilotService = copilotService;
        this.githubMcpConfig = githubMcpConfig;
        this.executionConfig = executionConfig;
        this.featureFlags = featureFlags;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.executorCache = new ConcurrentHashMap<>();
    }

    /// Registers all skills from an agent configuration.
    public void registerAgentSkills(AgentConfig agentConfig) {
        if (agentConfig.skills() != null) {
            for (SkillDefinition skill : agentConfig.skills()) {
                skillRegistry.register(skill);
            }
            logger.info("Registered {} skills from agent: {}",
                agentConfig.skills().size(), agentConfig.name());
        }
    }

    /// Registers multiple agent skills.
    public void registerAllAgentSkills(Map<String, AgentConfig> agents) {
        for (AgentConfig agent : agents.values()) {
            registerAgentSkills(agent);
        }
    }

    /// Gets the skill registry.
    public SkillRegistry getRegistry() {
        return skillRegistry;
    }

    /// Gets a skill by ID.
    public Optional<SkillDefinition> getSkill(String skillId) {
        return skillRegistry.get(skillId);
    }

    /// Executes a skill by ID with the given parameters.
    public CompletableFuture<SkillResult> executeSkill(String skillId,
                                                        Map<String, String> parameters,
                                                        String githubToken,
                                                        String model) {
        Optional<SkillDefinition> skillOpt = skillRegistry.get(skillId);
        if (skillOpt.isEmpty()) {
            return CompletableFuture.completedFuture(
                SkillResult.failure(skillId, "Skill not found: " + skillId));
        }

        return createExecutor(githubToken, model).execute(skillOpt.get(), parameters);
    }

    /// Executes a skill with a custom system prompt.
    public CompletableFuture<SkillResult> executeSkill(String skillId,
                                                        Map<String, String> parameters,
                                                        String githubToken,
                                                        String model,
                                                        String systemPrompt) {
        Optional<SkillDefinition> skillOpt = skillRegistry.get(skillId);
        if (skillOpt.isEmpty()) {
            return CompletableFuture.completedFuture(
                SkillResult.failure(skillId, "Skill not found: " + skillId));
        }

        return createExecutor(githubToken, model).execute(skillOpt.get(), parameters, systemPrompt);
    }

    private SkillExecutor createExecutor(String githubToken, String model) {
        var key = new ExecutorCacheKey(
            secureHash(githubToken),
            model
        );
        return executorCache.computeIfAbsent(key, ignored -> new SkillExecutor(
            copilotService.getClient(),
            githubToken,
            githubMcpConfig,
            model,
            executionConfig.skillTimeoutMinutes(),
            executorService,
            false,
            featureFlags.isStructuredConcurrencyEnabledForSkills()
        ));
    }

    private static String secureHash(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException _) {
            return Integer.toString(token.hashCode());
        }
    }

    private record ExecutorCacheKey(String tokenDigest, String model) {
        @Override
        public String toString() {
            return "ExecutorCacheKey{tokenDigest=***, model='%s'}".formatted(model);
        }
    }

    @PreDestroy
    public void shutdown() {
        ExecutorUtils.shutdownGracefully(executorService, 60);
    }
}
