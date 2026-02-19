package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

import java.net.URI;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
        public McpServerConfig {
            tools = tools != null ? List.copyOf(tools) : List.of();
            headers = headers != null ? Map.copyOf(headers) : Map.of();
        }

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
                .collect(Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    entry -> {
                        String normalized = entry.getKey() == null ? "" : entry.getKey().toLowerCase(java.util.Locale.ROOT);
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

    private static boolean isSensitiveHeaderName(String headerName) {
        String normalized = headerName == null ? "" : headerName.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("authorization") || normalized.contains("token");
    }

    private static String maskHeaderValue(String headerName, String value) {
        return isSensitiveHeaderName(headerName)
            ? McpServerConfig.maskSensitiveHeaderValue(value)
            : value;
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

        /// @param delegate     source map (defensive copy via {@link Map#copyOf}; {@code put()} correctly throws {@link UnsupportedOperationException})
        /// @param maskedString the string returned by {@link #toString()} to mask sensitive headers
        MaskedToStringMap(Map<String, Object> delegate, String maskedString) {
            // delegate is already immutable (Map.copyOf); put() correctly throws UnsupportedOperationException
            this.delegate = Map.copyOf(delegate);
            this.maskedString = maskedString;
        }

        @Override public Set<Entry<String, Object>> entrySet() { return delegate.entrySet(); }
        @Override public Object get(Object key) { return delegate.get(key); }
        @Override public int size() { return delegate.size(); }
        @Override public boolean isEmpty() { return delegate.isEmpty(); }
        @Override public boolean containsKey(Object key) { return delegate.containsKey(key); }
        @Override public boolean containsValue(Object value) { return delegate.containsValue(value); }
        @Override public Set<String> keySet() { return delegate.keySet(); }
        @Override public Collection<Object> values() { return delegate.values(); }
        @Override public String toString() { return maskedString; }
    }

    /// Header map that keeps raw values for SDK consumption while masking
    /// string representations used by debug logging paths.
    private static final class MaskedHeadersMap extends AbstractMap<String, String> {
        private final Map<String, String> delegate;

        MaskedHeadersMap(Map<String, String> delegate) {
            this.delegate = Map.copyOf(delegate);
        }

        @Override
        public Set<Map.Entry<String, String>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public Iterator<Map.Entry<String, String>> iterator() {
                    Iterator<Map.Entry<String, String>> iterator = delegate.entrySet().iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return iterator.hasNext();
                        }

                        @Override
                        public Map.Entry<String, String> next() {
                            return new MaskedHeaderEntry(iterator.next());
                        }
                    };
                }

                @Override
                public int size() {
                    return delegate.size();
                }

                @Override
                public String toString() {
                    return buildMaskedMapString(delegate);
                }
            };
        }

        @Override
        public String get(Object key) {
            return delegate.get(key);
        }

        @Override
        public Set<String> keySet() {
            return delegate.keySet();
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return delegate.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return delegate.containsValue(value);
        }

        @Override
        public Collection<String> values() {
            var values = delegate.values();
            return new Collection<>() {
                @Override
                public int size() {
                    return values.size();
                }

                @Override
                public boolean isEmpty() {
                    return values.isEmpty();
                }

                @Override
                public boolean contains(Object o) {
                    return values.contains(o);
                }

                @Override
                public Iterator<String> iterator() {
                    return values.iterator();
                }

                @Override
                public Object[] toArray() {
                    return values.toArray();
                }

                @Override
                public <T> T[] toArray(T[] a) {
                    return values.toArray(a);
                }

                @Override
                public boolean add(String value) {
                    throw new UnsupportedOperationException("MaskedHeadersMap values are immutable");
                }

                @Override
                public boolean remove(Object value) {
                    throw new UnsupportedOperationException("MaskedHeadersMap values are immutable");
                }

                @Override
                public boolean containsAll(Collection<?> c) {
                    return values.containsAll(c);
                }

                @Override
                public boolean addAll(Collection<? extends String> valuesToAdd) {
                    throw new UnsupportedOperationException("MaskedHeadersMap values are immutable");
                }

                @Override
                public boolean removeAll(Collection<?> valuesToRemove) {
                    throw new UnsupportedOperationException("MaskedHeadersMap values are immutable");
                }

                @Override
                public boolean retainAll(Collection<?> valuesToRetain) {
                    throw new UnsupportedOperationException("MaskedHeadersMap values are immutable");
                }

                @Override
                public void clear() {
                    throw new UnsupportedOperationException("MaskedHeadersMap values are immutable");
                }

                @Override
                public String toString() {
                    List<String> maskedValues = new ArrayList<>(delegate.size());
                    for (Entry<String, String> entry : delegate.entrySet()) {
                        maskedValues.add(maskHeaderValue(entry.getKey(), entry.getValue()));
                    }
                    return maskedValues.toString();
                }
            };
        }

        @Override
        public String toString() {
            return buildMaskedMapString(delegate);
        }
    }

    private static final class MaskedHeaderEntry implements Map.Entry<String, String> {
        private final Map.Entry<String, String> delegate;

        MaskedHeaderEntry(Map.Entry<String, String> delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getKey() {
            return delegate.getKey();
        }

        @Override
        public String getValue() {
            return delegate.getValue();
        }

        @Override
        public String setValue(String value) {
            throw new UnsupportedOperationException("MaskedHeadersMap entries are immutable");
        }

        @Override
        public String toString() {
            return getKey() + "=" + maskHeaderValue(getKey(), getValue());
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (!(other instanceof Map.Entry<?, ?> entry)) {
                return false;
            }
            return java.util.Objects.equals(getKey(), entry.getKey())
                && java.util.Objects.equals(getValue(), entry.getValue());
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hashCode(getKey()) ^ java.util.Objects.hashCode(getValue());
        }
    }

    private static String buildMaskedMapString(Map<String, String> headers) {
        return headers.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> maskHeaderValue(entry.getKey(), entry.getValue())
            ))
            .toString();
    }

    public Map<String, Object> toMcpServer(String token) {
        Map<String, String> combinedHeaders = new HashMap<>(headers != null ? headers : Map.of());
        applyAuthHeader(token, combinedHeaders);
        Map<String, String> immutableHeaders = Map.copyOf(combinedHeaders);
        McpServerConfig config = new McpServerConfig(type, url, tools, immutableHeaders);
        Map<String, Object> rawMap = config.toMap();
        Map<String, Object> protectedMap = new HashMap<>(rawMap);
        protectedMap.put("headers", new MaskedHeadersMap(immutableHeaders));
        return new MaskedToStringMap(protectedMap, config.toString());
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
