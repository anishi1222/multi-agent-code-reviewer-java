package dev.logicojp.reviewer.service;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.CopilotClientOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Service for managing the Copilot SDK client lifecycle.
@Singleton
public class CopilotService {
    
    private static final Logger logger = LoggerFactory.getLogger(CopilotService.class);
    private static final long DEFAULT_START_TIMEOUT_SECONDS = 60;
    private static final String START_TIMEOUT_ENV = "COPILOT_START_TIMEOUT_SECONDS";
    private static final String GITHUB_TOKEN_ENV = "GITHUB_TOKEN";
    private static final String UNRESOLVED_TOKEN_PLACEHOLDER = "${GITHUB_TOKEN}";

    private final CopilotCliPathResolver cliPathResolver;
    private final CopilotCliHealthChecker cliHealthChecker;
    private final CopilotTimeoutResolver timeoutResolver;
    private final CopilotStartupErrorFormatter startupErrorFormatter;
    private final CopilotClientStarter clientStarter;
    private volatile CopilotClient client;
    private volatile String initializedToken;

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

    /// Attempts eager initialization during bean startup using GITHUB_TOKEN when available.
    /// Falls back to lazy/explicit initialization if startup prerequisites are not met.
    @PostConstruct
    void initializeAtStartup() {
        try {
            initializeOrThrow(System.getenv(GITHUB_TOKEN_ENV));
        } catch (CopilotCliException e) {
            logger.debug("Skipping eager Copilot initialization at startup: {}", e.getMessage(), e);
        }
    }
    
    /// Initializes the Copilot client, wrapping checked exceptions as RuntimeException.
    /// Convenience method for callers that cannot handle checked exceptions.
    public void initializeOrThrow(String githubToken) {
        try {
            initialize(normalizeToken(githubToken));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CopilotCliException("Failed to initialize Copilot service", e);
        }
    }

    /// Initializes the Copilot client.
    private synchronized void initialize(String githubToken) throws InterruptedException {
        if (client != null && Objects.equals(initializedToken, githubToken)) {
            return;
        }

        if (client != null) {
            closeCurrentClient();
        }

        logger.info("Initializing Copilot client...");
        CopilotClientOptions options = buildClientOptions(githubToken);
        CopilotClient createdClient = new CopilotClient(options);
        long timeoutSeconds = resolveStartTimeoutSeconds();
        startClient(createdClient, timeoutSeconds);
        client = createdClient;
        initializedToken = githubToken;
        logger.info("Copilot client initialized");
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
                             long timeoutSeconds) throws InterruptedException {
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
            && !githubToken.equals(UNRESOLVED_TOKEN_PLACEHOLDER);
    }

    private String normalizeToken(String githubToken) {
        return shouldUseToken(githubToken) ? githubToken : null;
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
        if (localClient == null) {
            throw new IllegalStateException("CopilotService not initialized. Call initialize() first.");
        }
        return localClient;
    }
    
    /// Checks if the service is initialized.
    public boolean isInitialized() {
        return client != null;
    }
    
    /// Shuts down the Copilot client.
    @PreDestroy
    public synchronized void shutdown() {
        if (client != null) {
            closeCurrentClient();
        }
    }

    private void closeCurrentClient() {
        try {
            logger.info("Shutting down Copilot client...");
            client.close();
            logger.info("Copilot client shut down");
        } catch (Exception e) {
            logger.warn("Error shutting down Copilot client: {}", e.getMessage(), e);
        } finally {
            client = null;
            initializedToken = null;
        }
    }
}
