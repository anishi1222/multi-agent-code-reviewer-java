package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.util.FeatureFlags;
import io.micronaut.core.annotation.Nullable;

import java.util.List;
import java.util.Objects;

public record OrchestratorConfig(
    @Nullable String githubToken,
    @Nullable GithubMcpConfig githubMcpConfig,
    LocalFileConfig localFileConfig,
    FeatureFlags featureFlags,
    ExecutionConfig executionConfig,
    List<CustomInstruction> customInstructions,
    @Nullable String reasoningEffort,
    @Nullable String outputConstraints,
    PromptTexts promptTexts
) {
    public OrchestratorConfig {
        executionConfig = Objects.requireNonNull(executionConfig, "executionConfig must not be null");
        featureFlags = Objects.requireNonNull(featureFlags, "featureFlags must not be null");
        localFileConfig = localFileConfig != null ? localFileConfig : new LocalFileConfig();
        customInstructions = customInstructions != null ? List.copyOf(customInstructions) : List.of();
        promptTexts = promptTexts != null ? promptTexts : new PromptTexts(null, null, null);
    }

    @Override
    public String toString() {
        return "OrchestratorConfig{githubToken=***, localFileConfig=%s, executionConfig=%s, customInstructions=%d}"
            .formatted(localFileConfig, executionConfig, customInstructions.size());
    }
}