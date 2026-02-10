package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewAgent;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.config.OrchestratorConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import com.github.copilot.sdk.CopilotClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Orchestrates parallel execution of multiple review agents.
 */
public class ReviewOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewOrchestrator.class);
    
    private final CopilotClient client;
    private final String githubToken;
    private final GithubMcpConfig githubMcpConfig;
    private final ExecutionConfig executionConfig;
    private final ExecutorService executorService;
    private final Semaphore concurrencyLimit;
    private final List<CustomInstruction> customInstructions;
    private final String reasoningEffort;

    public ReviewOrchestrator(CopilotClient client,
                              String githubToken,
                              GithubMcpConfig githubMcpConfig,
                              ExecutionConfig executionConfig,
                              List<CustomInstruction> customInstructions,
                              String reasoningEffort) {
        this.client = client;
        this.githubToken = githubToken;
        this.githubMcpConfig = githubMcpConfig;
        this.executionConfig = executionConfig;
        // Java 21+: Use virtual threads for better scalability with I/O-bound tasks
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        // Limit concurrent agent executions via --parallelism
        this.concurrencyLimit = new Semaphore(executionConfig.parallelism());
        this.customInstructions = customInstructions != null ? List.copyOf(customInstructions) : List.of();
        this.reasoningEffort = reasoningEffort;
        
        logger.info("Parallelism set to {}", executionConfig.parallelism());
        if (!this.customInstructions.isEmpty()) {
            logger.info("Custom instructions loaded ({} instruction(s))", this.customInstructions.size());
        }
    }
    
    /**
     * Executes reviews for all provided agents in parallel.
     * @param agents Map of agent name to AgentConfig
     * @param target The target to review (GitHub repository or local directory)
     * @return List of ReviewResults from all agents
     */
    public List<ReviewResult> executeReviews(Map<String, AgentConfig> agents, ReviewTarget target) {
        logger.info("Starting parallel review for {} agents on target: {}", 
            agents.size(), target.getDisplayName());
        
        List<CompletableFuture<ReviewResult>> futures = new ArrayList<>();
        long timeoutMinutes = executionConfig.orchestratorTimeoutMinutes();
        // Per-agent timeout accounts for retries: each attempt gets the full agent timeout
        long perAgentTimeoutMinutes = executionConfig.agentTimeoutMinutes() 
            * (executionConfig.maxRetries() + 1);
        
        for (AgentConfig config : agents.values()) {
            ReviewAgent agent = new ReviewAgent(config, client, githubToken, githubMcpConfig,
                executionConfig.agentTimeoutMinutes(), customInstructions, reasoningEffort,
                executionConfig.maxRetries());
            CompletableFuture<ReviewResult> future = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        concurrencyLimit.acquire();
                        try {
                            return agent.review(target);
                        } finally {
                            concurrencyLimit.release();
                        }
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        return ReviewResult.builder()
                            .agentConfig(config)
                            .repository(target.getDisplayName())
                            .success(false)
                            .errorMessage("Review interrupted while waiting for concurrency permit")
                            .build();
                    }
                }, executorService)
                .orTimeout(perAgentTimeoutMinutes, TimeUnit.MINUTES)
                .exceptionally(ex -> {
                    logger.error("Agent {} failed with timeout or error: {}", 
                        config.getName(), ex.getMessage());
                    return ReviewResult.builder()
                        .agentConfig(config)
                        .repository(target.getDisplayName())
                        .success(false)
                        .errorMessage("Review timed out or failed: " + ex.getMessage())
                        .build();
                });
            futures.add(future);
        }
        
        // Wait for all reviews to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        try {
            allFutures.get(timeoutMinutes + 1, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.error("Error waiting for reviews to complete: {}", e.getMessage());
        }
        
        // Collect results
        List<ReviewResult> results = new ArrayList<>();
        for (CompletableFuture<ReviewResult> future : futures) {
            try {
                results.add(future.getNow(null));
            } catch (Exception e) {
                logger.error("Error collecting review result: {}", e.getMessage());
            }
        }
        
        // Filter out nulls
        results.removeIf(Objects::isNull);
        
        logger.info("Completed {} reviews (success: {}, failed: {})",
            results.size(),
            results.stream().filter(ReviewResult::isSuccess).count(),
            results.stream().filter(r -> !r.isSuccess()).count());
        
        return results;
    }
    
    /**
     * Shuts down the executor service.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException _) {
            // Java 22+: Unnamed variable - exception not used
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
