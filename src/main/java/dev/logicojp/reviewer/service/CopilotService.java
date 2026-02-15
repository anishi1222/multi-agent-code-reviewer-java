package dev.logicojp.reviewer.service;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.CopilotClientOptions;
import dev.logicojp.reviewer.util.CliPathResolver;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Service for managing the Copilot SDK client lifecycle.
@Singleton
public class CopilotService {
    
    private static final Logger logger = LoggerFactory.getLogger(CopilotService.class);
    private static final long DEFAULT_START_TIMEOUT_SECONDS = 60;
    private static final String START_TIMEOUT_ENV = "COPILOT_START_TIMEOUT_SECONDS";
    private static final String CLI_PATH_ENV = "COPILOT_CLI_PATH";
    private static final String PATH_ENV = "PATH";
    private static final String[] CLI_CANDIDATES = {"github-copilot", "copilot"};
    private static final long DEFAULT_CLI_HEALTHCHECK_SECONDS = 10;
    private static final String CLI_HEALTHCHECK_ENV = "COPILOT_CLI_HEALTHCHECK_SECONDS";
    private static final String CLI_AUTH_CHECK_ENV = "COPILOT_CLI_AUTHCHECK_SECONDS";
    private static final long DEFAULT_CLI_AUTHCHECK_SECONDS = 15;

    private final Object lock = new Object();
    private volatile CopilotClient client;
    private volatile boolean initialized = false;
    
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
            CopilotClientOptions options = new CopilotClientOptions();
            String cliPath = resolveCliPath();
            if (cliPath != null && !cliPath.isBlank()) {
                options.setCliPath(cliPath);
            }
            boolean useToken = githubToken != null && !githubToken.isBlank() && !githubToken.equals("${GITHUB_TOKEN}");
            verifyCliHealthy(cliPath, useToken);
            if (useToken) {
                options.setGithubToken(githubToken);
                options.setUseLoggedInUser(Boolean.FALSE);
            } else {
                options.setUseLoggedInUser(Boolean.TRUE);
            }
            CopilotClient createdClient = new CopilotClient(options);
            try {
                long timeoutSeconds = resolveStartTimeoutSeconds();
                if (timeoutSeconds > 0) {
                    createdClient.start().get(timeoutSeconds, TimeUnit.SECONDS);
                } else {
                    createdClient.start().get();
                }
            } catch (ExecutionException e) {
                closeQuietly(createdClient);
                Throwable cause = e.getCause();
                if (cause instanceof TimeoutException) {
                    throw new ExecutionException(buildProtocolTimeoutMessage(), cause);
                }
                if (cause != null) {
                    throw new ExecutionException("Copilot client start failed: " + cause.getMessage(), cause);
                }
                throw e;
            } catch (TimeoutException e) {
                closeQuietly(createdClient);
                throw new ExecutionException(buildClientTimeoutMessage(), e);
            }
            client = createdClient;
            initialized = true;
            logger.info("Copilot client initialized");
        }
    }

    private long resolveStartTimeoutSeconds() {
        return resolveEnvTimeout(START_TIMEOUT_ENV, DEFAULT_START_TIMEOUT_SECONDS);
    }

    private String resolveCliPath() {
        String explicit = resolveExplicitCliPath();
        if (explicit != null) {
            return explicit;
        }
        return resolveCliPathFromSystemPath();
    }

    /// Resolves CLI path from the COPILOT_CLI_PATH environment variable.
    private String resolveExplicitCliPath() {
        String explicit = System.getenv(CLI_PATH_ENV);
        if (explicit == null || explicit.isBlank()) {
            return null;
        }
        var explicitPath = CliPathResolver.resolveExplicitExecutable(explicit, CLI_CANDIDATES);
        if (explicitPath.isPresent()) {
            return explicitPath.get().toString();
        }
        Path explicitPathValue = Path.of(explicit.trim()).toAbsolutePath().normalize();
        throw new CopilotCliException("Copilot CLI not found at " + explicitPathValue
            + ". Verify " + CLI_PATH_ENV + " or install GitHub Copilot CLI.");
    }

    /// Resolves CLI path by scanning the system PATH directories.
    private String resolveCliPathFromSystemPath() {
        String pathEnv = System.getenv(PATH_ENV);
        if (pathEnv == null || pathEnv.isBlank()) {
            throw new CopilotCliException("PATH is not set. Install GitHub Copilot CLI and/or set "
                + CLI_PATH_ENV + " to its executable path.");
        }
        var candidate = CliPathResolver.findExecutableInPath(CLI_CANDIDATES);
        if (candidate.isPresent()) {
            return candidate.get().toString();
        }

        throw new CopilotCliException("GitHub Copilot CLI not found in PATH. Install it and ensure "
            + "`github-copilot` or `copilot` is available, or set " + CLI_PATH_ENV + ".");
    }

    private void verifyCliHealthy(String cliPath, boolean tokenProvided) throws InterruptedException {
        if (cliPath == null || cliPath.isBlank()) {
            return;
        }
        runCliCommand(List.of(cliPath, "--version"), resolveCliHealthcheckSeconds(),
            "Copilot CLI did not respond within ",
            "Copilot CLI exited with code ",
            "Failed to execute Copilot CLI: ",
            "Ensure the CLI is installed and authenticated.");

        if (tokenProvided) {
            logger.info("GITHUB_TOKEN provided — skipping CLI auth status check");
        } else {
            runCliCommand(List.of(cliPath, "auth", "status"), resolveCliAuthcheckSeconds(),
                "Copilot CLI auth status timed out after ",
                "Copilot CLI auth status failed with code ",
                "Failed to execute Copilot CLI auth status: ",
                "Run `github-copilot auth login` to authenticate.");
        }
    }

    private void runCliCommand(List<String> command, long timeoutSeconds,
                               String timeoutMessage, String exitMessage, String ioMessage,
                               String remediationMessage)
        throws InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            // Drain stdout/stderr in a separate thread to prevent pipe buffer overflow
            // blocking the child process, while keeping waitFor timeout effective.
            var drainFuture = CompletableFuture.runAsync(() -> {
                try (var in = process.getInputStream()) {
                    in.transferTo(OutputStream.nullOutputStream());
                } catch (IOException _) {
                    // Ignore drain errors
                }
            });
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                drainFuture.cancel(true);
                throw new CopilotCliException(timeoutMessage + timeoutSeconds + "s. "
                    + remediationMessage);
            }
            drainFuture.join();
            if (process.exitValue() != 0) {
                String baseMessage = exitMessage + process.exitValue() + ". ";
                throw new CopilotCliException(baseMessage + remediationMessage);
            }
        } catch (IOException e) {
            throw new CopilotCliException(ioMessage + e.getMessage(), e);
        }
    }

    private long resolveCliHealthcheckSeconds() {
        return resolveEnvTimeout(CLI_HEALTHCHECK_ENV, DEFAULT_CLI_HEALTHCHECK_SECONDS);
    }

    private long resolveCliAuthcheckSeconds() {
        return resolveEnvTimeout(CLI_AUTH_CHECK_ENV, DEFAULT_CLI_AUTHCHECK_SECONDS);
    }

    /// Resolves a timeout value from an environment variable.
    /// Returns the default if the env var is missing, blank, negative, or not a valid number.
    private long resolveEnvTimeout(String envVar, long defaultValue) {
        String value = System.getenv(envVar);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed >= 0 ? parsed : defaultValue;
        } catch (NumberFormatException _) {
            logger.warn("Invalid {} value: {}. Using default.", envVar, value);
            return defaultValue;
        }
    }

    private String buildClientTimeoutMessage() {
        return "Copilot client start timed out after " + resolveStartTimeoutSeconds() + "s. "
            + "Ensure GitHub Copilot CLI is installed and authenticated, "
            + "or set " + START_TIMEOUT_ENV + " to a higher value.";
    }

    private String buildProtocolTimeoutMessage() {
        return "Copilot CLI ping timed out. Ensure GitHub Copilot CLI is installed "
            + "and authenticated (for example, run `github-copilot auth login`), "
            + "or set " + CLI_PATH_ENV + " to the correct executable.";
    }

    private void closeQuietly(CopilotClient copilotClient) {
        try {
            copilotClient.close();
        } catch (Exception e) {
            logger.debug("Failed to close Copilot client after startup failure: {}", e.getMessage());
        }
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
        synchronized (lock) {
            return initialized;
        }
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
