package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.config.ModelConfig;
import jakarta.inject.Singleton;

/// Resolves effective model configuration from defaults and CLI overrides.
@Singleton
public class ReviewModelConfigResolver {

    public ModelConfig resolve(ModelConfig defaultModelConfig,
                               String defaultModel,
                               String reviewModel,
                               String reportModel,
                               String summaryModel) {
        ModelConfig baseConfig = defaultModelConfig != null ? defaultModelConfig : new ModelConfig();
        ModelConfig.Builder builder = createBaseBuilder(baseConfig);
        applyModelOverrides(builder, defaultModel, reviewModel, reportModel, summaryModel);

        return builder.build();
    }

    private ModelConfig.Builder createBaseBuilder(ModelConfig baseConfig) {
        return ModelConfig.builder()
            .reviewModel(baseConfig.reviewModel())
            .reportModel(baseConfig.reportModel())
            .summaryModel(baseConfig.summaryModel())
            .reasoningEffort(baseConfig.reasoningEffort());
    }

    private void applyModelOverrides(ModelConfig.Builder builder,
                                     String defaultModel,
                                     String reviewModel,
                                     String reportModel,
                                     String summaryModel) {
        applyIfProvided(defaultModel, builder::allModels);
        applyIfProvided(reviewModel, builder::reviewModel);
        applyIfProvided(reportModel, builder::reportModel);
        applyIfProvided(summaryModel, builder::summaryModel);
    }

    private void applyIfProvided(String value, java.util.function.Consumer<String> applier) {
        if (value != null) {
            applier.accept(value);
        }
    }
}
