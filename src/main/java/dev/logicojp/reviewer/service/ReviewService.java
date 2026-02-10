package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.instruction.CustomInstructionLoader;
import dev.logicojp.reviewer.orchestrator.ReviewOrchestrator;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Service for executing code reviews with multiple agents.
 */
@Singleton
public class ReviewService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewService.class);
    
    private final CopilotService copilotService;
    private final GithubMcpConfig githubMcpConfig;
    private final ExecutionConfig executionConfig;
    
    @Inject
    public ReviewService(CopilotService copilotService,
                         GithubMcpConfig githubMcpConfig,
                         ExecutionConfig executionConfig) {
        this.copilotService = copilotService;
        this.githubMcpConfig = githubMcpConfig;
        this.executionConfig = executionConfig;
    }
    
    /**
     * Executes reviews with all specified agents in parallel.
     * @param agentConfigs Map of agent configurations
     * @param target Target to review (GitHub repository or local directory)
     * @param githubToken GitHub authentication token (required for GitHub targets)
     * @param parallelism Number of parallel agents (overrides config)
     * @param customInstructions List of custom instructions to apply (may be empty)
     * @param reasoningEffort Reasoning effort level for reasoning models (optional)
     * @return List of review results from all agents
     */
    public List<ReviewResult> executeReviews(
            Map<String, AgentConfig> agentConfigs,
            ReviewTarget target,
            String githubToken,
            int parallelism,
            List<CustomInstruction> customInstructions,
            String reasoningEffort) {
        
        logger.info("Executing reviews for {} agents on target: {}", 
            agentConfigs.size(), target.getDisplayName());
        
        // Load custom instructions from target if none provided
        List<CustomInstruction> effectiveInstructions = customInstructions;
        if (effectiveInstructions == null || effectiveInstructions.isEmpty()) {
            CustomInstructionLoader loader = new CustomInstructionLoader();
            effectiveInstructions = loader.loadForTarget(target);
            
            if (!effectiveInstructions.isEmpty()) {
                logger.info("Loaded {} custom instruction(s) from target directory", 
                    effectiveInstructions.size());
            }
        }
        
        // Create config with overridden parallelism
        ExecutionConfig overriddenConfig = new ExecutionConfig(
            parallelism,
            executionConfig.orchestratorTimeoutMinutes(),
            executionConfig.agentTimeoutMinutes(),
            executionConfig.skillTimeoutMinutes(),
            executionConfig.summaryTimeoutMinutes(),
            executionConfig.ghAuthTimeoutSeconds(),
            executionConfig.maxRetries()
        );
        
        ReviewOrchestrator orchestrator = new ReviewOrchestrator(
            copilotService.getClient(), githubToken, githubMcpConfig, overriddenConfig,
            effectiveInstructions, reasoningEffort);
        
        try {
            return orchestrator.executeReviews(agentConfigs, target);
        } finally {
            orchestrator.shutdown();
        }
    }
}
