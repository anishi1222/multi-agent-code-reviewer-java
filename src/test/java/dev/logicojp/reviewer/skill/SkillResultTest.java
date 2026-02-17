package dev.logicojp.reviewer.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SkillResult")
class SkillResultTest {

    @Nested
    @DisplayName("ファクトリメソッド")
    class FactoryMethods {

        @Test
        @DisplayName("successは成功結果を生成する")
        void successCreatesSuccessResult() {
            SkillResult result = SkillResult.success("skill-1", "content output");
            assertThat(result.success()).isTrue();
            assertThat(result.skillId()).isEqualTo("skill-1");
            assertThat(result.content()).isEqualTo("content output");
            assertThat(result.errorMessage()).isNull();
            assertThat(result.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("failureは失敗結果を生成する")
        void failureCreatesFailureResult() {
            SkillResult result = SkillResult.failure("skill-2", "timeout");
            assertThat(result.success()).isFalse();
            assertThat(result.skillId()).isEqualTo("skill-2");
            assertThat(result.content()).isNull();
            assertThat(result.errorMessage()).isEqualTo("timeout");
        }
    }

    @Nested
    @DisplayName("コンストラクタ")
    class Constructor {

        @Test
        @DisplayName("timestampがnullの場合はLocalDateTime.nowが設定される")
        void nullTimestampDefaultsToNow() {
            SkillResult result = new SkillResult("s1", true, "c", null, null);
            assertThat(result.timestamp()).isNotNull();
            assertThat(result.timestamp()).isBeforeOrEqualTo(LocalDateTime.now());
        }

        @Test
        @DisplayName("timestampが指定された場合はそのまま保持される")
        void specifiedTimestampIsPreserved() {
            LocalDateTime ts = LocalDateTime.of(2025, 1, 1, 0, 0);
            SkillResult result = new SkillResult("s1", true, "c", null, ts);
            assertThat(result.timestamp()).isEqualTo(ts);
        }
    }
}
