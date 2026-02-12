package dev.logicojp.reviewer.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Parses Agent Skills specification files (SKILL.md) and legacy .skill.md files.
///
/// Supports two formats:
///
/// **Agent Skills spec** (https://agentskills.io/specification):
/// ```
/// .github/skills/skill-name/SKILL.md
/// ```
/// - name must be lowercase alphanumeric + hyphens, matching directory name
/// - required frontmatter: name, description
/// - optional: license, compatibility, metadata, allowed-tools
///
/// **Legacy format** (.skill.md — for backward compatibility):
/// ```
/// agents/agent-name/skill-id.skill.md
/// ```
public class SkillMarkdownParser {

    private static final Logger logger = LoggerFactory.getLogger(SkillMarkdownParser.class);

    /// Standard filename for Agent Skills spec
    static final String SKILL_MD = "SKILL.md";

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
        "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$",
        Pattern.DOTALL
    );

    /// Parses a SKILL.md or .skill.md file and returns a SkillDefinition.
    ///
    /// For SKILL.md files, the skill ID is derived from the parent directory name.
    /// For legacy .skill.md files, the skill ID is derived from the filename.
    ///
    /// @param skillFile Path to the skill file
    /// @return SkillDefinition parsed from the file
    public SkillDefinition parse(Path skillFile) throws IOException {
        String content = Files.readString(skillFile);
        String filename = skillFile.getFileName().toString();

        // For SKILL.md, extract ID from directory name (Agent Skills spec)
        if (filename.equals(SKILL_MD)) {
            String dirName = skillFile.getParent().getFileName().toString();
            return parseContent(content, dirName, true);
        }

        // Legacy .skill.md format
        return parseContent(content, filename, false);
    }

    /// Parses skill markdown content and returns a SkillDefinition.
    /// @param content The full content of the skill file
    /// @param identifier The directory name (for SKILL.md) or filename (for .skill.md)
    /// @return SkillDefinition parsed from the content
    public SkillDefinition parseContent(String content, String identifier) {
        return parseContent(content, identifier, false);
    }

    /// Parses skill markdown content and returns a SkillDefinition.
    /// @param content The full content of the skill file
    /// @param identifier The directory name (for SKILL.md) or filename (for .skill.md)
    /// @param isAgentSkillsSpec true if this is an Agent Skills spec file (SKILL.md in named directory)
    /// @return SkillDefinition parsed from the content
    SkillDefinition parseContent(String content, String identifier, boolean isAgentSkillsSpec) {
        String id = isAgentSkillsSpec ? identifier : extractIdFromFilename(identifier);

        Matcher frontmatterMatcher = FRONTMATTER_PATTERN.matcher(content);
        if (!frontmatterMatcher.matches()) {
            logger.warn("No valid frontmatter found in {}; using entire content as prompt.", identifier);
            return SkillDefinition.of(id, id, "", content.trim());
        }

        String frontmatter = frontmatterMatcher.group(1);
        String body = frontmatterMatcher.group(2).trim();

        Map<String, String> simpleFields = parseSimpleFields(frontmatter);
        List<SkillParameter> parameters = parseParameters(frontmatter);
        Map<String, String> metadataMap = parseMetadataBlock(frontmatter);

        String name = simpleFields.getOrDefault("name", id);
        String description = simpleFields.getOrDefault("description", "");

        if (body.isBlank()) {
            throw new IllegalArgumentException("Skill file " + identifier + " has no prompt content after frontmatter.");
        }

        return new SkillDefinition(id, name, description, body, parameters, metadataMap);
    }

    /// Checks whether the given path is a skill file.
    /// Recognizes both Agent Skills spec (SKILL.md) and legacy (.skill.md) formats.
    public boolean isSkillFile(Path path) {
        if (path == null) return false;
        String filename = path.getFileName().toString();
        return filename.equals(SKILL_MD) || filename.toLowerCase(Locale.ROOT).endsWith(".skill.md");
    }

    /// Discovers all skill directories under the given skills root.
    /// Each skill directory must contain a SKILL.md file.
    ///
    /// @param skillsRoot The root directory to scan (e.g. .github/skills/)
    /// @return List of paths to SKILL.md files found
    public List<Path> discoverSkills(Path skillsRoot) {
        if (skillsRoot == null || !Files.isDirectory(skillsRoot)) {
            return List.of();
        }

        try (Stream<Path> dirs = Files.list(skillsRoot)) {
            return dirs
                .filter(Files::isDirectory)
                .map(dir -> dir.resolve(SKILL_MD))
                .filter(Files::isRegularFile)
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Failed to discover skills in {}: {}", skillsRoot, e.getMessage());
            return List.of();
        }
    }

    /// Extracts the skill ID from a filename by removing the .skill.md suffix.
    String extractIdFromFilename(String filename) {
        if (filename.endsWith(".skill.md")) {
            return filename.substring(0, filename.length() - ".skill.md".length());
        }
        if (filename.endsWith(".md")) {
            return filename.substring(0, filename.length() - ".md".length());
        }
        return filename;
    }

    /// Parses simple key-value fields from frontmatter.
    /// Skips list items (lines starting with '-') and indented sub-keys.
    private Map<String, String> parseSimpleFields(String frontmatter) {
        Map<String, String> fields = new HashMap<>();

        for (String line : frontmatter.split("\\n")) {
            // Skip blank lines, list items, and indented lines (sub-keys of parameters)
            if (line.isBlank() || line.trim().startsWith("-") || line.startsWith(" ") || line.startsWith("\t")) {
                continue;
            }
            int colonIdx = line.indexOf(':');
            if (colonIdx <= 0) continue;

            String key = line.substring(0, colonIdx).trim();
            String value = line.substring(colonIdx + 1).trim();

            // Skip keys that start list blocks (e.g. "parameters:")
            if (value.isEmpty()) continue;

            // Remove surrounding quotes
            if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }

            fields.put(key, value);
        }

        return fields;
    }

    /// Parses the parameters block from frontmatter.
    /// Expected format:
    /// ```
    /// parameters:
    ///   - name: repository
    ///     required: true
    ///     description: 対象リポジトリ
    /// ```
    private List<SkillParameter> parseParameters(String frontmatter) {
        List<SkillParameter> parameters = new ArrayList<>();

        String[] lines = frontmatter.split("\\n");
        boolean inParametersBlock = false;
        Map<String, String> currentParam = null;

        for (String line : lines) {
            String trimmed = line.trim();

            // Detect start of parameters block
            if (trimmed.equals("parameters:")) {
                inParametersBlock = true;
                continue;
            }

            if (!inParametersBlock) continue;

            // End of parameters block: non-indented line that is not a list item
            if (!line.startsWith(" ") && !line.startsWith("\t") && !trimmed.isEmpty()) {
                // Flush last parameter
                if (currentParam != null) {
                    parameters.add(buildParameter(currentParam));
                }
                break;
            }

            // New list item (parameter entry)
            if (trimmed.startsWith("- ")) {
                // Flush previous parameter
                if (currentParam != null) {
                    parameters.add(buildParameter(currentParam));
                }
                currentParam = new HashMap<>();
                // Parse the first key-value on this line
                String afterDash = trimmed.substring(2).trim();
                parseKeyValue(afterDash, currentParam);
                continue;
            }

            // Continuation key-value for current parameter
            if (currentParam != null && !trimmed.isEmpty()) {
                parseKeyValue(trimmed, currentParam);
            }
        }

        // Flush last parameter
        if (currentParam != null) {
            parameters.add(buildParameter(currentParam));
        }

        return parameters;
    }

    private void parseKeyValue(String text, Map<String, String> target) {
        int colonIdx = text.indexOf(':');
        if (colonIdx <= 0) return;

        String key = text.substring(0, colonIdx).trim();
        String value = text.substring(colonIdx + 1).trim();

        // Remove surrounding quotes
        if ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }

        target.put(key, value);
    }

    private SkillParameter buildParameter(Map<String, String> paramData) {
        String name = paramData.getOrDefault("name", "unknown");
        String description = paramData.getOrDefault("description", "");
        String type = paramData.getOrDefault("type", "string");
        boolean required = Boolean.parseBoolean(paramData.getOrDefault("required", "false"));
        String defaultValue = paramData.get("default");

        return new SkillParameter(name, description, type, required, defaultValue);
    }

    /// Parses the metadata block from frontmatter.
    /// Expected format:
    /// ```
    /// metadata:
    ///   agent: best-practices
    ///   version: "1.0"
    /// ```
    private Map<String, String> parseMetadataBlock(String frontmatter) {
        Map<String, String> metadata = new HashMap<>();

        String[] lines = frontmatter.split("\\n");
        boolean inMetadataBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // Detect start of metadata block
            if (trimmed.equals("metadata:")) {
                inMetadataBlock = true;
                continue;
            }

            if (!inMetadataBlock) continue;

            // End of metadata block: non-indented, non-empty line
            if (!line.startsWith(" ") && !line.startsWith("\t") && !trimmed.isEmpty()) {
                break;
            }

            if (!trimmed.isEmpty()) {
                parseKeyValue(trimmed, metadata);
            }
        }

        return metadata.isEmpty() ? Map.of() : Map.copyOf(metadata);
    }
}
