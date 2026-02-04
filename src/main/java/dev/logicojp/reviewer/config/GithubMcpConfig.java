package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for the GitHub MCP server connection.
 */
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
        tools = (tools == null || tools.isEmpty()) ? List.of("*") : List.copyOf(tools);
        headers = (headers == null) ? Map.of() : Map.copyOf(headers);
        authHeaderName = (authHeaderName == null || authHeaderName.isBlank())
            ? "Authorization" : authHeaderName;
        authHeaderTemplate = (authHeaderTemplate == null || authHeaderTemplate.isBlank())
            ? "Bearer {token}" : authHeaderTemplate;
    }

    public Map<String, Object> toMcpServer(String token) {
        Map<String, Object> server = new HashMap<>();
        server.put("type", type);
        server.put("url", url);
        server.put("tools", tools);

        Map<String, String> combinedHeaders = new HashMap<>(headers);
        if (authHeaderName != null && !authHeaderName.isBlank()
            && authHeaderTemplate != null && !authHeaderTemplate.isBlank()) {
            String headerValue = authHeaderTemplate
                .replace("${token}", token)
                .replace("{token}", token);
            combinedHeaders.put(authHeaderName, headerValue);
        }
        server.put("headers", combinedHeaders);

        return server;
    }
}
