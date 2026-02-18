package dev.logicojp.reviewer.skill;

import dev.logicojp.reviewer.instruction.CustomInstructionSafetyValidator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Defines a skill that an agent can perform.
/// Skills are discrete capabilities that can be invoked by name.
public record SkillDefinition(
    String id,
    String name,
    String description,
    String prompt,
    List<SkillParameter> parameters,
    Map<String, String> metadata
) {
    public SkillDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Skill id is required");
        }
        if (name == null || name.isBlank()) {
            name = id;
        }
        if (description == null) {
            description = "";
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Skill prompt is required");
        }
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /// Creates a skill with minimal required fields.
    public static SkillDefinition of(String id, String name, String description, String prompt) {
        return new SkillDefinition(id, name, description, prompt, List.of(), Map.of());
    }

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    /// Builds the prompt with parameter substitution in a single pass.
    /// Parameter values exceeding {@code maxParameterValueLength} characters are rejected
    /// to mitigate prompt injection and resource exhaustion.
    public String buildPrompt(Map<String, String> parameterValues, int maxParameterValueLength) {
        // Resolve parameter values
        Map<String, String> resolvedValues = new HashMap<>();
        for (SkillParameter param : parameters) {
            String value = parameterValues.getOrDefault(param.name(), param.defaultValue());
            if (value != null) {
                if (value.length() > maxParameterValueLength) {
                    throw new IllegalArgumentException(
                        "Parameter value too long for: " + param.name());
                }
                if (CustomInstructionSafetyValidator.containsSuspiciousPattern(value)) {
                    throw new IllegalArgumentException(
                        "Parameter value contains suspicious pattern for: " + param.name());
                }
                resolvedValues.put(param.name(), value);
            }
        }
        // Single-pass replacement using regex
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(prompt);
        var sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = resolvedValues.getOrDefault(key, matcher.group());
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /// Validates that all required parameters are provided.
    public void validateParameters(Map<String, String> parameterValues) {
        for (SkillParameter param : parameters) {
            if (param.required() && !parameterValues.containsKey(param.name())) {
                throw new IllegalArgumentException("Missing required parameter: " + param.name());
            }
        }
    }
}
