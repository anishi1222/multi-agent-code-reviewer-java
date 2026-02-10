package dev.logicojp.reviewer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExecutionConfig")
class ExecutionConfigTest {

    @Nested
    @DisplayName("コンストラクタ - デフォルト値")
    class DefaultValues {

        @Test
        @DisplayName("parallelismが0以下の場合は4に設定される")
        void parallelismZeroDefaultsToFour() {
            ExecutionConfig config = new ExecutionConfig(0, 10, 5, 5, 5, 10, 2);
            assertThat(config.parallelism()).isEqualTo(4);
        }

        @Test
        @DisplayName("parallelismが負数の場合は4に設定される")
        void parallelismNegativeDefaultsToFour() {
            ExecutionConfig config = new ExecutionConfig(-1, 10, 5, 5, 5, 10, 2);
            assertThat(config.parallelism()).isEqualTo(4);
        }

        @Test
        @DisplayName("orchestratorTimeoutMinutesが0以下の場合は10に設定される")
        void orchestratorTimeoutZeroDefaultsToTen() {
            ExecutionConfig config = new ExecutionConfig(4, 0, 5, 5, 5, 10, 2);
            assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(10);
        }

        @Test
        @DisplayName("orchestratorTimeoutMinutesが負数の場合は10に設定される")
        void orchestratorTimeoutNegativeDefaultsToTen() {
            ExecutionConfig config = new ExecutionConfig(4, -5, 5, 5, 5, 10, 2);
            assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(10);
        }

        @Test
        @DisplayName("agentTimeoutMinutesが0以下の場合は5に設定される")
        void agentTimeoutZeroDefaultsToFive() {
            ExecutionConfig config = new ExecutionConfig(4, 10, 0, 5, 5, 10, 2);
            assertThat(config.agentTimeoutMinutes()).isEqualTo(5);
        }

        @Test
        @DisplayName("agentTimeoutMinutesが負数の場合は5に設定される")
        void agentTimeoutNegativeDefaultsToFive() {
            ExecutionConfig config = new ExecutionConfig(4, 10, -3, 5, 5, 10, 2);
            assertThat(config.agentTimeoutMinutes()).isEqualTo(5);
        }

        @Test
        @DisplayName("skillTimeoutMinutesが0以下の場合は5に設定される")
        void skillTimeoutZeroDefaultsToFive() {
            ExecutionConfig config = new ExecutionConfig(4, 10, 5, 0, 5, 10, 2);
            assertThat(config.skillTimeoutMinutes()).isEqualTo(5);
        }

        @Test
        @DisplayName("skillTimeoutMinutesが負数の場合は5に設定される")
        void skillTimeoutNegativeDefaultsToFive() {
            ExecutionConfig config = new ExecutionConfig(4, 10, 5, -2, 5, 10, 2);
            assertThat(config.skillTimeoutMinutes()).isEqualTo(5);
        }

        @Test
        @DisplayName("summaryTimeoutMinutesが0以下の場合は5に設定される")
        void summaryTimeoutZeroDefaultsToFive() {
            ExecutionConfig config = new ExecutionConfig(4, 10, 5, 5, 0, 10, 2);
            assertThat(config.summaryTimeoutMinutes()).isEqualTo(5);
        }

        @Test
        @DisplayName("summaryTimeoutMinutesが負数の場合は5に設定される")
        void summaryTimeoutNegativeDefaultsToFive() {
            ExecutionConfig config = new ExecutionConfig(4, 10, 5, 5, -1, 10, 2);
            assertThat(config.summaryTimeoutMinutes()).isEqualTo(5);
        }

        @Test
        @DisplayName("ghAuthTimeoutSecondsが0以下の場合は10に設定される")
        void ghAuthTimeoutZeroDefaultsToTen() {
            ExecutionConfig config = new ExecutionConfig(4, 10, 5, 5, 5, 0, 2);
            assertThat(config.ghAuthTimeoutSeconds()).isEqualTo(10);
        }

        @Test
        @DisplayName("ghAuthTimeoutSecondsが負数の場合は10に設定される")
        void ghAuthTimeoutNegativeDefaultsToTen() {
            ExecutionConfig config = new ExecutionConfig(4, 10, 5, 5, 5, -3, 2);
            assertThat(config.ghAuthTimeoutSeconds()).isEqualTo(10);
        }

        @Test
        @DisplayName("maxRetriesが負数の場合はデフォルト値に設定される")
        void maxRetriesNegativeDefaultsToDefault() {
            ExecutionConfig config = new ExecutionConfig(4, 10, 5, 5, 5, 10, -1);
            assertThat(config.maxRetries()).isEqualTo(ExecutionConfig.DEFAULT_MAX_RETRIES);
        }
    }

    @Nested
    @DisplayName("コンストラクタ - 正常値")
    class ValidValues {

        @Test
        @DisplayName("正の値が指定された場合はそのまま保持される")
        void positiveValuesArePreserved() {
            ExecutionConfig config = new ExecutionConfig(8, 20, 15, 10, 12, 30, 3);

            assertThat(config.parallelism()).isEqualTo(8);
            assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(20);
            assertThat(config.agentTimeoutMinutes()).isEqualTo(15);
            assertThat(config.skillTimeoutMinutes()).isEqualTo(10);
            assertThat(config.summaryTimeoutMinutes()).isEqualTo(12);
            assertThat(config.ghAuthTimeoutSeconds()).isEqualTo(30);
            assertThat(config.maxRetries()).isEqualTo(3);
        }

        @Test
        @DisplayName("parallelismが1の場合はそのまま保持される")
        void parallelismOneIsPreserved() {
            ExecutionConfig config = new ExecutionConfig(1, 10, 5, 5, 5, 10, 2);
            assertThat(config.parallelism()).isEqualTo(1);
        }

        @Test
        @DisplayName("タイムアウトが1の場合はそのまま保持される")
        void timeoutOneIsPreserved() {
            ExecutionConfig config = new ExecutionConfig(4, 1, 1, 1, 1, 1, 2);

            assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(1);
            assertThat(config.agentTimeoutMinutes()).isEqualTo(1);
            assertThat(config.skillTimeoutMinutes()).isEqualTo(1);
            assertThat(config.summaryTimeoutMinutes()).isEqualTo(1);
            assertThat(config.ghAuthTimeoutSeconds()).isEqualTo(1);
        }

        @Test
        @DisplayName("大きな値も正しく保持される")
        void largeValuesArePreserved() {
            ExecutionConfig config = new ExecutionConfig(100, 1000, 500, 300, 200, 600, 10);

            assertThat(config.parallelism()).isEqualTo(100);
            assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(1000);
            assertThat(config.agentTimeoutMinutes()).isEqualTo(500);
            assertThat(config.skillTimeoutMinutes()).isEqualTo(300);
            assertThat(config.summaryTimeoutMinutes()).isEqualTo(200);
            assertThat(config.ghAuthTimeoutSeconds()).isEqualTo(600);
            assertThat(config.maxRetries()).isEqualTo(10);
        }

        @Test
        @DisplayName("maxRetriesが0の場合はそのまま保持される（リトライなし）")
        void maxRetriesZeroIsPreserved() {
            ExecutionConfig config = new ExecutionConfig(4, 10, 5, 5, 5, 10, 0);
            assertThat(config.maxRetries()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("レコードの等価性")
    class RecordEquality {

        @Test
        @DisplayName("同じ値を持つレコードは等価である")
        void sameValuesAreEqual() {
            ExecutionConfig config1 = new ExecutionConfig(4, 10, 5, 5, 5, 10, 2);
            ExecutionConfig config2 = new ExecutionConfig(4, 10, 5, 5, 5, 10, 2);

            assertThat(config1).isEqualTo(config2);
            assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
        }

        @Test
        @DisplayName("異なる値を持つレコードは等価でない")
        void differentValuesAreNotEqual() {
            ExecutionConfig config1 = new ExecutionConfig(4, 10, 5, 5, 5, 10, 2);
            ExecutionConfig config2 = new ExecutionConfig(8, 10, 5, 5, 5, 10, 2);

            assertThat(config1).isNotEqualTo(config2);
        }
    }
}
