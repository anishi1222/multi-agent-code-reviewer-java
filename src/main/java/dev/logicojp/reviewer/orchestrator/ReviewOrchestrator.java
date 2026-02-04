package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewAgent;
import dev.logicojp.reviewer.report.ReviewResult;
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
    private static final int DEFAULT_PARALLELISM = 4;
    private static final long TIMEOUT_MINUTES = 10;
    
    private final CopilotClient client;
    private final String githubToken;
    private final ExecutorService executorService;
    
    public ReviewOrchestrator(CopilotClient client, String githubToken) {
        this(client, githubToken, DEFAULT_PARALLELISM);
    }
    
    public ReviewOrchestrator(CopilotClient client, String githubToken, int parallelism) {
        this.client = client;
        this.githubToken = githubToken;
        this.executorService = Executors.newFixedThreadPool(parallelism);
    }
    
    /**
     * Executes reviews for all provided agents in parallel.
     * @param agents Map of agent name to AgentConfig
     * @param repository The repository to review
     * @return List of ReviewResults from all agents
     */
    public List<ReviewResult> executeReviews(Map<String, AgentConfig> agents, String repository) {
        logger.info("Starting parallel review for {} agents on repository: {}", agents.size(), repository);
        
        List<CompletableFuture<ReviewResult>> futures = new ArrayList<>();
        
        for (AgentConfig config : agents.values()) {
            ReviewAgent agent = new ReviewAgent(config, client, githubToken);
            CompletableFuture<ReviewResult> future = agent.review(repository)
                .orTimeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .exceptionally(ex -> {
                    logger.error("Agent {} failed with timeout or error: {}", 
                        config.getName(), ex.getMessage());
                    return ReviewResult.builder()
                        .agentConfig(config)
                        .repository(repository)
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
            allFutures.get(TIMEOUT_MINUTES + 1, TimeUnit.MINUTES);
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
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
