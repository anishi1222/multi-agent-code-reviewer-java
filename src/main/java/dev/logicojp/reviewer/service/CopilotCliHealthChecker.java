package dev.logicojp.reviewer.service;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Singleton
public class CopilotCliHealthChecker {

    private static final Logger logger = LoggerFactory.getLogger(CopilotCliHealthChecker.class);
    private static final long DEFAULT_CLI_HEALTHCHECK_SECONDS = 10;
    private static final String CLI_HEALTHCHECK_ENV = "COPILOT_CLI_HEALTHCHECK_SECONDS";
    private static final String CLI_AUTH_CHECK_ENV = "COPILOT_CLI_AUTHCHECK_SECONDS";
    private static final long DEFAULT_CLI_AUTHCHECK_SECONDS = 15;

    public void verifyCliHealthy(String cliPath, boolean tokenProvided) throws InterruptedException {
        if (cliPath == null || cliPath.isBlank()) {
            return;
        }
        runCliCommand(versionCommand(cliPath), resolveCliHealthcheckSeconds(),
            "Copilot CLI did not respond within ",
            "Copilot CLI exited with code ",
            "Failed to execute Copilot CLI: ",
            "Ensure the CLI is installed and authenticated.");

        if (tokenProvided) {
            logger.info("GITHUB_TOKEN provided — skipping CLI auth status check");
        } else {
            runCliCommand(authStatusCommand(cliPath), resolveCliAuthcheckSeconds(),
                "Copilot CLI auth status timed out after ",
                "Copilot CLI auth status failed with code ",
                "Failed to execute Copilot CLI auth status: ",
                "Run `github-copilot auth login` to authenticate.");
        }
    }

    private List<String> versionCommand(String cliPath) {
        return List.of(cliPath, "--version");
    }

    private List<String> authStatusCommand(String cliPath) {
        return List.of(cliPath, "auth", "status");
    }

    private void runCliCommand(List<String> command, long timeoutSeconds,
                               String timeoutMessage, String exitMessage, String ioMessage,
                               String remediationMessage)
        throws InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            var drainFuture = CompletableFuture.runAsync(() -> {
                try (var in = process.getInputStream()) {
                    in.transferTo(OutputStream.nullOutputStream());
                } catch (IOException _) {
                }
            });
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                handleTimeout(process, drainFuture, timeoutSeconds, timeoutMessage, remediationMessage);
            }
            drainFuture.join();
            if (process.exitValue() != 0) {
                throw exitFailure(exitMessage, process.exitValue(), remediationMessage);
            }
        } catch (IOException e) {
            throw new CopilotCliException(ioMessage + e.getMessage(), e);
        }
    }

    private void handleTimeout(Process process,
                               CompletableFuture<Void> drainFuture,
                               long timeoutSeconds,
                               String timeoutMessage,
                               String remediationMessage) {
        process.destroyForcibly();
        drainFuture.cancel(true);
        throw new CopilotCliException(timeoutMessage + timeoutSeconds + "s. " + remediationMessage);
    }

    private CopilotCliException exitFailure(String exitMessage, int exitCode, String remediationMessage) {
        String baseMessage = exitMessage + exitCode + ". ";
        return new CopilotCliException(baseMessage + remediationMessage);
    }

    private long resolveCliHealthcheckSeconds() {
        return resolveEnvTimeout(CLI_HEALTHCHECK_ENV, DEFAULT_CLI_HEALTHCHECK_SECONDS);
    }

    private long resolveCliAuthcheckSeconds() {
        return resolveEnvTimeout(CLI_AUTH_CHECK_ENV, DEFAULT_CLI_AUTHCHECK_SECONDS);
    }

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
}
