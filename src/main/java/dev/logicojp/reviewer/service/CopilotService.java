package dev.logicojp.reviewer.service;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.CopilotClientOptions;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    private CopilotClient client;
    private boolean initialized = false;
    
    /// Initializes the Copilot client.
    public synchronized void initialize(String githubToken) throws ExecutionException, InterruptedException {
        if (!initialized) {
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
            client = new CopilotClient(options);
            try {
                long timeoutSeconds = resolveStartTimeoutSeconds();
                if (timeoutSeconds > 0) {
                    client.start().get(timeoutSeconds, TimeUnit.SECONDS);
                } else {
                    client.start().get();
                }
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof TimeoutException) {
                    throw new ExecutionException(buildProtocolTimeoutMessage(), cause);
                }
                if (cause != null) {
                    throw new ExecutionException("Copilot client start failed: " + cause.getMessage(), cause);
                }
                throw e;
            } catch (TimeoutException e) {
                throw new ExecutionException(buildClientTimeoutMessage(), e);
            }
            initialized = true;
            logger.info("Copilot client initialized");
        }
    }

    private long resolveStartTimeoutSeconds() {
        return resolveEnvTimeout(START_TIMEOUT_ENV, DEFAULT_START_TIMEOUT_SECONDS);
    }

    private String resolveCliPath() throws ExecutionException {
        String explicit = System.getenv(CLI_PATH_ENV);
        if (explicit != null && !explicit.isBlank()) {
            Path explicitPath = Path.of(explicit.trim()).toAbsolutePath().normalize();
            if (Files.isExecutable(explicitPath)) {
                // Validate that the binary name matches expected Copilot CLI candidates
                String fileName = explicitPath.getFileName().toString();
                boolean validName = java.util.Arrays.stream(CLI_CANDIDATES)
                    .anyMatch(candidate -> fileName.equals(candidate) || fileName.startsWith(candidate));
                if (!validName) {
                    logger.warn("CLI path {} does not match expected Copilot CLI binary names ({})",
                        explicitPath, String.join(", ", CLI_CANDIDATES));
                }
                return explicitPath.toString();
            }
            throw new ExecutionException("Copilot CLI not found at " + explicitPath
                + ". Verify " + CLI_PATH_ENV + " or install GitHub Copilot CLI.", null);
        }

        String pathEnv = System.getenv(PATH_ENV);
        if (pathEnv == null || pathEnv.isBlank()) {
            throw new ExecutionException("PATH is not set. Install GitHub Copilot CLI and/or set "
                + CLI_PATH_ENV + " to its executable path.", null);
        }

        List<Path> candidates = new ArrayList<>();
        for (String entry : pathEnv.split(java.io.File.pathSeparator)) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Path base = Path.of(entry.trim());
            for (String name : CLI_CANDIDATES) {
                candidates.add(base.resolve(name));
            }
        }

        for (Path candidate : candidates) {
            if (Files.isExecutable(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
        }

        throw new ExecutionException("GitHub Copilot CLI not found in PATH. Install it and ensure "
            + "`github-copilot` or `copilot` is available, or set " + CLI_PATH_ENV + ".", null);
    }

    private void verifyCliHealthy(String cliPath, boolean tokenProvided) throws ExecutionException, InterruptedException {
        if (cliPath == null || cliPath.isBlank()) {
            return;
        }
        runCliCommand(List.of(cliPath, "--version"), resolveCliHealthcheckSeconds(),
            "Copilot CLI did not respond within ",
            "Copilot CLI exited with code ",
            "Failed to execute Copilot CLI: ");

        if (tokenProvided) {
            logger.info("GITHUB_TOKEN provided — skipping CLI auth status check");
        } else {
            runCliCommand(List.of(cliPath, "auth", "status"), resolveCliAuthcheckSeconds(),
                "Copilot CLI auth status timed out after ",
                "Copilot CLI auth status failed with code ",
                "Failed to execute Copilot CLI auth status: ");
        }
    }

    private void runCliCommand(List<String> command, long timeoutSeconds,
                               String timeoutMessage, String exitMessage, String ioMessage)
        throws ExecutionException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            // Drain stdout/stderr to prevent pipe buffer overflow blocking the child process
            try (var in = process.getInputStream()) {
                in.transferTo(java.io.OutputStream.nullOutputStream());
            }
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ExecutionException(timeoutMessage + timeoutSeconds + "s. "
                    + "Ensure the CLI is installed and authenticated.", null);
            }
            if (process.exitValue() != 0) {
                String baseMessage = exitMessage + process.exitValue() + ". ";
                if (command.size() >= 3 && "auth".equals(command.get(1)) && "status".equals(command.get(2))) {
                    throw new ExecutionException(baseMessage
                        + "Run `github-copilot auth login` to authenticate.", null);
                }
                throw new ExecutionException(baseMessage
                    + "Ensure the CLI is installed and authenticated.", null);
            }
        } catch (java.io.IOException e) {
            throw new ExecutionException(ioMessage + e.getMessage(), e);
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
    
    /// Gets the Copilot client. Must call initialize() first.
    /// @return The initialized CopilotClient
    /// @throws IllegalStateException if not initialized
    public CopilotClient getClient() {
        if (!initialized || client == null) {
            throw new IllegalStateException("CopilotService not initialized. Call initialize() first.");
        }
        return client;
    }
    
    /// Checks if the service is initialized.
    public boolean isInitialized() {
        return initialized;
    }
    
    /// Shuts down the Copilot client.
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
