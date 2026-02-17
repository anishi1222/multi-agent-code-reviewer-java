package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.target.ReviewTarget;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Singleton
class ReviewRunRequestFactory {

    public ReviewRunExecutor.ReviewRunRequest create(
        ReviewCommand.ParsedOptions options,
        ReviewTarget target,
        ModelConfig modelConfig,
        Map<String, AgentConfig> agentConfigs,
        List<CustomInstruction> customInstructions,
        Path outputDirectory
    ) {
        String summaryModel = resolveSummaryModel(modelConfig);
        String reasoningEffort = resolveReasoningEffort(modelConfig);
        int parallelism = resolveParallelism(options);
        boolean noSummary = isSummaryDisabled(options);

        return new ReviewRunExecutor.ReviewRunRequest(
            target,
            summaryModel,
            reasoningEffort,
            agentConfigs,
            parallelism,
            noSummary,
            customInstructions,
            outputDirectory
        );
    }

    private String resolveSummaryModel(ModelConfig modelConfig) {
        return modelConfig.summaryModel();
    }

    private String resolveReasoningEffort(ModelConfig modelConfig) {
        return modelConfig.reasoningEffort();
    }

    private int resolveParallelism(ReviewCommand.ParsedOptions options) {
        return options.parallelism();
    }

    private boolean isSummaryDisabled(ReviewCommand.ParsedOptions options) {
        return options.noSummary();
    }
}
