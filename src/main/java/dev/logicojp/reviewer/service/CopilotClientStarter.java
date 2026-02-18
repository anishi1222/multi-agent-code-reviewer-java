package dev.logicojp.reviewer.service;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Singleton
public class CopilotClientStarter {

    interface StartableClient {
        void start(long timeoutSeconds) throws ExecutionException, TimeoutException, InterruptedException;

        void close() throws Exception;
    }

    private static final Logger logger = LoggerFactory.getLogger(CopilotClientStarter.class);

    public void start(StartableClient client,
                      long timeoutSeconds,
                      CopilotStartupErrorFormatter startupErrorFormatter)
        throws InterruptedException {
        try {
            client.start(timeoutSeconds);
        } catch (ExecutionException e) {
            closeQuietly(client);
            throw mapExecutionException(e, startupErrorFormatter);
        } catch (TimeoutException e) {
            closeQuietly(client);
            throw timeoutDuringStart(timeoutSeconds, startupErrorFormatter, e);
        }
    }

    private CopilotCliException mapExecutionException(ExecutionException e,
                                                     CopilotStartupErrorFormatter startupErrorFormatter) {
        Throwable cause = e.getCause();
        if (cause instanceof TimeoutException) {
            return new CopilotCliException(startupErrorFormatter.buildProtocolTimeoutMessage(), cause);
        }
        if (cause != null) {
            return new CopilotCliException("Copilot client start failed: " + cause.getMessage(), cause);
        }
        return new CopilotCliException("Copilot client start failed", e);
    }

    private CopilotCliException timeoutDuringStart(long timeoutSeconds,
                                                  CopilotStartupErrorFormatter startupErrorFormatter,
                                                  TimeoutException e) {
        return new CopilotCliException(startupErrorFormatter.buildClientTimeoutMessage(timeoutSeconds), e);
    }

    private void closeQuietly(StartableClient client) {
        try {
            client.close();
        } catch (Exception e) {
            logger.debug("Failed to close Copilot client after startup failure: {}", e.getMessage());
        }
    }
}
