package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        type = (type == null || type.isBlank()) ? "http" : type;
        url = (url == null || url.isBlank()) ? "https://api.githubcopilot.com/mcp/" : url;
        validateUrl(url);
        tools = (tools == null || tools.isEmpty()) ? List.of("*") : List.copyOf(tools);
        headers = (headers == null) ? Map.of() : Map.copyOf(headers);
        authHeaderName = (authHeaderName == null || authHeaderName.isBlank())
            ? "Authorization" : authHeaderName;
        authHeaderTemplate = (authHeaderTemplate == null || authHeaderTemplate.isBlank())
            ? "Bearer {token}" : authHeaderTemplate;
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
            Map<String, String> maskedHeaders = new HashMap<>(headers);
            for (var entry : headers.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                String normalized = key == null ? "" : key.toLowerCase();
                if (normalized.contains("authorization") || normalized.contains("token")) {
                    maskedHeaders.put(key, maskSensitiveHeaderValue(value));
                }
            }
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

    /// Builds MCP server map from a token and config, or returns null if inputs are invalid.
    /// Centralizes the null/blank check logic for reuse across the codebase.
    public static Map<String, Object> buildMcpServers(String githubToken, GithubMcpConfig config) {
        if (githubToken != null && !githubToken.isBlank() && config != null) {
            return Map.of("github", config.toMcpServer(githubToken));
        }
        return null;
    }

    /// Builds a type-safe MCP server configuration, then converts to Map for SDK compatibility.
    public Map<String, Object> toMcpServer(String token) {
        Map<String, String> combinedHeaders = new HashMap<>(headers != null ? headers : Map.of());
        if (token != null && !token.isBlank()
            && authHeaderName != null && !authHeaderName.isBlank()
            && authHeaderTemplate != null && !authHeaderTemplate.isBlank()) {
            String headerValue = authHeaderTemplate
                .replace("${token}", token)
                .replace("{token}", token);
            combinedHeaders.put(authHeaderName, headerValue);
        }

        return new McpServerConfig(type, url, tools, combinedHeaders).toMap();
    }
}
