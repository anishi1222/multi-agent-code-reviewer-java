package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.SkillConfig;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/// Service for managing and executing skills.
@Singleton
public class SkillService {

    private static final Logger logger = LoggerFactory.getLogger(SkillService.class);

    private final SkillRegistry skillRegistry;
    private final CopilotService copilotService;
    private final GithubMcpConfig githubMcpConfig;
    private final ExecutionConfig executionConfig;
    private final SkillConfig skillConfig;
    private final FeatureFlags featureFlags;
    private final ExecutorService executorService;
    private final Map<ExecutorCacheKey, SkillExecutor> executorCache;
    private final Deque<ExecutorCacheKey> executorCacheOrder;
    private final Object executorCacheOrderLock;

    @Inject
    public SkillService(SkillRegistry skillRegistry,
                        CopilotService copilotService,
                        GithubMcpConfig githubMcpConfig,
                        ExecutionConfig executionConfig,
                        SkillConfig skillConfig,
                        FeatureFlags featureFlags) {
        this.skillRegistry = skillRegistry;
        this.copilotService = copilotService;
        this.githubMcpConfig = githubMcpConfig;
        this.executionConfig = executionConfig;
        this.skillConfig = skillConfig;
        this.featureFlags = featureFlags;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.executorCache = new ConcurrentHashMap<>(
            skillConfig.executorCacheInitialCapacity(),
            (float) skillConfig.executorCacheLoadFactor()
        );
        this.executorCacheOrder = new ArrayDeque<>(skillConfig.executorCacheInitialCapacity());
        this.executorCacheOrderLock = new Object();
    }

    /// Registers all skills from an agent configuration.
    public void registerAgentSkills(AgentConfig agentConfig) {
        for (SkillDefinition skill : agentConfig.skills()) {
            skillRegistry.register(skill);
        }
        if (!agentConfig.skills().isEmpty()) {
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
        return executeResolvedSkill(
            skillId,
            skill -> createExecutor(githubToken, model).execute(skill, parameters)
        );
    }

    /// Executes a skill with a custom system prompt.
    public CompletableFuture<SkillResult> executeSkill(String skillId,
                                                        Map<String, String> parameters,
                                                        String githubToken,
                                                        String model,
                                                        String systemPrompt) {
        return executeResolvedSkill(
            skillId,
            skill -> createExecutor(githubToken, model).execute(skill, parameters, systemPrompt)
        );
    }

    private CompletableFuture<SkillResult> executeResolvedSkill(
            String skillId,
            Function<SkillDefinition, CompletableFuture<SkillResult>> runner) {
        Optional<SkillDefinition> skillOpt = skillRegistry.get(skillId);
        if (skillOpt.isEmpty()) {
            return CompletableFuture.completedFuture(
                SkillResult.failure(skillId, "Skill not found: " + skillId));
        }

        return runner.apply(skillOpt.get());
    }

    private SkillExecutor createExecutor(String githubToken, String model) {
        var key = new ExecutorCacheKey(
            secureHash(githubToken),
            model
        );
        SkillExecutor existing = executorCache.get(key);
        if (existing != null) {
            touchExecutorCacheOrder(key);
            return existing;
        }

        var created = new SkillExecutor(
            copilotService.getClient(),
            githubToken,
            githubMcpConfig,
            new SkillExecutor.SkillExecutorConfig(
                model,
                executionConfig.skillTimeoutMinutes(),
                featureFlags.structuredConcurrencySkills(),
                skillConfig.maxParameterValueLength(),
                skillConfig.executorShutdownTimeoutSeconds()
            ),
            executorService,
            false
        );

        SkillExecutor raced = executorCache.putIfAbsent(key, created);
        if (raced != null) {
            created.close();
            touchExecutorCacheOrder(key);
            return raced;
        }

        touchExecutorCacheOrder(key);
        evictOverflowExecutors();
        return created;
    }

    private void touchExecutorCacheOrder(ExecutorCacheKey key) {
        synchronized (executorCacheOrderLock) {
            executorCacheOrder.remove(key);
            executorCacheOrder.addLast(key);
        }
    }

    private void evictOverflowExecutors() {
        synchronized (executorCacheOrderLock) {
            while (executorCache.size() > skillConfig.maxExecutorCacheSize()) {
                ExecutorCacheKey eldest = executorCacheOrder.pollFirst();
                if (eldest == null) {
                    return;
                }
                SkillExecutor removed = executorCache.remove(eldest);
                if (removed != null) {
                    removed.close();
                }
            }
        }
    }

    private static String secureHash(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
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
        for (SkillExecutor executor : executorCache.values()) {
            executor.close();
        }
        executorCache.clear();
        synchronized (executorCacheOrderLock) {
            executorCacheOrder.clear();
        }
        ExecutorUtils.shutdownGracefully(executorService, skillConfig.serviceShutdownTimeoutSeconds());
    }
}
