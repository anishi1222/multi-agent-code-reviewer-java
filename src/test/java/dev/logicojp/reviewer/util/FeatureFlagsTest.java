package dev.logicojp.reviewer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FeatureFlags")
class FeatureFlagsTest {

    @Nested
    @DisplayName("isStructuredConcurrencyEnabled")
    class StructuredConcurrency {

        @Test
        @DisplayName("設定値をそのまま返す")
        void returnsConfiguredValue() {
            FeatureFlags flags = new FeatureFlags(true, false);
            assertThat(flags.isStructuredConcurrencyEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("isStructuredConcurrencyEnabledForSkills")
    class StructuredConcurrencyForSkills {

        @Test
        @DisplayName("スキル用フラグ設定値を返す")
        void returnsSkillsFlag() {
            FeatureFlags flags = new FeatureFlags(false, true);
            assertThat(flags.isStructuredConcurrencyEnabledForSkills()).isTrue();
        }
    }
}
