package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewAgent;
import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.target.LocalFileProvider;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.ExecutorUtils;
import dev.logicojp.reviewer.util.FeatureFlags;
import com.github.copilot.sdk.CopilotClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

/// Orchestrates parallel execution of multiple review agents.
public class ReviewOrchestrator implements AutoCloseable {

    @FunctionalInterface
    interface AgentReviewer {
        ReviewResult review(ReviewTarget target);
    }

    @FunctionalInterface
    interface AgentReviewerFactory {
        AgentReviewer create(AgentConfig config, ReviewContext context);
    }

    @FunctionalInterface
    interface LocalSourceCollector {
        LocalFileProvider.CollectionResult collectAndGenerate();
    }

    @FunctionalInterface
    interface LocalSourceCollectorFactory {
        LocalSourceCollector create(Path directory, LocalFileConfig localFileConfig);
    }
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewOrchestrator.class);

    private final ExecutionConfig executionConfig;
    private final Semaphore concurrencyLimit;
    private final List<CustomInstruction> customInstructions;
    private final String reasoningEffort;
    private final String outputConstraints;
    private final boolean structuredConcurrencyEnabled;
    /// Initialized in constructor — null when Structured Concurrency mode is active.
    private final ExecutorService executorService;
    /// Dedicated executor for per-agent review execution to avoid commonPool usage.
    private final ExecutorService agentExecutionExecutor;
    /// Shared scheduler for idle-timeout handling across all agents.
    private final ScheduledExecutorService sharedScheduler;
    /// Cached MCP server map — built once and shared across all agent contexts.
    private final Map<String, Object> cachedMcpServers;
    private final long localMaxFileSize;
    private final long localMaxTotalSize;
    private final LocalFileConfig localFileConfig;
    private final AgentReviewerFactory reviewerFactory;
    private final LocalSourceCollectorFactory localSourceCollectorFactory;
    private final ReviewResultPipeline reviewResultPipeline;
    private final ReviewExecutionModeRunner reviewExecutionModeRunner;
    private final AgentReviewExecutor agentReviewExecutor;
    private final ReviewContextFactory reviewContextFactory;
    private final LocalSourcePrecomputer localSourcePrecomputer;

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
        this(
            client,
            orchestratorConfig,
            (config, context) -> {
                ReviewAgent agent = new ReviewAgent(config, context);
                return agent::review;
            },
            (directory, config) -> {
                LocalFileProvider provider = new LocalFileProvider(directory, config);
                return provider::collectAndGenerate;
            }
        );
    }

    ReviewOrchestrator(CopilotClient client,
                       OrchestratorConfig orchestratorConfig,
                       AgentReviewerFactory reviewerFactory,
                       LocalSourceCollectorFactory localSourceCollectorFactory) {
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
        this.agentExecutionExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.sharedScheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("idle-timeout-shared", 0).factory());
        this.cachedMcpServers = GithubMcpConfig.buildMcpServers(
            orchestratorConfig.githubToken(), orchestratorConfig.githubMcpConfig());
        this.localFileConfig = orchestratorConfig.localFileConfig();
        this.localMaxFileSize = localFileConfig.maxFileSize();
        this.localMaxTotalSize = localFileConfig.maxTotalSize();
        this.reviewerFactory = Objects.requireNonNull(reviewerFactory);
        this.localSourceCollectorFactory = Objects.requireNonNull(localSourceCollectorFactory);
        this.reviewResultPipeline = new ReviewResultPipeline(logger);
        this.agentReviewExecutor = new AgentReviewExecutor(
            logger,
            concurrencyLimit,
            agentExecutionExecutor,
            this.reviewerFactory
        );
        this.reviewExecutionModeRunner = new ReviewExecutionModeRunner(
            executionConfig,
            executorService,
            reviewResultPipeline
        );
        this.reviewContextFactory = new ReviewContextFactory(
            client,
            executionConfig,
            customInstructions,
            reasoningEffort,
            outputConstraints,
            cachedMcpServers,
            localMaxFileSize,
            localMaxTotalSize,
            localFileConfig,
            sharedScheduler
        );
        this.localSourcePrecomputer = new LocalSourcePrecomputer(
            logger,
            this.localSourceCollectorFactory,
            localFileConfig
        );
        
        logger.info("Parallelism set to {}", executionConfig.parallelism());
        if (executionConfig.reviewPasses() > 1) {
            logger.info("Multi-pass review enabled: {} passes per agent", executionConfig.reviewPasses());
        }
        if (!this.customInstructions.isEmpty()) {
            logger.info("Custom instructions loaded ({} instruction(s))", this.customInstructions.size());
        }
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
        logReviewStart(agents.size(), reviewPasses, totalTasks, target);

        String cachedSourceContent = localSourcePrecomputer.preComputeSourceContent(target);

        ReviewContext sharedContext = reviewContextFactory.create(cachedSourceContent);

        return executeByMode(agents, target, sharedContext);
    }

    private void logReviewStart(int agentCount,
                                int reviewPasses,
                                int totalTasks,
                                ReviewTarget target) {
        logger.info("Starting parallel review for {} agents ({} passes each, {} total tasks) on target: {}",
            agentCount, reviewPasses, totalTasks, target.displayName());
    }

    private List<ReviewResult> executeByMode(Map<String, AgentConfig> agents,
                                             ReviewTarget target,
                                             ReviewContext sharedContext) {
        if (structuredConcurrencyEnabled) {
            return reviewExecutionModeRunner.executeStructured(
                agents,
                target,
                sharedContext,
                agentReviewExecutor::executeAgentSafely
            );
        }

        return reviewExecutionModeRunner.executeAsync(
            agents,
            target,
            sharedContext,
            agentReviewExecutor::executeAgentSafely
        );
    }

    /// Closes executor resources.
    @Override
    public void close() {
        ExecutorUtils.shutdownGracefully(executorService, 60);
        ExecutorUtils.shutdownGracefully(agentExecutionExecutor, 60);
        ExecutorUtils.shutdownGracefully(sharedScheduler, 10);
    }
}
