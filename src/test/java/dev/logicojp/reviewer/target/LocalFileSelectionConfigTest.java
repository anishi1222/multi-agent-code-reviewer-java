package dev.logicojp.reviewer.target;

import dev.logicojp.reviewer.config.LocalFileConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalFileSelectionConfig")
class LocalFileSelectionConfigTest {

    @Test
    @DisplayName("設定値を正規化して小文字セットへ変換する")
    void normalizesConfiguredValues() {
        LocalFileConfig config = new LocalFileConfig(
            123,
            456,
            List.of(".Git", "build", "  "),
            List.of("JAVA", "Ts"),
            List.of("Secret", ".ENV"),
            List.of("PEM", "KEY")
        );

        LocalFileSelectionConfig normalized = LocalFileSelectionConfig.from(config);

        assertThat(normalized.maxFileSize()).isEqualTo(123);
        assertThat(normalized.maxTotalSize()).isEqualTo(456);
        assertThat(normalized.ignoredDirectories()).containsExactlyInAnyOrder(".git", "build");
        assertThat(normalized.sourceExtensions()).containsExactlyInAnyOrder("java", "ts");
        assertThat(normalized.sensitiveFilePatterns()).containsExactlyInAnyOrder("secret", ".env");
        assertThat(normalized.sensitiveExtensions()).containsExactlyInAnyOrder("pem", "key");
    }

    @Test
    @DisplayName("デフォルト設定でも空でない正規化セットを生成する")
    void keepsDefaultsFromLocalFileConfig() {
        LocalFileSelectionConfig normalized = LocalFileSelectionConfig.from(new LocalFileConfig());

        assertThat(normalized.ignoredDirectories()).isNotEmpty();
        assertThat(normalized.sourceExtensions()).isNotEmpty();
        assertThat(normalized.sensitiveFilePatterns()).isNotEmpty();
        assertThat(normalized.sensitiveExtensions()).isNotEmpty();
    }
}
