package dev.logicojp.reviewer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Resolves a GitHub token from CLI options, environment, or gh auth.
 */
public final class GitHubTokenResolver {

    private static final Logger logger = LoggerFactory.getLogger(GitHubTokenResolver.class);
    private static final String PLACEHOLDER = "${GITHUB_TOKEN}";

    public Optional<String> resolve(String providedToken) {
        String normalized = normalizeToken(providedToken);
        if (normalized != null) {
            return Optional.of(normalized);
        }

        return resolveFromGhAuth();
    }

    private String normalizeToken(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty() || PLACEHOLDER.equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private Optional<String> resolveFromGhAuth() {
        ProcessBuilder builder = new ProcessBuilder("gh", "auth", "token", "-h", "github.com");
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    logger.warn("gh auth token failed with exit code {}", exitCode);
                    return Optional.empty();
                }
                if (line == null || line.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(line.trim());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while resolving token from gh auth", e);
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("Failed to resolve token from gh auth", e);
            return Optional.empty();
        }
    }
}