package dev.logicojp.reviewer.service;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.CopilotClientOptions;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Service for managing the Copilot SDK client lifecycle.
@Singleton
public class CopilotService {
    
    private static final Logger logger = LoggerFactory.getLogger(CopilotService.class);
    private static final long DEFAULT_START_TIMEOUT_SECONDS = 60;
    private static final String START_TIMEOUT_ENV = "COPILOT_START_TIMEOUT_SECONDS";

    private final Object lock = new Object();
    private final CopilotCliPathResolver cliPathResolver;
    private final CopilotCliHealthChecker cliHealthChecker;
    private final CopilotTimeoutResolver timeoutResolver;
    private final CopilotStartupErrorFormatter startupErrorFormatter;
    private final CopilotClientStarter clientStarter;
    private volatile CopilotClient client;
    private volatile boolean initialized = false;

    public CopilotService() {
        this(
            new CopilotCliPathResolver(),
            new CopilotCliHealthChecker(),
            new CopilotTimeoutResolver(),
            new CopilotStartupErrorFormatter(),
            new CopilotClientStarter()
        );
    }

    @Inject
    public CopilotService(CopilotCliPathResolver cliPathResolver,
                          CopilotCliHealthChecker cliHealthChecker,
                          CopilotTimeoutResolver timeoutResolver,
                          CopilotStartupErrorFormatter startupErrorFormatter,
                          CopilotClientStarter clientStarter) {
        this.cliPathResolver = cliPathResolver;
        this.cliHealthChecker = cliHealthChecker;
        this.timeoutResolver = timeoutResolver;
        this.startupErrorFormatter = startupErrorFormatter;
        this.clientStarter = clientStarter;
    }
    
    /// Initializes the Copilot client, wrapping checked exceptions as RuntimeException.
    /// Convenience method for callers that cannot handle checked exceptions.
    public void initializeOrThrow(String githubToken) {
        try {
            initialize(githubToken);
        } catch (ExecutionException e) {
            throw new CopilotCliException("Failed to initialize Copilot service", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CopilotCliException("Failed to initialize Copilot service", e);
        }
    }

    /// Initializes the Copilot client.
    public void initialize(String githubToken) throws ExecutionException, InterruptedException {
        if (initialized) {
            return;
        }
        synchronized (lock) {
            if (initialized) {
                return;
            }
            logger.info("Initializing Copilot client...");
            CopilotClientOptions options = buildClientOptions(githubToken);
            CopilotClient createdClient = new CopilotClient(options);
            long timeoutSeconds = resolveStartTimeoutSeconds();
            startClient(createdClient, timeoutSeconds);
            client = createdClient;
            initialized = true;
            logger.info("Copilot client initialized");
        }
    }

    private CopilotClientOptions buildClientOptions(String githubToken) throws InterruptedException {
        CopilotClientOptions options = new CopilotClientOptions();
        String cliPath = cliPathResolver.resolveCliPath();
        applyCliPathOption(options, cliPath);
        boolean useToken = shouldUseToken(githubToken);
        cliHealthChecker.verifyCliHealthy(cliPath, useToken);
        applyAuthOptions(options, githubToken, useToken);
        return options;
    }

    private void startClient(CopilotClient createdClient,
                             long timeoutSeconds) throws ExecutionException, InterruptedException {
        clientStarter.start(new CopilotClientStarter.StartableClient() {
            @Override
            public void start(long timeoutSeconds) throws ExecutionException, TimeoutException, InterruptedException {
                if (timeoutSeconds > 0) {
                    createdClient.start().get(timeoutSeconds, TimeUnit.SECONDS);
                } else {
                    createdClient.start().get();
                }
            }

            @Override
            public void close() {
                createdClient.close();
            }
        }, timeoutSeconds, startupErrorFormatter);
    }

    private long resolveStartTimeoutSeconds() {
        return timeoutResolver.resolveEnvTimeout(START_TIMEOUT_ENV, DEFAULT_START_TIMEOUT_SECONDS);
    }

    private void applyCliPathOption(CopilotClientOptions options, String cliPath) {
        if (cliPath != null && !cliPath.isBlank()) {
            options.setCliPath(cliPath);
        }
    }

    private boolean shouldUseToken(String githubToken) {
        return githubToken != null
            && !githubToken.isBlank()
            && !githubToken.equals("${GITHUB_TOKEN}");
    }

    private void applyAuthOptions(CopilotClientOptions options, String githubToken, boolean useToken) {
        if (useToken) {
            options.setGithubToken(githubToken);
            options.setUseLoggedInUser(Boolean.FALSE);
            return;
        }
        options.setUseLoggedInUser(Boolean.TRUE);
    }

    /// Gets the Copilot client. Must call initialize() first.
    /// @return The initialized CopilotClient
    /// @throws IllegalStateException if not initialized
    public CopilotClient getClient() {
        CopilotClient localClient = client;
        if (!initialized || localClient == null) {
            throw new IllegalStateException("CopilotService not initialized. Call initialize() first.");
        }
        return localClient;
    }
    
    /// Checks if the service is initialized.
    public boolean isInitialized() {
        return initialized;
    }
    
    /// Shuts down the Copilot client.
    @PreDestroy
    public void shutdown() {
        synchronized (lock) {
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
}
