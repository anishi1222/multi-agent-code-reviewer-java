package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.skill.*;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
    private final ExecutorService executorService;

    @Inject
    public SkillService(CopilotService copilotService,
                        GithubMcpConfig githubMcpConfig,
                        ExecutionConfig executionConfig) {
        this.skillRegistry = new SkillRegistry();
        this.copilotService = copilotService;
        this.githubMcpConfig = githubMcpConfig;
        this.executionConfig = executionConfig;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
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

    /// Lists all available skill IDs.
    public List<String> listSkills() {
        return skillRegistry.getAll().stream()
            .map(SkillDefinition::id)
            .toList();
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

        SkillDefinition skill = skillOpt.get();
        SkillExecutor executor = new SkillExecutor(
            copilotService.getClient(),
            githubToken,
            githubMcpConfig,
            model,
            executionConfig.skillTimeoutMinutes(),
            executorService
        );

        return executor.execute(skill, parameters);
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

        SkillDefinition skill = skillOpt.get();
        SkillExecutor executor = new SkillExecutor(
            copilotService.getClient(),
            githubToken,
            githubMcpConfig,
            model,
            executionConfig.skillTimeoutMinutes(),
            executorService
        );

        return executor.execute(skill, parameters, systemPrompt);
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }
}
