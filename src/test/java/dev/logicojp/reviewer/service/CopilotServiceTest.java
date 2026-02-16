package dev.logicojp.reviewer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CopilotService")
class CopilotServiceTest {

    @Test
    @DisplayName("初期状態ではisInitializedはfalse")
    void defaultIsNotInitialized() {
        CopilotService service = new CopilotService();

        assertThat(service.isInitialized()).isFalse();
    }

    @Test
    @DisplayName("未初期化状態のshutdownは安全に実行できる")
    void shutdownWithoutInitializeIsSafe() {
        CopilotService service = new CopilotService();

        service.shutdown();

        assertThat(service.isInitialized()).isFalse();
    }
}
