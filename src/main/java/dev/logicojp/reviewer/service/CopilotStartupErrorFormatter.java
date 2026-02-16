package dev.logicojp.reviewer.service;

import jakarta.inject.Singleton;

@Singleton
public class CopilotStartupErrorFormatter {

    private static final String CLI_INSTALL_AUTH_GUIDANCE =
        "Ensure GitHub Copilot CLI is installed and authenticated";
    private static final String CLIENT_TIMEOUT_ENV_GUIDANCE =
        "or set COPILOT_START_TIMEOUT_SECONDS to a higher value.";
    private static final String PROTOCOL_AUTH_GUIDANCE =
        "and authenticated (for example, run `github-copilot auth login`)";

    public String buildClientTimeoutMessage(long timeoutSeconds) {
        return clientTimeoutPrefix(timeoutSeconds)
            + CLI_INSTALL_AUTH_GUIDANCE + ", "
            + CLIENT_TIMEOUT_ENV_GUIDANCE;
    }

    public String buildProtocolTimeoutMessage() {
        return "Copilot CLI ping timed out. "
            + CLI_INSTALL_AUTH_GUIDANCE + " "
            + PROTOCOL_AUTH_GUIDANCE + ", "
            + "or set " + CopilotCliPathResolver.CLI_PATH_ENV + " to the correct executable.";
    }

    private String clientTimeoutPrefix(long timeoutSeconds) {
        return "Copilot client start timed out after " + timeoutSeconds + "s. ";
    }
}
