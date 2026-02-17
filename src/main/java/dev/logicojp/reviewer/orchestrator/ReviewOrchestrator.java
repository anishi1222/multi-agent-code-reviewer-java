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
import io.micronaut.core.annotation.Nullable;
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
/// <p>This class is NOT managed by Micronaut DI — callers must ensure
/// {@link #close()} is called (preferably via try-with-resources).</p>
public class ReviewOrchestrator implements AutoCloseable {

    private static final int EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 60;
    private static final int SCHEDULER_SHUTDOWN_TIMEOUT_SECONDS = 10;

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

    record OrchestratorCollaborators(
        AgentReviewerFactory reviewerFactory,
        LocalSourceCollectorFactory localSourceCollectorFactory,
        Semaphore concurrencyLimit,
        @Nullable ExecutorService executorService,
        ExecutorService agentExecutionExecutor,
        ScheduledExecutorService sharedScheduler,
        Map<String, Object> cachedMcpServers,
        ReviewResultPipeline reviewResultPipeline,
        AgentReviewExecutor agentReviewExecutor,
        ReviewExecutionModeRunner reviewExecutionModeRunner,
        ReviewContextFactory reviewContextFactory,
        LocalSourcePrecomputer localSourcePrecomputer
    ) {
        OrchestratorCollaborators {
            reviewerFactory = Objects.requireNonNull(reviewerFactory);
            localSourceCollectorFactory = Objects.requireNonNull(localSourceCollectorFactory);
            concurrencyLimit = Objects.requireNonNull(concurrencyLimit);
            agentExecutionExecutor = Objects.requireNonNull(agentExecutionExecutor);
            sharedScheduler = Objects.requireNonNull(sharedScheduler);
            cachedMcpServers = cachedMcpServers != null ? Map.copyOf(cachedMcpServers) : Map.of();
            reviewResultPipeline = Objects.requireNonNull(reviewResultPipeline);
            agentReviewExecutor = Objects.requireNonNull(agentReviewExecutor);
            reviewExecutionModeRunner = Objects.requireNonNull(reviewExecutionModeRunner);
            reviewContextFactory = Objects.requireNonNull(reviewContextFactory);
            localSourcePrecomputer = Objects.requireNonNull(localSourcePrecomputer);
        }
    }
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewOrchestrator.class);

    private final ExecutionConfig executionConfig;
    private final List<CustomInstruction> customInstructions;
    private final boolean structuredConcurrencyEnabled;
    /// Initialized in constructor — null when Structured Concurrency mode is active.
    private final ExecutorService executorService;
    /// Dedicated executor for per-agent review execution to avoid commonPool usage.
    private final ExecutorService agentExecutionExecutor;
    /// Shared scheduler for idle-timeout handling across all agents.
    private final ScheduledExecutorService sharedScheduler;
    private final ReviewExecutionModeRunner reviewExecutionModeRunner;
    private final AgentReviewExecutor agentReviewExecutor;
    private final ReviewContextFactory reviewContextFactory;
    private final LocalSourcePrecomputer localSourcePrecomputer;

    public record OrchestratorConfig(
        @Nullable String githubToken,
        @Nullable GithubMcpConfig githubMcpConfig,
        LocalFileConfig localFileConfig,
        FeatureFlags featureFlags,
        ExecutionConfig executionConfig,
        List<CustomInstruction> customInstructions,
        @Nullable String reasoningEffort,
        @Nullable String outputConstraints,
        String focusAreasGuidance,
        String localSourceHeader,
        String localReviewResultRequest
    ) {
        public OrchestratorConfig {
            executionConfig = Objects.requireNonNull(executionConfig, "executionConfig must not be null");
            featureFlags = Objects.requireNonNull(featureFlags, "featureFlags must not be null");
            localFileConfig = localFileConfig != null ? localFileConfig : new LocalFileConfig();
            customInstructions = customInstructions != null ? List.copyOf(customInstructions) : List.of();
            focusAreasGuidance = focusAreasGuidance != null ? focusAreasGuidance : "";
            localSourceHeader = localSourceHeader != null ? localSourceHeader : "";
            localReviewResultRequest = localReviewResultRequest != null ? localReviewResultRequest : "";
        }

        @Override
        public String toString() {
            return "OrchestratorConfig{githubToken=***, localFileConfig=%s, executionConfig=%s, customInstructions=%d}"
                .formatted(localFileConfig, executionConfig, customInstructions.size());
        }
    }

    public ReviewOrchestrator(CopilotClient client, OrchestratorConfig orchestratorConfig) {
        this(client, orchestratorConfig, defaultCollaborators(client, orchestratorConfig));
    }

    ReviewOrchestrator(CopilotClient client,
                       OrchestratorConfig orchestratorConfig,
                       AgentReviewerFactory reviewerFactory,
                       LocalSourceCollectorFactory localSourceCollectorFactory) {
        this(
            client,
            orchestratorConfig,
            collaboratorsFromFactories(client, orchestratorConfig, reviewerFactory, localSourceCollectorFactory)
        );
    }

    ReviewOrchestrator(CopilotClient client,
                       OrchestratorConfig orchestratorConfig,
                       OrchestratorCollaborators collaborators) {
        this.executionConfig = orchestratorConfig.executionConfig();
        this.customInstructions = orchestratorConfig.customInstructions();
        this.structuredConcurrencyEnabled = orchestratorConfig.featureFlags().structuredConcurrency();
        this.executorService = collaborators.executorService();
        this.agentExecutionExecutor = collaborators.agentExecutionExecutor();
        this.sharedScheduler = collaborators.sharedScheduler();
        this.agentReviewExecutor = collaborators.agentReviewExecutor();
        this.reviewExecutionModeRunner = collaborators.reviewExecutionModeRunner();
        this.reviewContextFactory = collaborators.reviewContextFactory();
        this.localSourcePrecomputer = collaborators.localSourcePrecomputer();
        
        logger.info("Parallelism set to {}", executionConfig.parallelism());
        if (executionConfig.reviewPasses() > 1) {
            logger.info("Multi-pass review enabled: {} passes per agent", executionConfig.reviewPasses());
        }
        if (!this.customInstructions.isEmpty()) {
            logger.info("Custom instructions loaded ({} instruction(s))", this.customInstructions.size());
        }
    }

    static OrchestratorCollaborators defaultCollaborators(CopilotClient client,
                                                          OrchestratorConfig orchestratorConfig) {
        return collaboratorsFromFactories(
            client,
            orchestratorConfig,
            defaultReviewerFactory(orchestratorConfig),
            defaultLocalSourceCollectorFactory()
        );
    }

    private static OrchestratorCollaborators collaboratorsFromFactories(
            CopilotClient client,
            OrchestratorConfig orchestratorConfig,
            AgentReviewerFactory reviewerFactory,
            LocalSourceCollectorFactory localSourceCollectorFactory) {
        boolean structuredConcurrencyEnabled = orchestratorConfig.featureFlags().structuredConcurrency();
        ExecutorService executorService = structuredConcurrencyEnabled
            ? null
            : Executors.newVirtualThreadPerTaskExecutor();
        ExecutorService agentExecutionExecutor = null;
        ScheduledExecutorService sharedScheduler = null;
        try {
            Semaphore concurrencyLimit = new Semaphore(orchestratorConfig.executionConfig().parallelism());
            agentExecutionExecutor = Executors.newVirtualThreadPerTaskExecutor();
            sharedScheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("idle-timeout-shared", 0).factory());
            Map<String, Object> cachedMcpServers = GithubMcpConfig.buildMcpServers(
                orchestratorConfig.githubToken(),
                orchestratorConfig.githubMcpConfig()
            ).orElse(Map.of());
            ReviewResultPipeline reviewResultPipeline = new ReviewResultPipeline();
            AgentReviewExecutor agentReviewExecutor = new AgentReviewExecutor(
                concurrencyLimit,
                agentExecutionExecutor,
                reviewerFactory
            );
            ReviewExecutionModeRunner reviewExecutionModeRunner = new ReviewExecutionModeRunner(
                orchestratorConfig.executionConfig(),
                executorService,
                reviewResultPipeline
            );
            ReviewContextFactory reviewContextFactory = new ReviewContextFactory(
                client,
                orchestratorConfig.executionConfig(),
                orchestratorConfig.customInstructions(),
                orchestratorConfig.reasoningEffort(),
                orchestratorConfig.outputConstraints(),
                cachedMcpServers,
                orchestratorConfig.localFileConfig(),
                sharedScheduler
            );
            LocalSourcePrecomputer localSourcePrecomputer = new LocalSourcePrecomputer(
                localSourceCollectorFactory,
                orchestratorConfig.localFileConfig()
            );

            return new OrchestratorCollaborators(
                reviewerFactory,
                localSourceCollectorFactory,
                concurrencyLimit,
                executorService,
                agentExecutionExecutor,
                sharedScheduler,
                cachedMcpServers,
                reviewResultPipeline,
                agentReviewExecutor,
                reviewExecutionModeRunner,
                reviewContextFactory,
                localSourcePrecomputer
            );
        } catch (Exception e) {
            ExecutorUtils.shutdownGracefully(executorService, EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
            ExecutorUtils.shutdownGracefully(agentExecutionExecutor, EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
            ExecutorUtils.shutdownGracefully(sharedScheduler, SCHEDULER_SHUTDOWN_TIMEOUT_SECONDS);
            throw e;
        }
    }

    private static AgentReviewerFactory defaultReviewerFactory(OrchestratorConfig orchestratorConfig) {
        return (config, context) -> {
            ReviewAgent agent = new ReviewAgent(
                config,
                context,
                new ReviewAgent.PromptTemplates(
                    orchestratorConfig.focusAreasGuidance(),
                    orchestratorConfig.localSourceHeader(),
                    orchestratorConfig.localReviewResultRequest()
                )
            );
            return agent::review;
        };
    }

    private static LocalSourceCollectorFactory defaultLocalSourceCollectorFactory() {
        return (directory, config) -> {
            LocalFileProvider provider = new LocalFileProvider(directory, config);
            return provider::collectAndGenerate;
        };
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
        ExecutorUtils.shutdownGracefully(executorService, EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
        ExecutorUtils.shutdownGracefully(agentExecutionExecutor, EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
        ExecutorUtils.shutdownGracefully(sharedScheduler, SCHEDULER_SHUTDOWN_TIMEOUT_SECONDS);
    }
}
