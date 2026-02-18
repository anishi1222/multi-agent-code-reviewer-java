package dev.logicojp.reviewer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CopilotClientStarter")
class CopilotClientStarterTest {

    private final CopilotClientStarter starter = new CopilotClientStarter();
    private final CopilotStartupErrorFormatter formatter = new CopilotStartupErrorFormatter();

    @Test
    @DisplayName("開始成功時は例外を投げない")
    void startsSuccessfully() throws Exception {
        var client = new StubStartableClient();

        starter.start(client, 60, formatter);

        assertThat(client.started).isTrue();
        assertThat(client.closed.get()).isFalse();
    }

    @Test
    @DisplayName("TimeoutException時はCopilotCliExceptionへ変換しcloseする")
    void wrapsTimeoutExceptionAndCloses() {
        var client = new StubStartableClient();
        client.timeoutException = new TimeoutException("timeout");

        assertThatThrownBy(() -> starter.start(client, 30, formatter))
            .isInstanceOf(CopilotCliException.class)
            .hasMessageContaining("timed out after 30s");
        assertThat(client.closed.get()).isTrue();
    }

    @Test
    @DisplayName("ExecutionExceptionのcauseがTimeoutExceptionならprotocolメッセージに変換")
    void wrapsExecutionExceptionWithTimeoutCause() {
        var client = new StubStartableClient();
        client.executionException = new ExecutionException(new TimeoutException("protocol timeout"));

        assertThatThrownBy(() -> starter.start(client, 10, formatter))
            .isInstanceOf(CopilotCliException.class)
            .hasMessageContaining("Copilot CLI ping timed out");
        assertThat(client.closed.get()).isTrue();
    }

    @Test
    @DisplayName("ExecutionExceptionのcauseが通常例外ならstart失敗メッセージに変換")
    void wrapsExecutionExceptionWithCauseMessage() {
        var client = new StubStartableClient();
        client.executionException = new ExecutionException(new IllegalStateException("boom"));

        assertThatThrownBy(() -> starter.start(client, 10, formatter))
            .isInstanceOf(CopilotCliException.class)
            .hasMessageContaining("Copilot client start failed: boom");
        assertThat(client.closed.get()).isTrue();
    }

    private static class StubStartableClient implements CopilotClientStarter.StartableClient {
        boolean started;
        AtomicBoolean closed = new AtomicBoolean(false);
        TimeoutException timeoutException;
        ExecutionException executionException;

        @Override
        public void start(long timeoutSeconds) throws ExecutionException, TimeoutException {
            started = true;
            if (executionException != null) {
                throw executionException;
            }
            if (timeoutException != null) {
                throw timeoutException;
            }
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
