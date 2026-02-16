package dev.logicojp.reviewer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CopilotTimeoutResolver")
class CopilotTimeoutResolverTest {

    private final CopilotTimeoutResolver resolver = new CopilotTimeoutResolver();

    @Test
    @DisplayName("環境変数が未設定ならデフォルト値を返す")
    void returnsDefaultWhenEnvMissing() {
        long value = resolver.resolveEnvTimeout("TEST_TIMEOUT", 42L, key -> null);

        assertThat(value).isEqualTo(42L);
    }

    @Test
    @DisplayName("有効な数値文字列ならその値を返す")
    void returnsParsedValueWhenValid() {
        long value = resolver.resolveEnvTimeout("TEST_TIMEOUT", 42L, key -> " 15 ");

        assertThat(value).isEqualTo(15L);
    }

    @Test
    @DisplayName("負数はデフォルト値を返す")
    void returnsDefaultWhenNegative() {
        long value = resolver.resolveEnvTimeout("TEST_TIMEOUT", 42L, key -> "-1");

        assertThat(value).isEqualTo(42L);
    }

    @Test
    @DisplayName("数値でない値はデフォルト値を返す")
    void returnsDefaultWhenInvalidNumber() {
        long value = resolver.resolveEnvTimeout("TEST_TIMEOUT", 42L, key -> "abc");

        assertThat(value).isEqualTo(42L);
    }
}
