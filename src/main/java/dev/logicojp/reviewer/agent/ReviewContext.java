package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import com.github.copilot.sdk.CopilotClient;
import io.micronaut.core.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;

/// Shared, immutable context for executing review agents.
///
/// Groups the common configuration that every {@link ReviewAgent} needs,
/// reducing the constructor parameter count from 10 to 2
/// ({@code AgentConfig} + {@code ReviewContext}).
///
/// @param client              The Copilot SDK client
/// @param timeoutMinutes      Per-attempt timeout in minutes
/// @param idleTimeoutMinutes  Idle timeout in minutes (no-event threshold)
/// @param customInstructions  Custom instructions to inject into agent prompts
/// @param reasoningEffort     Reasoning effort level for reasoning models (nullable)
/// @param maxRetries          Maximum number of retries on failure
/// @param outputConstraints   Output constraints template content (nullable)
/// @param cachedMcpServers    Pre-built MCP server configuration map (nullable, cached for reuse)
/// @param cachedSourceContent Pre-computed source content for local reviews (nullable, shared across agents)
/// @param localFileConfig     Local file collection configuration (used by fallback path)
/// @param sharedScheduler     Shared ScheduledExecutorService for idle-timeout scheduling
public record ReviewContext(
    CopilotClient client,
    long timeoutMinutes,
    long idleTimeoutMinutes,
    List<CustomInstruction> customInstructions,
    @Nullable String reasoningEffort,
    int maxRetries,
    @Nullable String outputConstraints,
    @Nullable Map<String, Object> cachedMcpServers,
    @Nullable String cachedSourceContent,
    LocalFileConfig localFileConfig,
    ScheduledExecutorService sharedScheduler
) {

    public ReviewContext {
        Objects.requireNonNull(client, "client must not be null");
        Objects.requireNonNull(sharedScheduler, "sharedScheduler must not be null");
        customInstructions = customInstructions != null ? List.copyOf(customInstructions) : List.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private CopilotClient client;
        private long timeoutMinutes;
        private long idleTimeoutMinutes;
        private List<CustomInstruction> customInstructions;
        private String reasoningEffort;
        private int maxRetries;
        private String outputConstraints;
        private Map<String, Object> cachedMcpServers;
        private String cachedSourceContent;
        private LocalFileConfig localFileConfig;
        private ScheduledExecutorService sharedScheduler;

        public Builder client(CopilotClient client) {
            this.client = client;
            return this;
        }

        public Builder timeoutMinutes(long timeoutMinutes) {
            this.timeoutMinutes = timeoutMinutes;
            return this;
        }

        public Builder idleTimeoutMinutes(long idleTimeoutMinutes) {
            this.idleTimeoutMinutes = idleTimeoutMinutes;
            return this;
        }

        public Builder customInstructions(List<CustomInstruction> customInstructions) {
            this.customInstructions = customInstructions;
            return this;
        }

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder outputConstraints(String outputConstraints) {
            this.outputConstraints = outputConstraints;
            return this;
        }

        public Builder cachedMcpServers(Map<String, Object> cachedMcpServers) {
            this.cachedMcpServers = cachedMcpServers;
            return this;
        }

        public Builder cachedSourceContent(String cachedSourceContent) {
            this.cachedSourceContent = cachedSourceContent;
            return this;
        }

        public Builder localFileConfig(LocalFileConfig localFileConfig) {
            this.localFileConfig = localFileConfig;
            return this;
        }

        public Builder sharedScheduler(ScheduledExecutorService sharedScheduler) {
            this.sharedScheduler = sharedScheduler;
            return this;
        }

        public ReviewContext build() {
            Objects.requireNonNull(client, "client must not be null");
            Objects.requireNonNull(sharedScheduler, "sharedScheduler must not be null");
            requirePositive(timeoutMinutes, "timeoutMinutes");
            requirePositive(idleTimeoutMinutes, "idleTimeoutMinutes");

            LocalFileConfig effectiveLocalFileConfig = resolveLocalFileConfig();

            return new ReviewContext(
                client,
                timeoutMinutes,
                idleTimeoutMinutes,
                customInstructions,
                reasoningEffort,
                maxRetries,
                outputConstraints,
                cachedMcpServers,
                cachedSourceContent,
                effectiveLocalFileConfig,
                sharedScheduler
            );
        }

        private void requirePositive(long value, String fieldName) {
            if (value <= 0) {
                throw new IllegalArgumentException(fieldName + " must be positive");
            }
        }

        private LocalFileConfig resolveLocalFileConfig() {
            if (localFileConfig != null) {
                return localFileConfig;
            }
            return new LocalFileConfig();
        }
    }

    @Override
    public String toString() {
        return "ReviewContext{cachedMcpServers=%s, timeoutMinutes=%d, idleTimeoutMinutes=%d, maxRetries=%d, localFileConfig=%s}"
            .formatted(cachedMcpServers != null ? "(configured)" : "(null)", timeoutMinutes, idleTimeoutMinutes, maxRetries,
                localFileConfig != null ? "(configured)" : "(null)");
    }
}
