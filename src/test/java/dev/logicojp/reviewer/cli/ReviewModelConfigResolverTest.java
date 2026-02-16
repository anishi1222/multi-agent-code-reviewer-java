package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.config.ModelConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewModelConfigResolver")
class ReviewModelConfigResolverTest {

    @Test
    @DisplayName("CLIのデフォルトモデル指定で全モデルを上書きできる")
    void resolvesAllModelsFromDefaultOverride() {
        var resolver = new ReviewModelConfigResolver();

        ModelConfig resolved = resolver.resolve(
            new ModelConfig("base-r", "base-p", "base-s", "high", "base-default"),
            "override-model",
            null,
            null,
            null
        );

        assertThat(resolved.reviewModel()).isEqualTo("override-model");
        assertThat(resolved.reportModel()).isEqualTo("override-model");
        assertThat(resolved.summaryModel()).isEqualTo("override-model");
    }

    @Test
    @DisplayName("個別モデル指定はdefault overrideより優先される")
    void explicitPerStageModelOverridesDefaultModel() {
        var resolver = new ReviewModelConfigResolver();

        ModelConfig resolved = resolver.resolve(
            new ModelConfig("base-r", "base-p", "base-s", "high", "base-default"),
            "override-model",
            "review-only",
            null,
            "summary-only"
        );

        assertThat(resolved.reviewModel()).isEqualTo("review-only");
        assertThat(resolved.reportModel()).isEqualTo("override-model");
        assertThat(resolved.summaryModel()).isEqualTo("summary-only");
    }
}
