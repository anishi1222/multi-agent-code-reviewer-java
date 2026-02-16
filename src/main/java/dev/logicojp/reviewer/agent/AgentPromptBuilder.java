package dev.logicojp.reviewer.agent;

/// Builds prompt strings from {@link AgentConfig} data.
///
/// Extracted from {@link AgentConfig} to maintain single responsibility —
/// the record is a pure data carrier while this class handles prompt construction logic.
public final class AgentPromptBuilder {

    private AgentPromptBuilder() {
        // Utility class — not instantiable
    }

    /// Builds the complete system prompt including output format instructions
    /// and output constraints (language, CoT suppression).
    public static String buildFullSystemPrompt(AgentConfig config) {
        var sb = new StringBuilder();
        if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
            sb.append(config.systemPrompt().trim()).append("\n\n");
        }

        appendFocusAreas(config, sb);
        appendOutputFormat(config, sb);

        return sb.toString();
    }

    private static void appendFocusAreas(AgentConfig config, StringBuilder sb) {
        if (config.focusAreas().isEmpty()) {
            return;
        }
        sb.append("## Focus Areas\n\n");
        sb.append(PromptTexts.FOCUS_AREAS_GUIDANCE).append("\n\n");
        for (String area : config.focusAreas()) {
            sb.append("- ").append(area).append("\n");
        }
        sb.append("\n");
    }

    private static void appendOutputFormat(AgentConfig config, StringBuilder sb) {
        if (config.outputFormat() == null || config.outputFormat().isBlank()) {
            return;
        }
        sb.append(config.outputFormat().trim()).append("\n");
    }

    /// Builds the instruction for a GitHub repository review.
    /// @param config The agent configuration
    /// @param repository The repository name (e.g. "owner/repo")
    /// @return The formatted instruction
    public static String buildInstruction(AgentConfig config, String repository) {
        if (config.instruction() == null || config.instruction().isBlank()) {
            throw new IllegalStateException("Instruction is not configured for agent: " + config.name());
        }
        return applyPlaceholders(config, repository);
    }

    /// Builds the instruction for a local directory review.
    /// Embeds the source code content directly in the prompt.
    /// @param config The agent configuration
    /// @param targetName Display name of the target directory
    /// @param sourceContent Collected source code content
    /// @return The formatted instruction with embedded source code
    public static String buildLocalInstruction(AgentConfig config, String targetName, String sourceContent) {
        return buildLocalInstructionBase(config, targetName)
            + "\n\n" + PromptTexts.LOCAL_SOURCE_HEADER + "\n\n"
            + sourceContent
            + "\n";
    }

    /// Builds only the local-review base instruction without embedding source code.
    /// This enables callers to reuse shared source-content references and avoid
    /// creating large concatenated strings per agent.
    public static String buildLocalInstructionBase(AgentConfig config, String targetName) {
        if (config.instruction() == null || config.instruction().isBlank()) {
            throw new IllegalStateException("Instruction is not configured for agent: " + config.name());
        }
        return applyPlaceholders(config, targetName);
    }

    /// Applies placeholder substitution to the instruction template.
    /// Centralizes ${repository}, ${displayName}, ${name}, ${focusAreas} replacement.
    private static String applyPlaceholders(AgentConfig config, String targetName) {
        String focusAreaText = formatFocusAreas(config);
        return config.instruction()
            .replace("${repository}", targetName)
            .replace("${displayName}", config.displayName() != null ? config.displayName() : config.name())
            .replace("${name}", config.name())
            .replace("${focusAreas}", focusAreaText);
    }

    private static String formatFocusAreas(AgentConfig config) {
        if (config.focusAreas().isEmpty()) {
            return "";
        }

        var sb = new StringBuilder();
        for (String area : config.focusAreas()) {
            sb.append("- ").append(area).append("\n");
        }
        return sb.toString();
    }
}
