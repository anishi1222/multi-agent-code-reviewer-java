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
    }

    @Test
    @DisplayName("正の値はそのまま保持される")
    void positiveValuesArePreserved() {
        LocalFileConfig config = new LocalFileConfig(1024, 4096);

        assertThat(config.maxFileSize()).isEqualTo(1024);
        assertThat(config.maxTotalSize()).isEqualTo(4096);
    }
}
