package dev.logicojp.reviewer.instruction;

import dev.logicojp.reviewer.target.ReviewTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads custom instructions from repository or local directory.
 * 
 * Supports the following instruction file locations (in priority order):
 * - .github/copilot-instructions.md
 * - .copilot/instructions.md
 * - copilot-instructions.md (root)
 * - INSTRUCTIONS.md (root)
 * - .instructions.md (root)
 */
public class CustomInstructionLoader {

    private static final Logger logger = LoggerFactory.getLogger(CustomInstructionLoader.class);

    /** Instruction file paths to check, in priority order */
    private static final List<String> INSTRUCTION_PATHS = List.of(
        ".github/copilot-instructions.md",
        ".copilot/instructions.md",
        "copilot-instructions.md",
        "INSTRUCTIONS.md",
        ".instructions.md"
    );

    private final List<Path> additionalInstructionPaths;

    public CustomInstructionLoader() {
        this.additionalInstructionPaths = new ArrayList<>();
    }

    public CustomInstructionLoader(List<Path> additionalInstructionPaths) {
        this.additionalInstructionPaths = additionalInstructionPaths != null 
            ? new ArrayList<>(additionalInstructionPaths) 
            : new ArrayList<>();
    }

    /**
     * Loads custom instructions from a local directory.
     * @param directory The directory to search for instruction files
     * @return Optional containing the instruction content, or empty if not found
     */
    public Optional<CustomInstruction> loadFromLocalDirectory(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return Optional.empty();
        }

        Path resolvedDirectory = directory.toAbsolutePath().normalize();
        List<CustomInstruction> instructions = new ArrayList<>();

        // Check standard locations
        for (String relativePath : INSTRUCTION_PATHS) {
            Path instructionPath = resolvedDirectory.resolve(relativePath);
            loadInstructionFile(instructionPath).ifPresent(instructions::add);
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
            return Optional.empty();
        }

        // Merge all found instructions
        return Optional.of(mergeInstructions(instructions));
    }

    /**
     * Loads custom instructions for the given review target.
     * For local targets, loads from the directory.
     * For GitHub targets, this returns empty (GitHub instructions should be fetched via MCP).
     * 
     * @param target The review target
     * @return Optional containing the instruction content
     */
    public Optional<CustomInstruction> loadForTarget(ReviewTarget target) {
        if (target.isLocal()) {
            return target.getLocalPath().flatMap(this::loadFromLocalDirectory);
        }
        // For GitHub targets, instructions should be fetched via MCP tools
        // Return empty here - the caller should handle GitHub instruction fetching
        return Optional.empty();
    }

    /**
     * Loads a single instruction file.
     */
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
                InstructionSource.LOCAL_FILE
            ));
        } catch (IOException e) {
            logger.warn("Failed to read instruction file {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Merges multiple instructions into one.
     */
    private CustomInstruction mergeInstructions(List<CustomInstruction> instructions) {
        if (instructions.size() == 1) {
            return instructions.get(0);
        }

        StringBuilder mergedContent = new StringBuilder();
        List<String> sources = new ArrayList<>();

        for (CustomInstruction instruction : instructions) {
            if (!mergedContent.isEmpty()) {
                mergedContent.append("\n\n---\n\n");
            }
            mergedContent.append("<!-- Source: ").append(instruction.source()).append(" -->\n");
            mergedContent.append(instruction.content());
            sources.add(instruction.sourcePath());
        }

        return new CustomInstruction(
            String.join(", ", sources),
            mergedContent.toString(),
            InstructionSource.MERGED
        );
    }

    /**
     * Creates a prompt section for custom instructions.
     */
    public static String formatAsPromptSection(CustomInstruction instruction) {
        return """
            ## カスタムインストラクション
            
            以下のプロジェクト固有の指示に従ってください:
            
            %s
            """.formatted(instruction.content());
    }

    /**
     * Represents a loaded custom instruction.
     */
    public record CustomInstruction(
        String sourcePath,
        String content,
        InstructionSource source
    ) {
        public boolean isEmpty() {
            return content == null || content.isBlank();
        }
    }

    /**
     * Source type of the instruction.
     */
    public enum InstructionSource {
        LOCAL_FILE,
        GITHUB_REPOSITORY,
        MERGED
    }
}
