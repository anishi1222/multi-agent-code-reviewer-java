package dev.logicojp.reviewer.instruction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

final class ScopedInstructionLoader {

    private static final Logger logger = LoggerFactory.getLogger(ScopedInstructionLoader.class);

    private final String instructionsDirectory;
    private final String instructionsExtension;

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
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(instructionsExtension))
                .sorted()
                .forEach(path -> {
                    try {
                        String rawContent = Files.readString(path, StandardCharsets.UTF_8);
                        if (rawContent.isBlank()) {
                            logger.debug("Scoped instruction file is empty: {}", path);
                            return;
                        }

                        var parsed = CustomInstructionLoader.parseFrontmatter(rawContent);
                        instructions.add(new CustomInstruction(
                            path.toString(),
                            parsed.content().trim(),
                            InstructionSource.LOCAL_FILE,
                            parsed.applyTo(),
                            parsed.description()
                        ));
                        logger.info("Loaded scoped instruction from: {} (applyTo: {})",
                            path, parsed.applyTo());
                    } catch (IOException e) {
                        logger.warn("Failed to read instruction file {}: {}", path, e.getMessage());
                    }
                });
        } catch (IOException e) {
            logger.warn("Failed to scan instructions directory {}: {}", instructionsDir, e.getMessage());
        }
        return instructions;
    }
}