package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.util.CliPathResolver;
import jakarta.inject.Singleton;

import java.nio.file.Path;

/// Resolves the filesystem path to the Copilot CLI binary.
@Singleton
public class CopilotCliPathResolver {

    static final String CLI_PATH_ENV = "COPILOT_CLI_PATH";
    private static final String PATH_ENV = "PATH";
    private static final String[] CLI_CANDIDATES = {"github-copilot", "copilot"};

    public String resolveCliPath() {
        String explicit = resolveExplicitCliPath();
        if (explicit != null) {
            return explicit;
        }
        return resolveCliPathFromSystemPath();
    }

    private String resolveExplicitCliPath() {
        String explicit = env(CLI_PATH_ENV);
        if (explicit == null || explicit.isBlank()) {
            return null;
        }
        var explicitPath = CliPathResolver.resolveExplicitExecutable(explicit, CLI_CANDIDATES);
        if (explicitPath.isPresent()) {
            return explicitPath.get().toString();
        }
        throw explicitPathNotFound(explicit);
    }

    private String resolveCliPathFromSystemPath() {
        String pathEnv = env(PATH_ENV);
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

    private String env(String key) {
        return System.getenv(key);
    }

    private CopilotCliException explicitPathNotFound(String explicit) {
        Path explicitPathValue = Path.of(explicit.trim()).toAbsolutePath().normalize();
        return new CopilotCliException("Copilot CLI not found at " + explicitPathValue
            + ". Verify " + CLI_PATH_ENV + " or install GitHub Copilot CLI.");
    }
}
