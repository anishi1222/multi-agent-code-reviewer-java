package dev.logicojp.reviewer.service;

import com.github.copilot.sdk.CopilotClient;
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
    public synchronized void initialize() throws ExecutionException, InterruptedException {
        if (!initialized) {
            logger.info("Initializing Copilot client...");
            client = new CopilotClient();
            client.start().get();
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
