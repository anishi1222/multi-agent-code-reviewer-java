package dev.logicojp.reviewer.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses GitHub Copilot agent definition files (.agent.md format).
 * 
 * Format example:
 * ---
 * name: security-reviewer
 * description: Security code review agent
 * model: claude-sonnet-4
 * ---
 * 
 * # Security Reviewer
 * 
 * You are a security expert...
 * 
 * ## Focus Areas
 * - SQL Injection
 * - XSS
 */
public class AgentMarkdownParser {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentMarkdownParser.class);
    
    // Pattern to match YAML frontmatter
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
        "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$", 
        Pattern.DOTALL
    );
    
    // Pattern to extract focus areas from markdown list
    private static final Pattern FOCUS_AREA_PATTERN = Pattern.compile(
        "##\\s*(?:Focus Areas|レビュー観点|観点)\\s*\\n((?:\\s*[-*]\\s*.+\\n?)+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SECTION_HEADER_PATTERN = Pattern.compile("^##\\s+(.+)$");
    private static final Set<String> RECOGNIZED_SECTIONS = Set.of(
        "system prompt",
        "システムプロンプト",
        "review prompt",
        "レビュー依頼",
        "レビュー用プロンプト",
        "output format",
        "出力フォーマット",
        "focus areas",
        "レビュー観点",
        "観点"
    );
    
    /**
     * Parses a .agent.md file and returns an AgentConfig.
     * @param mdFile Path to the .agent.md file
     * @return AgentConfig parsed from the file
     */
    public AgentConfig parse(Path mdFile) throws IOException {
        String content = Files.readString(mdFile);
        return parseContent(content, mdFile.getFileName().toString());
    }
    
    /**
     * Parses markdown content and returns an AgentConfig.
     */
    public AgentConfig parseContent(String content, String filename) {
        Matcher frontmatterMatcher = FRONTMATTER_PATTERN.matcher(content);

        if (!frontmatterMatcher.matches()) {
            logger.warn("No valid frontmatter found in {}", filename);
            // Try to parse without frontmatter
            return parseWithoutFrontmatter(content, filename);
        }

        String frontmatter = frontmatterMatcher.group(1);
        String body = frontmatterMatcher.group(2);

        // Parse frontmatter as simple key-value pairs
        Map<String, String> metadata = parseFrontmatter(frontmatter);

        // Extract name from filename if not in frontmatter
        String name = metadata.getOrDefault("name", extractNameFromFilename(filename));
        String displayName = metadata.getOrDefault("description",
            metadata.getOrDefault("displayName", name));
        String model = metadata.getOrDefault("model", "claude-sonnet-4");

        Map<String, String> sections = extractSections(body);
        String systemPrompt = getSection(sections, "system prompt", "システムプロンプト");
        String reviewPrompt = getSection(sections, "review prompt", "レビュー依頼", "レビュー用プロンプト");
        String outputFormat = getSection(sections, "output format", "出力フォーマット");

        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = body.trim();
        }

        String focusAreasSection = getSection(sections, "focus areas", "レビュー観点", "観点");
        List<String> focusAreas = focusAreasSection != null
            ? extractFocusAreas(focusAreasSection)
            : extractFocusAreas(body);

        AgentConfig config = new AgentConfig(
            name,
            displayName,
            model,
            systemPrompt,
            reviewPrompt,
            outputFormat,
            focusAreas,
            List.of()  // skills - parsed from Skills section if present
        );
        config.validateRequired();
        return config;
    }

    private AgentConfig parseWithoutFrontmatter(String content, String filename) {
        String name = extractNameFromFilename(filename);

        Map<String, String> sections = extractSections(content);
        String systemPrompt = getSection(sections, "system prompt", "システムプロンプト");
        String reviewPrompt = getSection(sections, "review prompt", "レビュー依頼", "レビュー用プロンプト");
        String outputFormat = getSection(sections, "output format", "出力フォーマット");
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = content.trim();
        }

        AgentConfig config = new AgentConfig(
            name,
            name,
            "claude-sonnet-4",
            systemPrompt,
            reviewPrompt,
            outputFormat,
            extractFocusAreas(content),
            List.of()  // skills
        );
        config.validateRequired();
        return config;
    }
    
    private Map<String, String> parseFrontmatter(String frontmatter) {
        Map<String, String> metadata = new HashMap<>();
        
        for (String line : frontmatter.split("\\n")) {
            line = line.trim();
            if (line.isEmpty() || !line.contains(":")) {
                continue;
            }
            
            int colonIndex = line.indexOf(':');
            String key = line.substring(0, colonIndex).trim();
            String value = line.substring(colonIndex + 1).trim();
            
            // Remove quotes if present
            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            
            metadata.put(key, value);
        }
        
        return metadata;
    }
    
    private List<String> extractFocusAreas(String body) {
        List<String> focusAreas = new ArrayList<>();
        
        Matcher matcher = FOCUS_AREA_PATTERN.matcher(body);
        if (matcher.find()) {
            String listContent = matcher.group(1);
            for (String line : listContent.split("\\n")) {
                line = line.trim();
                if (line.startsWith("-") || line.startsWith("*")) {
                    String item = line.substring(1).trim();
                    if (!item.isEmpty()) {
                        focusAreas.add(item);
                    }
                }
            }
        }
        
        // If no focus areas found, return a default
        if (focusAreas.isEmpty()) {
            focusAreas.add("一般的なコード品質");
        }
        
        return focusAreas;
    }

    private Map<String, String> extractSections(String body) {
        Map<String, StringBuilder> sectionBuilders = new LinkedHashMap<>();
        String currentKey = null;

        for (String line : body.split("\\n", -1)) {
            Matcher matcher = SECTION_HEADER_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                String sectionKey = normalizeSectionKey(matcher.group(1));
                if (RECOGNIZED_SECTIONS.contains(sectionKey)) {
                    if (sectionBuilders.containsKey(sectionKey)) {
                        logger.warn("Duplicate section '## {}' found; using the last occurrence.", matcher.group(1));
                    }
                    currentKey = sectionKey;
                    sectionBuilders.put(currentKey, new StringBuilder());
                    continue;
                }
            }

            if (currentKey != null) {
                sectionBuilders.get(currentKey).append(line).append("\n");
            }
        }

        Map<String, String> sections = new LinkedHashMap<>();
        for (Map.Entry<String, StringBuilder> entry : sectionBuilders.entrySet()) {
            sections.put(entry.getKey(), entry.getValue().toString().trim());
        }
        return sections;
    }

    private String getSection(Map<String, String> sections, String... keys) {
        for (String key : keys) {
            String normalized = normalizeSectionKey(key);
            if (sections.containsKey(normalized)) {
                return sections.get(normalized);
            }
        }
        return null;
    }

    private String normalizeSectionKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
    
    private String extractNameFromFilename(String filename) {
        // Remove .agent.md or .md suffix
        String name = filename;
        if (name.endsWith(".agent.md")) {
            name = name.substring(0, name.length() - ".agent.md".length());
        } else if (name.endsWith(".md")) {
            name = name.substring(0, name.length() - ".md".length());
        }
        return name;
    }
}
