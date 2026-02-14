package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.service.CopilotService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/// Factory for creating {@link ReviewOrchestrator} instances.
///
/// Encapsulates the injection of shared services ({@link CopilotService},
/// {@link GithubMcpConfig}) so that callers only need to provide
/// per-invocation parameters such as execution config overrides and
/// custom instructions.
@Singleton
public class ReviewOrchestratorFactory {

    private final CopilotService copilotService;
    private final GithubMcpConfig githubMcpConfig;

    @Inject
    public ReviewOrchestratorFactory(CopilotService copilotService,
                                     GithubMcpConfig githubMcpConfig) {
        this.copilotService = copilotService;
        this.githubMcpConfig = githubMcpConfig;
    }

    /// Creates a new {@link ReviewOrchestrator} for a single review run.
    ///
    /// @param githubToken        GitHub authentication token
    /// @param executionConfig    Execution configuration (may have overridden parallelism)
    /// @param customInstructions Custom instructions to include in agent prompts
    /// @param reasoningEffort    Reasoning effort level for reasoning models (nullable)
    /// @param outputConstraints  Output constraints template content (nullable)
    /// @return A new ReviewOrchestrator ready to execute reviews
    public ReviewOrchestrator create(String githubToken,
                                     ExecutionConfig executionConfig,
                                     List<CustomInstruction> customInstructions,
                                     String reasoningEffort,
                                     String outputConstraints) {
        return new ReviewOrchestrator(
            copilotService.getClient(),
            githubToken,
            githubMcpConfig,
            executionConfig,
            customInstructions,
            reasoningEffort,
            outputConstraints
        );
    }
}
