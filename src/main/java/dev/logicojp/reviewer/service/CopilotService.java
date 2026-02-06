package dev.logicojp.reviewer.service;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.CopilotClientOptions;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

/**
 * Service for managing the Copilot SDK client lifecycle.
 */
@Singleton
public class CopilotService {
    
    private static final Logger logger = LoggerFactory.getLogger(CopilotService.class);
    
    private CopilotClient client;
    private boolean initialized = false;
    
    /**
     * Initializes the Copilot client.
     */
    public synchronized void initialize(String githubToken) throws ExecutionException, InterruptedException {
        if (!initialized) {
            logger.info("Initializing Copilot client...");
            CopilotClientOptions options = new CopilotClientOptions();
            String cliPath = System.getenv("COPILOT_CLI_PATH");
            if (cliPath != null && !cliPath.isBlank()) {
                options.setCliPath(cliPath);
            }
            if (githubToken != null && !githubToken.isBlank() && !githubToken.equals("${GITHUB_TOKEN}")) {
                options.setGithubToken(githubToken);
                options.setUseLoggedInUser(Boolean.FALSE);
            } else {
                options.setUseLoggedInUser(Boolean.TRUE);
            }
            client = new CopilotClient(options);
            try {
                client.start().get();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause != null) {
                    throw new ExecutionException("Copilot client start failed: " + cause.getMessage(), cause);
                }
                throw e;
            }
            initialized = true;
            logger.info("Copilot client initialized");
        }
    }
    
    /**
     * Gets the Copilot client. Must call initialize() first.
     * @return The initialized CopilotClient
     * @throws IllegalStateException if not initialized
     */
    public CopilotClient getClient() {
        if (!initialized || client == null) {
            throw new IllegalStateException("CopilotService not initialized. Call initialize() first.");
        }
        return client;
    }
    
    /**
     * Checks if the service is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Shuts down the Copilot client.
     */
    @PreDestroy
    public synchronized void shutdown() {
        if (client != null) {
            try {
                logger.info("Shutting down Copilot client...");
                client.close();
                logger.info("Copilot client shut down");
            } catch (Exception e) {
                logger.warn("Error shutting down Copilot client: {}", e.getMessage());
            } finally {
                client = null;
                initialized = false;
            }
        }
    }
}
