package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.instruction.CustomInstructionLoader;
import dev.logicojp.reviewer.instruction.CustomInstructionSafetyValidator;
import dev.logicojp.reviewer.orchestrator.ReviewOrchestrator;
import dev.logicojp.reviewer.orchestrator.ReviewOrchestratorFactory;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/// Service for executing code reviews with multiple agents.
@Singleton
public class ReviewService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewService.class);
    
    private final ReviewOrchestratorFactory orchestratorFactory;
    private final ExecutionConfig executionConfig;
    private final TemplateService templateService;
    private final CustomInstructionLoader instructionLoader;
    
    @Inject
    public ReviewService(ReviewOrchestratorFactory orchestratorFactory,
                         ExecutionConfig executionConfig,
                         TemplateService templateService,
                         CustomInstructionLoader instructionLoader) {
        this.orchestratorFactory = orchestratorFactory;
        this.executionConfig = executionConfig;
        this.templateService = templateService;
        this.instructionLoader = instructionLoader;
    }
    
    /// Executes reviews with all specified agents in parallel.
    /// @param agentConfigs Map of agent configurations
    /// @param target Target to review (GitHub repository or local directory)
    /// @param githubToken GitHub authentication token (required for GitHub targets)
    /// @param parallelism Number of parallel agents (overrides config)
    /// @param customInstructions List of custom instructions to apply (may be empty)
    /// @param reasoningEffort Reasoning effort level for reasoning models (optional)
    /// @return List of review results from all agents
    public List<ReviewResult> executeReviews(
            Map<String, AgentConfig> agentConfigs,
            ReviewTarget target,
            String githubToken,
            int parallelism,
            List<CustomInstruction> customInstructions,
            String reasoningEffort) {
        
        logger.info("Executing reviews for {} agents on target: {}", 
            agentConfigs.size(), target.displayName());
        List<CustomInstruction> effectiveInstructions = resolveEffectiveInstructions(customInstructions, target);
        ExecutionConfig overriddenConfig = overrideParallelism(parallelism);
        String outputConstraints = loadOutputConstraints();
        
        try (ReviewOrchestrator orchestrator = orchestratorFactory.create(
            githubToken, overriddenConfig,
            effectiveInstructions, reasoningEffort, outputConstraints)) {
            return orchestrator.executeReviews(agentConfigs, target);
        }
    }

    private List<CustomInstruction> resolveEffectiveInstructions(List<CustomInstruction> customInstructions,
                                                                 ReviewTarget target) {
        if (customInstructions != null) {
            return customInstructions;
        }

        List<CustomInstruction> loadedInstructions = instructionLoader.loadForTarget(target);
        List<CustomInstruction> safeInstructions = CustomInstructionSafetyValidator.filterSafe(
            loadedInstructions,
            "Skipped unsafe auto-loaded instruction"
        );

        if (!safeInstructions.isEmpty()) {
            logger.info("Loaded {} custom instruction(s) from target directory", safeInstructions.size());
        }
        return safeInstructions;
    }

    private ExecutionConfig overrideParallelism(int parallelism) {
        return executionConfig.withParallelism(parallelism);
    }

    private String loadOutputConstraints() {
        String outputConstraints = templateService.getOutputConstraints();
        if (outputConstraints != null && !outputConstraints.isBlank()) {
            logger.info("Loaded output constraints from template ({} chars)", outputConstraints.length());
        }
        return outputConstraints;
    }
}
