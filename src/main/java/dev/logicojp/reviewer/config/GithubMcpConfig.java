package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

import java.net.URI;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// Configuration for the GitHub MCP server connection.
@ConfigurationProperties("reviewer.mcp.github")
public record GithubMcpConfig(
    String type,
    String url,
    List<String> tools,
    Map<String, String> headers,
    String authHeaderName,
    @Nullable String authHeaderTemplate
) {

    public GithubMcpConfig {
        type = ConfigDefaults.defaultIfBlank(type, "http");
        url = ConfigDefaults.defaultIfBlank(url, "https://api.githubcopilot.com/mcp/");
        validateUrl(url);
        tools = (tools == null || tools.isEmpty()) ? List.of("*") : List.copyOf(tools);
        headers = (headers == null) ? Map.of() : Map.copyOf(headers);
        authHeaderName = ConfigDefaults.defaultIfBlank(authHeaderName, "Authorization");
        authHeaderTemplate = ConfigDefaults.defaultIfBlank(authHeaderTemplate, "Bearer {token}");
    }

    private static void validateUrl(String url) {
        URI parsed = URI.create(url);
        String scheme = parsed.getScheme();
        if (scheme == null || !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("GitHub MCP URL must use HTTPS: " + url);
        }
        if (parsed.getHost() == null || parsed.getHost().isBlank()) {
            throw new IllegalArgumentException("GitHub MCP URL must include host: " + url);
        }
    }

    /// Type-safe intermediate representation of MCP server configuration.
    /// Provides compile-time safety within the application; converted to
    /// {@code Map<String, Object>} only at the SDK boundary.
    public record McpServerConfig(String type, String url, List<String> tools, Map<String, String> headers) {
        /// Converts to an immutable Map for SDK compatibility.
        public Map<String, Object> toMap() {
            return Map.of(
                "type", type,
                "url", url,
                "tools", tools,
                "headers", headers
            );
        }

        @Override
        public String toString() {
            // Mask Authorization header values to prevent token leakage in logs
            Map<String, String> maskedHeaders = headers.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    entry -> {
                        String normalized = entry.getKey() == null ? "" : entry.getKey().toLowerCase();
                        if (normalized.contains("authorization") || normalized.contains("token")) {
                            return maskSensitiveHeaderValue(entry.getValue());
                        }
                        return entry.getValue();
                    }
                ));
            return "McpServerConfig{type='%s', url='%s', tools=%s, headers=%s}"
                .formatted(type, url, tools, maskedHeaders);
        }

        private static String maskSensitiveHeaderValue(String value) {
            if (value == null || value.isBlank()) {
                return "***";
            }
            int spaceIndex = value.indexOf(' ');
            if (spaceIndex > 0) {
                String prefix = value.substring(0, spaceIndex);
                return prefix + " ***";
            }
            return "***";
        }
    }

    /// Builds MCP server map from a token and config.
    /// Returns {@link Optional#empty()} when inputs are invalid.
    public static Optional<Map<String, Object>> buildMcpServers(String githubToken, GithubMcpConfig config) {
        if (canBuildMcpServers(githubToken, config)) {
            return Optional.of(Map.of("github", config.toMcpServer(githubToken)));
        }
        return Optional.empty();
    }

    private static boolean canBuildMcpServers(String githubToken, GithubMcpConfig config) {
        return githubToken != null && !githubToken.isBlank() && config != null;
    }

    /// Builds a type-safe MCP server configuration, then converts to Map for SDK compatibility.
    /// The returned Map wraps toString() to mask sensitive headers, preventing token leakage
    /// via SDK/framework debug logging.
    /// Map wrapper that delegates toString() to McpServerConfig for token masking.
    /// Prevents token leakage via SDK/framework debug logging of Map.toString().
    private static final class MaskedToStringMap extends AbstractMap<String, Object> {
        private final Map<String, Object> delegate;
        private final String maskedString;

        MaskedToStringMap(Map<String, Object> delegate, String maskedString) {
            this.delegate = Map.copyOf(delegate);
            this.maskedString = maskedString;
        }

        @Override public Set<Entry<String, Object>> entrySet() { return delegate.entrySet(); }
        @Override public String toString() { return maskedString; }
    }

    public Map<String, Object> toMcpServer(String token) {
        Map<String, String> combinedHeaders = new HashMap<>(headers != null ? headers : Map.of());
        applyAuthHeader(token, combinedHeaders);
        McpServerConfig config = new McpServerConfig(type, url, tools, combinedHeaders);
        return Collections.unmodifiableMap(new MaskedToStringMap(config.toMap(), config.toString()));
    }

    private void applyAuthHeader(String token, Map<String, String> combinedHeaders) {
        if (token == null || token.isBlank()) {
            return;
        }
        String headerValue = authHeaderTemplate
            .replace("{token}", token);
        combinedHeaders.put(authHeaderName, headerValue);
    }
}
