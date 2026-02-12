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

/// Parses Agent Skills specification files (SKILL.md).
///
/// Follows the Agent Skills spec (https://agentskills.io/specification):
/// ```
/// .github/skills/skill-name/SKILL.md
/// ```
/// - name must be lowercase alphanumeric + hyphens, matching directory name
/// - required frontmatter: name, description
/// - optional: license, compatibility, metadata, allowed-tools
public class SkillMarkdownParser {

    private static final Logger logger = LoggerFactory.getLogger(SkillMarkdownParser.class);

    /// Standard filename for Agent Skills spec
    static final String SKILL_MD = "SKILL.md";

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
        "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$",
        Pattern.DOTALL
    );

    /// Parses a SKILL.md file and returns a SkillDefinition.
    ///
    /// The skill ID is derived from the parent directory name.
    ///
    /// @param skillFile Path to the SKILL.md file
    /// @return SkillDefinition parsed from the file
    /// @throws IllegalArgumentException if the file is not a SKILL.md file
    public SkillDefinition parse(Path skillFile) throws IOException {
        String filename = skillFile.getFileName().toString();
        if (!filename.equals(SKILL_MD)) {
            throw new IllegalArgumentException(
                "Unsupported skill file format: " + filename + ". Only SKILL.md is supported.");
        }

        String content = Files.readString(skillFile);
        String dirName = skillFile.getParent().getFileName().toString();
        return parseContent(content, dirName);
    }

    /// Parses skill markdown content and returns a SkillDefinition.
    /// @param content The full content of the SKILL.md file
    /// @param skillId The skill ID (directory name)
    /// @return SkillDefinition parsed from the content
    public SkillDefinition parseContent(String content, String skillId) {
        String id = skillId;

        Matcher frontmatterMatcher = FRONTMATTER_PATTERN.matcher(content);
        if (!frontmatterMatcher.matches()) {
            logger.warn("No valid frontmatter found in {}; using entire content as prompt.", skillId);
            return SkillDefinition.of(id, id, "", content.trim());
        }

        String frontmatter = frontmatterMatcher.group(1);
        String body = frontmatterMatcher.group(2).trim();

        Map<String, String> simpleFields = parseSimpleFields(frontmatter);
        Map<String, String> metadataMap = parseMetadataBlock(frontmatter);

        String name = simpleFields.getOrDefault("name", id);
        String description = simpleFields.getOrDefault("description", "");

        if (body.isBlank()) {
            throw new IllegalArgumentException("Skill file " + skillId + " has no prompt content after frontmatter.");
        }

        return new SkillDefinition(id, name, description, body, List.of(), metadataMap);
    }

    /// Checks whether the given path is a SKILL.md file.
    public boolean isSkillFile(Path path) {
        if (path == null) return false;
        return path.getFileName().toString().equals(SKILL_MD);
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
