package dev.logicojp.reviewer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ModelConfig")
class ModelConfigTest {

    private static final String DEFAULT_MODEL = "claude-sonnet-4";

    @Nested
    @DisplayName("コンストラクタ")
    class Constructor {

        @Test
        @DisplayName("引数なしコンストラクタはデフォルトモデルを設定する")
        void noArgConstructorSetsDefaults() {
            ModelConfig config = new ModelConfig();

            assertThat(config.reviewModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.reportModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.summaryModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("単一モデルコンストラクタは全フィールドに同じモデルを設定する")
        void singleModelConstructorSetsAllFields() {
            String model = "gpt-4o";
            ModelConfig config = new ModelConfig(model);

            assertThat(config.reviewModel()).isEqualTo(model);
            assertThat(config.reportModel()).isEqualTo(model);
            assertThat(config.summaryModel()).isEqualTo(model);
            assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("3引数コンストラクタは各フィールドを個別に設定しデフォルトeffortを使用する")
        void threeArgConstructorSetsIndividualFields() {
            ModelConfig config = new ModelConfig("model-a", "model-b", "model-c");

            assertThat(config.reviewModel()).isEqualTo("model-a");
            assertThat(config.reportModel()).isEqualTo("model-b");
            assertThat(config.summaryModel()).isEqualTo("model-c");
            assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("4引数コンストラクタはreasoningEffortを個別に設定する")
        void fourArgConstructorSetsReasoningEffort() {
            ModelConfig config = new ModelConfig("model-a", "model-b", "model-c", "low");

            assertThat(config.reviewModel()).isEqualTo("model-a");
            assertThat(config.reportModel()).isEqualTo("model-b");
            assertThat(config.summaryModel()).isEqualTo("model-c");
            assertThat(config.reasoningEffort()).isEqualTo("low");
        }

        @Test
        @DisplayName("nullのフィールドはデフォルト値に変換される")
        void nullFieldsAreConvertedToDefaults() {
            ModelConfig config = new ModelConfig(null, null, null, null);

            assertThat(config.reviewModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.reportModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.summaryModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("空白のみのフィールドはデフォルト値に変換される")
        void blankFieldsAreConvertedToDefaults() {
            ModelConfig config = new ModelConfig("  ", "\t", "\n", "  ");

            assertThat(config.reviewModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.reportModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.summaryModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("空文字列のフィールドはデフォルト値に変換される")
        void emptyFieldsAreConvertedToDefaults() {
            ModelConfig config = new ModelConfig("", "", "", "");

            assertThat(config.reviewModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.reportModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.summaryModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }
    }

    @Nested
    @DisplayName("isReasoningModel")
    class IsReasoningModel {

        @Test
        @DisplayName("Opusモデルはreasoningモデルと判定される")
        void opusModelIsReasoning() {
            assertThat(ModelConfig.isReasoningModel("claude-opus-4.6")).isTrue();
            assertThat(ModelConfig.isReasoningModel("claude-opus-4.6-fast")).isTrue();
            assertThat(ModelConfig.isReasoningModel("Claude-Opus-4.5")).isTrue();
        }

        @Test
        @DisplayName("o3モデルはreasoningモデルと判定される")
        void o3ModelIsReasoning() {
            assertThat(ModelConfig.isReasoningModel("o3")).isTrue();
            assertThat(ModelConfig.isReasoningModel("o3-mini")).isTrue();
        }

        @Test
        @DisplayName("o4-miniモデルはreasoningモデルと判定される")
        void o4MiniModelIsReasoning() {
            assertThat(ModelConfig.isReasoningModel("o4-mini")).isTrue();
        }

        @Test
        @DisplayName("非reasoningモデルはfalseを返す")
        void nonReasoningModelReturnsFalse() {
            assertThat(ModelConfig.isReasoningModel("claude-sonnet-4")).isFalse();
            assertThat(ModelConfig.isReasoningModel("gpt-4o")).isFalse();
            assertThat(ModelConfig.isReasoningModel("GPT-5.2-Codex")).isFalse();
        }

        @Test
        @DisplayName("nullの場合はfalseを返す")
        void nullReturnsFalse() {
            assertThat(ModelConfig.isReasoningModel(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("getEffectiveReviewModel")
    class GetEffectiveReviewModel {

        @Test
        @DisplayName("agentModelが指定されている場合はそれを返す")
        void returnsAgentModelWhenProvided() {
            ModelConfig config = new ModelConfig();
            String agentModel = "agent-specific-model";

            assertThat(config.getEffectiveReviewModel(agentModel)).isEqualTo(agentModel);
        }

        @Test
        @DisplayName("agentModelがnullの場合はreviewModelを返す")
        void returnsReviewModelWhenAgentModelIsNull() {
            ModelConfig config = new ModelConfig("my-review-model", "report", "summary");

            assertThat(config.getEffectiveReviewModel(null)).isEqualTo("my-review-model");
        }

        @Test
        @DisplayName("agentModelが空文字列の場合はreviewModelを返す")
        void returnsReviewModelWhenAgentModelIsEmpty() {
            ModelConfig config = new ModelConfig("my-review-model", "report", "summary");

            assertThat(config.getEffectiveReviewModel("")).isEqualTo("my-review-model");
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("デフォルトのビルダーはデフォルトモデルで構築する")
        void defaultBuilderBuildsWithDefaults() {
            ModelConfig config = ModelConfig.builder().build();

            assertThat(config.reviewModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.reportModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.summaryModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("reviewModelを設定できる")
        void canSetReviewModel() {
            ModelConfig config = ModelConfig.builder()
                .reviewModel("custom-review")
                .build();

            assertThat(config.reviewModel()).isEqualTo("custom-review");
            assertThat(config.reportModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.summaryModel()).isEqualTo(DEFAULT_MODEL);
        }

        @Test
        @DisplayName("reportModelを設定できる")
        void canSetReportModel() {
            ModelConfig config = ModelConfig.builder()
                .reportModel("custom-report")
                .build();

            assertThat(config.reviewModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.reportModel()).isEqualTo("custom-report");
            assertThat(config.summaryModel()).isEqualTo(DEFAULT_MODEL);
        }

        @Test
        @DisplayName("summaryModelを設定できる")
        void canSetSummaryModel() {
            ModelConfig config = ModelConfig.builder()
                .summaryModel("custom-summary")
                .build();

            assertThat(config.reviewModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.reportModel()).isEqualTo(DEFAULT_MODEL);
            assertThat(config.summaryModel()).isEqualTo("custom-summary");
        }

        @Test
        @DisplayName("defaultModelで全フィールドを一括設定できる")
        void defaultModelSetsAllFields() {
            ModelConfig config = ModelConfig.builder()
                .defaultModel("unified-model")
                .build();

            assertThat(config.reviewModel()).isEqualTo("unified-model");
            assertThat(config.reportModel()).isEqualTo("unified-model");
            assertThat(config.summaryModel()).isEqualTo("unified-model");
        }

        @Test
        @DisplayName("メソッドチェーンで複数フィールドを設定できる")
        void canChainMultipleMethods() {
            ModelConfig config = ModelConfig.builder()
                .reviewModel("review")
                .reportModel("report")
                .summaryModel("summary")
                .build();

            assertThat(config.reviewModel()).isEqualTo("review");
            assertThat(config.reportModel()).isEqualTo("report");
            assertThat(config.summaryModel()).isEqualTo("summary");
        }

        @Test
        @DisplayName("reasoningEffortを設定できる")
        void canSetReasoningEffort() {
            ModelConfig config = ModelConfig.builder()
                .reasoningEffort("low")
                .build();

            assertThat(config.reasoningEffort()).isEqualTo("low");
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toStringはモデル名とreasoningEffortを含む")
        void toStringContainsModelNamesAndEffort() {
            ModelConfig config = new ModelConfig("r-model", "p-model", "s-model", "medium");

            String result = config.toString();

            assertThat(result).contains("r-model");
            assertThat(result).contains("p-model");
            assertThat(result).contains("s-model");
            assertThat(result).contains("medium");
        }
    }
}
