package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.orchestrator.ReviewOrchestrator;
import dev.logicojp.reviewer.report.ReviewResult;
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
     * @param repository Target repository (e.g., owner/repo)
     * @param githubToken GitHub authentication token
     * @return List of review results from all agents
     */
    public List<ReviewResult> executeReviews(
            Map<String, AgentConfig> agentConfigs,
            String repository,
            String githubToken) {
        return executeReviews(agentConfigs, repository, githubToken, executionConfig.parallelism());
    }
    
    /**
     * Executes reviews with all specified agents in parallel with custom parallelism.
     * @param agentConfigs Map of agent configurations
     * @param repository Target repository (e.g., owner/repo)
     * @param githubToken GitHub authentication token
     * @param parallelism Number of parallel agents (overrides config)
     * @return List of review results from all agents
     */
    public List<ReviewResult> executeReviews(
            Map<String, AgentConfig> agentConfigs,
            String repository,
            String githubToken,
            int parallelism) {
        
        logger.info("Executing reviews for {} agents on repository: {}", 
            agentConfigs.size(), repository);
        
        // Create config with overridden parallelism
        ExecutionConfig overriddenConfig = new ExecutionConfig(
            parallelism,
            executionConfig.orchestratorTimeoutMinutes(),
            executionConfig.agentTimeoutMinutes(),
            executionConfig.skillTimeoutMinutes(),
            executionConfig.summaryTimeoutMinutes()
        );
        
        ReviewOrchestrator orchestrator = new ReviewOrchestrator(
            copilotService.getClient(), githubToken, githubMcpConfig, overriddenConfig);
        
        try {
            return orchestrator.executeReviews(agentConfigs, repository);
        } finally {
            orchestrator.shutdown();
        }
    }
}
