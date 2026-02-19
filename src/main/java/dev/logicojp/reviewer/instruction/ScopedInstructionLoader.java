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
import java.util.Optional;
import java.util.stream.Stream;

@Singleton
final class ScopedInstructionLoader {

    private static final Logger logger = LoggerFactory.getLogger(ScopedInstructionLoader.class);

    private final String instructionsDirectory;
    private final String instructionsExtension;

    ScopedInstructionLoader() {
        this(".github/instructions", ".instructions.md");
    }

    ScopedInstructionLoader(String instructionsDirectory, String instructionsExtension) {
        this.instructionsDirectory = instructionsDirectory;
        this.instructionsExtension = instructionsExtension;
    }

    List<CustomInstruction> loadFromInstructionsDirectory(Path baseDirectory) {
        Path instructionsDir = baseDirectory.resolve(instructionsDirectory);
        if (!Files.isDirectory(instructionsDir)) {
            return List.of();
        }

        List<CustomInstruction> instructions = new ArrayList<>();
        try (Stream<Path> stream = Files.list(instructionsDir)) {
            List<Path> instructionFiles = stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(instructionsExtension))
                .sorted()
                .toList();

            for (Path path : instructionFiles) {
                loadScopedInstruction(path).ifPresent(instructions::add);
            }
        } catch (IOException e) {
            logger.warn("Failed to scan instructions directory {}: {}", instructionsDir, e.getMessage(), e);
        }
        return instructions;
    }

    private Optional<CustomInstruction> loadScopedInstruction(Path path) {
        try {
            String rawContent = Files.readString(path, StandardCharsets.UTF_8);
            if (rawContent.isBlank()) {
                logger.debug("Scoped instruction file is empty: {}", path);
                return Optional.empty();
            }

            var parsed = CustomInstructionLoader.parseFrontmatter(rawContent);
            logger.info("Loaded scoped instruction from: {} (applyTo: {})", path, parsed.applyTo());
            return Optional.of(new CustomInstruction(
                path.toString(),
                parsed.content().trim(),
                InstructionSource.LOCAL_FILE,
                parsed.applyTo(),
                parsed.description()
            ));
        } catch (IOException e) {
            logger.warn("Failed to read instruction file {}: {}", path, e.getMessage(), e);
            return Optional.empty();
        }
    }
}