package dev.logicojp.reviewer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalFileConfig")
class LocalFileConfigTest {

    @Test
    @DisplayName("0以下の値はデフォルト値に正規化される")
    void invalidValuesFallbackToDefaults() {
        LocalFileConfig config = new LocalFileConfig(0, -1);

        assertThat(config.maxFileSize()).isEqualTo(LocalFileConfig.DEFAULT_MAX_FILE_SIZE);
        assertThat(config.maxTotalSize()).isEqualTo(LocalFileConfig.DEFAULT_MAX_TOTAL_SIZE);
        assertThat(config.ignoredDirectories()).isNotEmpty();
        assertThat(config.sourceExtensions()).contains("java");
    }

    @Test
    @DisplayName("正の値はそのまま保持される")
    void positiveValuesArePreserved() {
        LocalFileConfig config = new LocalFileConfig(1024, 4096);

        assertThat(config.maxFileSize()).isEqualTo(1024);
        assertThat(config.maxTotalSize()).isEqualTo(4096);
    }

    @Test
    @DisplayName("フィルタ設定を明示した場合はその値を保持する")
    void customFiltersArePreserved() {
        LocalFileConfig config = new LocalFileConfig(
            1024,
            4096,
            java.util.List.of("custom-dir"),
            java.util.List.of("abc"),
            java.util.List.of("secret-file"),
            java.util.List.of("sec")
        );

        assertThat(config.ignoredDirectories()).containsExactly("custom-dir");
        assertThat(config.sourceExtensions()).containsExactly("abc");
        assertThat(config.sensitiveFilePatterns()).containsExactly("secret-file");
        assertThat(config.sensitiveExtensions()).containsExactly("sec");
    }
}
