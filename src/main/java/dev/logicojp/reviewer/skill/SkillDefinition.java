package dev.logicojp.reviewer.skill;

import java.util.List;
import java.util.Map;

/**
 * Defines a skill that an agent can perform.
 * Skills are discrete capabilities that can be invoked by name.
 */
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

    /**
     * Creates a skill with minimal required fields.
     */
    public static SkillDefinition of(String id, String name, String description, String prompt) {
        return new SkillDefinition(id, name, description, prompt, List.of(), Map.of());
    }

    /**
     * Builds the prompt with parameter substitution.
     */
    public String buildPrompt(Map<String, String> parameterValues) {
        String result = prompt;
        for (SkillParameter param : parameters) {
            String value = parameterValues.getOrDefault(param.name(), param.defaultValue());
            if (value != null) {
                result = result.replace("${" + param.name() + "}", value);
            }
        }
        return result;
    }

    /**
     * Validates that all required parameters are provided.
     */
    public void validateParameters(Map<String, String> parameterValues) {
        for (SkillParameter param : parameters) {
            if (param.required() && !parameterValues.containsKey(param.name())) {
                throw new IllegalArgumentException("Missing required parameter: " + param.name());
            }
        }
    }
}
