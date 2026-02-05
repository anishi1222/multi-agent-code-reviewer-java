package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
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
     * @return List of review results from all agents
     */
    public List<ReviewResult> executeReviews(
            Map<String, AgentConfig> agentConfigs,
            ReviewTarget target,
            String githubToken) {
        return executeReviews(agentConfigs, target, githubToken, executionConfig.parallelism(), null);
    }
    
    /**
     * Executes reviews with all specified agents in parallel with custom parallelism.
     * @param agentConfigs Map of agent configurations
     * @param target Target to review (GitHub repository or local directory)
     * @param githubToken GitHub authentication token (required for GitHub targets)
     * @param parallelism Number of parallel agents (overrides config)
     * @return List of review results from all agents
     */
    public List<ReviewResult> executeReviews(
            Map<String, AgentConfig> agentConfigs,
            ReviewTarget target,
            String githubToken,
            int parallelism) {
        return executeReviews(agentConfigs, target, githubToken, parallelism, null);
    }
    
    /**
     * Executes reviews with all specified agents in parallel with custom instruction.
     * @param agentConfigs Map of agent configurations
     * @param target Target to review (GitHub repository or local directory)
     * @param githubToken GitHub authentication token (required for GitHub targets)
     * @param parallelism Number of parallel agents (overrides config)
     * @param customInstruction Custom instruction content (optional)
     * @return List of review results from all agents
     */
    public List<ReviewResult> executeReviews(
            Map<String, AgentConfig> agentConfigs,
            ReviewTarget target,
            String githubToken,
            int parallelism,
            String customInstruction) {
        
        logger.info("Executing reviews for {} agents on target: {}", 
            agentConfigs.size(), target.getDisplayName());
        
        // Load custom instruction from target if not provided
        String effectiveCustomInstruction = customInstruction;
        if (effectiveCustomInstruction == null || effectiveCustomInstruction.isBlank()) {
            CustomInstructionLoader loader = new CustomInstructionLoader();
            effectiveCustomInstruction = loader.loadForTarget(target)
                .map(CustomInstructionLoader.CustomInstruction::content)
                .orElse(null);
            
            if (effectiveCustomInstruction != null) {
                logger.info("Loaded custom instruction from target directory");
            }
        }
        
        // Create config with overridden parallelism
        ExecutionConfig overriddenConfig = new ExecutionConfig(
            parallelism,
            executionConfig.orchestratorTimeoutMinutes(),
            executionConfig.agentTimeoutMinutes(),
            executionConfig.skillTimeoutMinutes(),
            executionConfig.summaryTimeoutMinutes()
        );
        
        ReviewOrchestrator orchestrator = new ReviewOrchestrator(
            copilotService.getClient(), githubToken, githubMcpConfig, overriddenConfig,
            effectiveCustomInstruction);
        
        try {
            return orchestrator.executeReviews(agentConfigs, target);
        } finally {
            orchestrator.shutdown();
        }
    }
}
