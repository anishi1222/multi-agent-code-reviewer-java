package dev.logicojp.reviewer.instruction;

import dev.logicojp.reviewer.target.ReviewTarget;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Loads custom instructions from repository or local directory.
///
/// Supports the following instruction file locations (in priority order):
/// - .github/copilot-instructions.md
/// - .github/instructions/*.instructions.md (GitHub Copilot per-scope format with YAML frontmatter)
/// - .github/prompts/*.prompt.md (GitHub Copilot reusable prompt files — loaded as supplementary instructions)
/// - .copilot/instructions.md
/// - copilot-instructions.md (root)
/// - INSTRUCTIONS.md (root)
/// - .instructions.md (root)
///
/// Per-scope instruction files (.github/instructions/*.instructions.md) support YAML frontmatter:
/// ```
/// ---
/// applyTo: '**/*.java'
/// description: 'Java coding standards'
/// ---
/// Follow these coding standards...
/// ```
///
/// Prompt files (.github/prompts/*.prompt.md) follow GitHub Copilot's prompt file format:
/// ```
/// ---
/// description: 'JUnit 5 best practices'
/// agent: 'agent'
/// ---
/// # JUnit 5 Best Practices
/// ...
/// ```
@Singleton
public class CustomInstructionLoader {

    private static final Logger logger = LoggerFactory.getLogger(CustomInstructionLoader.class);

    /// Instruction file paths to check, in priority order
    private static final List<String> INSTRUCTION_PATHS = List.of(
        ".github/copilot-instructions.md",
        ".copilot/instructions.md",
        "copilot-instructions.md",
        "INSTRUCTIONS.md",
        ".instructions.md"
    );

    /// Directory for GitHub Copilot per-scope instruction files
    private static final String INSTRUCTIONS_DIRECTORY = ".github/instructions";

    /// File extension for per-scope instruction files
    private static final String INSTRUCTIONS_EXTENSION = ".instructions.md";

    private final List<Path> additionalInstructionPaths;
    private final PromptLoader promptLoader;
    private final ScopedInstructionLoader scopedInstructionLoader;
    private final boolean loadPrompts;

    @Inject
    public CustomInstructionLoader(PromptLoader promptLoader, ScopedInstructionLoader scopedInstructionLoader) {
        this(null, true, promptLoader, scopedInstructionLoader);
    }

    /// Creates a loader with custom settings, using default collaborators.
    public static CustomInstructionLoader withSettings(List<Path> additionalInstructionPaths, boolean loadPrompts) {
        return new CustomInstructionLoader(additionalInstructionPaths, loadPrompts,
            new PromptLoader(), new ScopedInstructionLoader());
    }

    CustomInstructionLoader(List<Path> additionalInstructionPaths, boolean loadPrompts,
                            PromptLoader promptLoader, ScopedInstructionLoader scopedInstructionLoader) {
        this.additionalInstructionPaths = additionalInstructionPaths != null 
            ? List.copyOf(additionalInstructionPaths) 
            : List.of();
        this.promptLoader = promptLoader;
        this.scopedInstructionLoader = scopedInstructionLoader;
        this.loadPrompts = loadPrompts;
    }

    /// Loads custom instructions from a local directory.
    /// @param directory The directory to search for instruction files
    /// @return List of custom instructions found (empty if none found)
     List<CustomInstruction> loadFromLocalDirectory(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }

        Path resolvedDirectory = directory.toAbsolutePath().normalize();
        List<CustomInstruction> instructions = new ArrayList<>();

        // Check standard locations
        for (String relativePath : INSTRUCTION_PATHS) {
            Path instructionPath = resolvedDirectory.resolve(relativePath);
            loadInstructionFile(instructionPath).ifPresent(instructions::add);
        }

        // Check .github/instructions/ directory for per-scope instruction files
        instructions.addAll(loadFromInstructionsDirectory(resolvedDirectory));

        // Check .github/prompts/ directory for reusable prompt files
        if (loadPrompts) {
            List<CustomInstruction> prompts = promptLoader.loadFromPromptsDirectory(resolvedDirectory);
            instructions.addAll(prompts);
        }

        // Check additional paths
        for (Path additionalPath : additionalInstructionPaths) {
            Path resolvedPath = additionalPath.isAbsolute() 
                ? additionalPath 
                : resolvedDirectory.resolve(additionalPath);
            loadInstructionFile(resolvedPath).ifPresent(instructions::add);
        }

        if (instructions.isEmpty()) {
            logger.debug("No custom instructions found in: {}", resolvedDirectory);
        }

        return List.copyOf(instructions);
    }

    /// Loads custom instructions for the given review target.
    /// For local targets, loads from the directory.
    /// For GitHub targets, this returns empty (GitHub instructions should be fetched via MCP).
    ///
    /// @param target The review target
    /// @return List of custom instructions found
    public List<CustomInstruction> loadForTarget(ReviewTarget target) {
        if (target.isLocal()) {
            return target.localPath()
                .map(this::loadFromLocalDirectory)
                .orElse(List.of());
        }
        // For GitHub targets, instructions should be fetched via MCP tools
        // Return empty here - the caller should handle GitHub instruction fetching
        return List.of();
    }

    /// Loads per-scope instruction files from the .github/instructions/ directory.
    /// Files must have the .instructions.md extension and may contain YAML frontmatter
    /// with applyTo and description fields.
    List<CustomInstruction> loadFromInstructionsDirectory(Path baseDirectory) {
        return scopedInstructionLoader.loadFromInstructionsDirectory(baseDirectory);
    }

    /// Loads a single instruction file.
    private Optional<CustomInstruction> loadInstructionFile(Path path) {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return Optional.empty();
        }

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                logger.debug("Instruction file is empty: {}", path);
                return Optional.empty();
            }

            logger.info("Loaded custom instruction from: {}", path);
            return Optional.of(new CustomInstruction(
                path.toString(),
                content.trim(),
                InstructionSource.LOCAL_FILE,
                null,
                null
            ));
        } catch (IOException e) {
            logger.warn("Failed to read instruction file {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    /// Parsed result of a frontmatter-enabled instruction file.
    ///
    /// @param content The instruction content (without frontmatter)
    /// @param applyTo The glob pattern from frontmatter (nullable)
    /// @param description The description from frontmatter (nullable)
    record ParsedInstruction(String content, String applyTo, String description) {}

    /// Parses YAML frontmatter from an instruction file content.
    /// Frontmatter is delimited by --- lines at the start of the file.
    /// Supports applyTo and description fields.
    ///
    /// @param rawContent The raw file content
    /// @return ParsedInstruction with separated content and metadata
    static ParsedInstruction parseFrontmatter(String rawContent) {
        var parsed = InstructionFrontmatter.parse(rawContent);

        if (!parsed.hasFrontmatter()) {
            return new ParsedInstruction(rawContent, null, null);
        }

        String content = InstructionFrontmatter.bodyOrRaw(parsed, rawContent);
        String applyTo = parsed.get("applyTo");
        String description = parsed.get("description");

        return new ParsedInstruction(
            content,
            applyTo,
            description
        );
    }
}
