package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewAgent;
import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.report.ReviewResultMerger;
import dev.logicojp.reviewer.target.LocalFileProvider;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.ExecutorUtils;
import dev.logicojp.reviewer.util.FeatureFlags;
import dev.logicojp.reviewer.util.StructuredConcurrencyUtils;
import com.github.copilot.sdk.CopilotClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Orchestrates parallel execution of multiple review agents.
public class ReviewOrchestrator implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewOrchestrator.class);
    
    private final CopilotClient client;
    private final ExecutionConfig executionConfig;
    private final Semaphore concurrencyLimit;
    private final List<CustomInstruction> customInstructions;
    private final String reasoningEffort;
    private final String outputConstraints;
    private final boolean structuredConcurrencyEnabled;
    /// Initialized in constructor — null when Structured Concurrency mode is active.
    private final ExecutorService executorService;
    /// Shared scheduler for idle-timeout handling across all agents.
    private final ScheduledExecutorService sharedScheduler;
    /// Cached MCP server map — built once and shared across all agent contexts.
    private final Map<String, Object> cachedMcpServers;
    private final long localMaxFileSize;
    private final long localMaxTotalSize;

    public record OrchestratorConfig(
        String githubToken,
        GithubMcpConfig githubMcpConfig,
        LocalFileConfig localFileConfig,
        FeatureFlags featureFlags,
        ExecutionConfig executionConfig,
        List<CustomInstruction> customInstructions,
        String reasoningEffort,
        String outputConstraints
    ) {
    }

    public ReviewOrchestrator(CopilotClient client, OrchestratorConfig orchestratorConfig) {
        this.client = client;
        this.executionConfig = orchestratorConfig.executionConfig();
        // Limit concurrent agent executions via --parallelism
        this.concurrencyLimit = new Semaphore(executionConfig.parallelism());
        this.customInstructions = orchestratorConfig.customInstructions() != null
            ? List.copyOf(orchestratorConfig.customInstructions()) : List.of();
        this.reasoningEffort = orchestratorConfig.reasoningEffort();
        this.outputConstraints = orchestratorConfig.outputConstraints();
        this.structuredConcurrencyEnabled = orchestratorConfig.featureFlags().isStructuredConcurrencyEnabled();
        this.executorService = this.structuredConcurrencyEnabled
            ? null
            : Executors.newVirtualThreadPerTaskExecutor();
        this.sharedScheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("idle-timeout-shared", 0).factory());
        this.cachedMcpServers = GithubMcpConfig.buildMcpServers(
            orchestratorConfig.githubToken(), orchestratorConfig.githubMcpConfig());
        this.localMaxFileSize = orchestratorConfig.localFileConfig().maxFileSize();
        this.localMaxTotalSize = orchestratorConfig.localFileConfig().maxTotalSize();
        
        logger.info("Parallelism set to {}", executionConfig.parallelism());
        if (executionConfig.reviewPasses() > 1) {
            logger.info("Multi-pass review enabled: {} passes per agent", executionConfig.reviewPasses());
        }
        if (!this.customInstructions.isEmpty()) {
            logger.info("Custom instructions loaded ({} instruction(s))", this.customInstructions.size());
        }
    }
    
    /// Executes a single agent with semaphore control and error handling.
    /// Shared by both virtual thread and structured concurrency execution modes.
    private ReviewResult executeAgentSafely(AgentConfig config, ReviewTarget target,
                                            ReviewContext context) {
        try {
            concurrencyLimit.acquire();
            try {
                var agent = new ReviewAgent(config, context);
                return agent.review(target);
            } finally {
                concurrencyLimit.release();
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return ReviewResult.builder()
                .agentConfig(config)
                .repository(target.displayName())
                .success(false)
                .errorMessage("Review interrupted while waiting for concurrency permit")
                .build();
        }
    }

    /// Collects results, filters nulls, and logs completion summary.
    private List<ReviewResult> collectAndLogResults(List<ReviewResult> results) {
        List<ReviewResult> filtered = results.stream()
            .filter(Objects::nonNull)
            .toList();
        long successCount = filtered.stream().filter(ReviewResult::isSuccess).count();
        logger.info("Completed {} reviews (success: {}, failed: {})",
            filtered.size(), successCount, filtered.size() - successCount);
        return filtered;
    }

    /// Executes reviews for all provided agents in parallel.
    /// When `reviewPasses > 1`, each agent is reviewed multiple times in parallel
    /// and the results are merged per agent before returning.
    /// @param agents Map of agent name to AgentConfig
    /// @param target The target to review (GitHub repository or local directory)
    /// @return List of ReviewResults from all agents (one per agent, merged if multi-pass)
    public List<ReviewResult> executeReviews(Map<String, AgentConfig> agents, ReviewTarget target) {
        int reviewPasses = executionConfig.reviewPasses();
        int totalTasks = agents.size() * reviewPasses;
        logger.info("Starting parallel review for {} agents ({} passes each, {} total tasks) on target: {}",
            agents.size(), reviewPasses, totalTasks, target.displayName());

        String cachedSourceContent = preComputeSourceContent(target);

        ReviewContext sharedContext = createContext(cachedSourceContent);

        if (structuredConcurrencyEnabled) {
            return executeReviewsStructured(agents, target, sharedContext);
        }

        return executeReviewsAsync(agents, target, sharedContext, reviewPasses, totalTasks);
    }

    private String preComputeSourceContent(ReviewTarget target) {
        if (!(target instanceof ReviewTarget.LocalTarget(Path directory))) {
            return null;
        }

        logger.info("Pre-computing source content for local directory: {}", directory);
        var fileProvider = new LocalFileProvider(directory, localMaxFileSize, localMaxTotalSize);
        var collection = fileProvider.collectAndGenerate();
        logger.info("Collected {} source files from local directory", collection.fileCount());
        logger.debug("Directory summary:\n{}", collection.directorySummary());
        return collection.reviewContent();
    }

    private List<ReviewResult> executeReviewsAsync(Map<String, AgentConfig> agents,
                                                   ReviewTarget target,
                                                   ReviewContext sharedContext,
                                                   int reviewPasses,
                                                   int totalTasks) {
        List<CompletableFuture<ReviewResult>> futures = new ArrayList<>(totalTasks);
        long timeoutMinutes = executionConfig.orchestratorTimeoutMinutes();
        // Per-agent timeout accounts for retries: each attempt gets the full agent timeout
        long perAgentTimeoutMinutes = executionConfig.agentTimeoutMinutes()
            * (executionConfig.maxRetries() + 1L);

        ExecutorService executor = executorService;
        final ReviewContext context = sharedContext;
        
        for (var config : agents.values()) {
            for (int pass = 1; pass <= reviewPasses; pass++) {
                final int passNumber = pass;
                CompletableFuture<ReviewResult> future = CompletableFuture
                .supplyAsync(() -> {
                    if (reviewPasses > 1) {
                        logger.info("Agent {}: starting pass {}/{}",
                            config.name(), passNumber, reviewPasses);
                    }
                    return executeAgentSafely(config, target, context);
                }, executor)
                    .orTimeout(perAgentTimeoutMinutes, TimeUnit.MINUTES)
                    .exceptionally(ex -> {
                        logger.error("Agent {} (pass {}) failed with timeout or error: {}",
                            config.name(), passNumber, ex.getMessage(), ex);
                        return ReviewResult.builder()
                            .agentConfig(config)
                            .repository(target.displayName())
                            .success(false)
                            .errorMessage("Review timed out or failed (pass %d): %s"
                                .formatted(passNumber, ex.getMessage()))
                            .build();
                    });
                futures.add(future);
            }
        }
        
        // Wait for all reviews to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        try {
            allFutures.get(timeoutMinutes + 1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Review orchestration interrupted: {}", e.getMessage(), e);
        } catch (ExecutionException | TimeoutException e) {
            logger.error("Error waiting for reviews to complete: {}", e.getMessage(), e);
        }
        
        // Collect results
        List<ReviewResult> results = new ArrayList<>(futures.size());
        for (CompletableFuture<ReviewResult> future : futures) {
            try {
                results.add(future.getNow(null));
            } catch (Exception e) {
                logger.error("Error collecting review result: {}", e.getMessage(), e);
            }
        }

        return mergeIfRequired(collectAndLogResults(results), reviewPasses);
    }

    private List<ReviewResult> executeReviewsStructured(Map<String, AgentConfig> agents,
                                                        ReviewTarget target,
                                                        ReviewContext sharedContext) {
        int reviewPasses = executionConfig.reviewPasses();
        long timeoutMinutes = executionConfig.orchestratorTimeoutMinutes();
        long perAgentTimeoutMinutes = executionConfig.agentTimeoutMinutes()
            * (executionConfig.maxRetries() + 1L);

        int totalTasks = agents.size() * reviewPasses;
        List<StructuredTaskScope.Subtask<ReviewResult>> tasks = new ArrayList<>(totalTasks);
        try (var scope = StructuredTaskScope.<ReviewResult>open()) {
            for (var config : agents.values()) {
                for (int pass = 1; pass <= reviewPasses; pass++) {
                    final int passNumber = pass;
                    tasks.add(scope.fork(() -> {
                        if (reviewPasses > 1) {
                            logger.info("Agent {}: starting pass {}/{} (structured)",
                                config.name(), passNumber, reviewPasses);
                        }
                        return executeAgentSafely(config, target, sharedContext);
                    }));
                }
            }

            try {
                StructuredConcurrencyUtils.joinWithTimeout(scope, timeoutMinutes + 1, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                logger.error("Structured concurrency timed out after {} minutes", timeoutMinutes, e);
                scope.close();
            }

            List<ReviewResult> results = new ArrayList<>(tasks.size());
            for (var task : tasks) {
                results.add(summarizeTaskResult(task, target, perAgentTimeoutMinutes));
            }

            return mergeIfRequired(collectAndLogResults(results), reviewPasses);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Structured concurrency interrupted", e);
            return List.of();
        }
    }

    private List<ReviewResult> mergeIfRequired(List<ReviewResult> collected, int reviewPasses) {
        if (reviewPasses <= 1) {
            return collected;
        }

        List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(collected);
        logger.info("Merged {} pass results into {} agent results", collected.size(), merged.size());
        return merged;
    }

    private ReviewResult summarizeTaskResult(StructuredTaskScope.Subtask<ReviewResult> task,
                                             ReviewTarget target,
                                             long perAgentTimeoutMinutes) {
        return switch (task.state()) {
            case SUCCESS -> task.get();
            case FAILED -> {
                Throwable cause = task.exception();
                yield ReviewResult.builder()
                    .agentConfig(null)
                    .repository(target.displayName())
                    .success(false)
                    .errorMessage("Review failed: " + (cause != null ? cause.getMessage() : "unknown"))
                    .build();
            }
            case UNAVAILABLE -> ReviewResult.builder()
                .agentConfig(null)
                .repository(target.displayName())
                .success(false)
                .errorMessage("Review cancelled after " + perAgentTimeoutMinutes + " minutes")
                .build();
        };
    }
    
    private ReviewContext createContext(String cachedSourceContent) {
        return ReviewContext.builder()
            .client(client)
            .timeoutMinutes(executionConfig.agentTimeoutMinutes())
            .idleTimeoutMinutes(executionConfig.idleTimeoutMinutes())
            .customInstructions(customInstructions)
            .reasoningEffort(reasoningEffort)
            .maxRetries(executionConfig.maxRetries())
            .outputConstraints(outputConstraints)
            .cachedMcpServers(cachedMcpServers)
            .cachedSourceContent(cachedSourceContent)
                .maxFileSize(localMaxFileSize)
                .maxTotalSize(localMaxTotalSize)
            .sharedScheduler(sharedScheduler)
            .build();
    }

    /// Closes executor resources.
    @Override
    public void close() {
        ExecutorUtils.shutdownGracefully(executorService, 60);
        ExecutorUtils.shutdownGracefully(sharedScheduler, 10);
    }
}
