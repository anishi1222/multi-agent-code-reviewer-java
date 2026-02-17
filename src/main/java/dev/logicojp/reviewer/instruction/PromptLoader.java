package dev.logicojp.reviewer.instruction;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/// Loads reusable prompt files from the .github/prompts/ directory.
///
/// Prompt files follow GitHub Copilot's prompt file format:
/// - Files must have the `.prompt.md` extension
/// - Files may contain YAML frontmatter with `description`, `agent`, and `tools` fields
/// - The Markdown body is the prompt content
///
/// Example:
/// ```
/// ---
/// description: 'JUnit 5 best practices'
/// agent: 'agent'
/// tools: ['search/codebase', 'edit/editFiles']
/// ---
/// # JUnit 5 Best Practices
/// Your goal is to help write effective unit tests...
/// ```
///
/// Loaded prompts are converted to [CustomInstruction] records and injected
/// into the agent system prompt alongside regular instructions.
@Singleton
public class PromptLoader {

    private static final Logger logger = LoggerFactory.getLogger(PromptLoader.class);

    /// Default directory for prompt files
    static final String PROMPTS_DIRECTORY = ".github/prompts";

    /// File extension for prompt files
    static final String PROMPT_EXTENSION = ".prompt.md";

    /// Loads all prompt files from the .github/prompts/ directory under the given base directory.
    ///
    /// @param baseDirectory The base directory to search (typically the project root)
    /// @return List of custom instructions loaded from prompt files (empty if none found)
    public List<CustomInstruction> loadFromPromptsDirectory(Path baseDirectory) {
        if (baseDirectory == null || !Files.isDirectory(baseDirectory)) {
            return List.of();
        }

        Path promptsDir = baseDirectory.resolve(PROMPTS_DIRECTORY);
        if (!Files.isDirectory(promptsDir)) {
            logger.debug("Prompts directory not found: {}", promptsDir);
            return List.of();
        }

        List<CustomInstruction> prompts = new ArrayList<>();
        try (Stream<Path> stream = Files.list(promptsDir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().toString().endsWith(PROMPT_EXTENSION))
                  .sorted()
                  .forEach(path -> {
                      try {
                          String rawContent = Files.readString(path, StandardCharsets.UTF_8);
                          if (rawContent.isBlank()) {
                              logger.debug("Prompt file is empty: {}", path);
                              return;
                          }

                          ParsedPrompt parsed = parseFrontmatter(rawContent);
                          prompts.add(new CustomInstruction(
                              path.toString(),
                              parsed.content().trim(),
                              InstructionSource.LOCAL_FILE,
                              null, // prompts do not have applyTo scoping
                              parsed.description()
                          ));
                          logger.info("Loaded prompt from: {} (description: {})",
                              path.getFileName(), parsed.description());
                      } catch (IOException e) {
                          logger.warn("Failed to read prompt file {}: {}", path, e.getMessage());
                      }
                  });
        } catch (IOException e) {
            logger.warn("Failed to scan prompts directory {}: {}", promptsDir, e.getMessage());
        }

        return prompts;
    }

    /// Parsed result of a prompt file with YAML frontmatter.
    ///
    /// @param content     The prompt body content (without frontmatter)
    /// @param description The description from frontmatter (nullable)
    /// @param agent       The agent name from frontmatter (nullable)
    record ParsedPrompt(String content, String description, String agent) {}

    /// Parses YAML frontmatter from a prompt file.
    /// Supports description, agent, and tools fields.
    ///
    /// @param rawContent The raw file content
    /// @return ParsedPrompt with separated content and metadata
    static ParsedPrompt parseFrontmatter(String rawContent) {
        var parsed = InstructionFrontmatter.parse(rawContent);

        if (!parsed.hasFrontmatter()) {
            return new ParsedPrompt(rawContent, null, null);
        }

        String content = InstructionFrontmatter.bodyOrRaw(parsed, rawContent);
        String description = parsed.get("description");
        String agent = parsed.get("agent");

        return new ParsedPrompt(
            content,
            description,
            agent
        );
    }
}
