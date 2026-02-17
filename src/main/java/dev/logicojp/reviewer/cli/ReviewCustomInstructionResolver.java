package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.instruction.CustomInstructionLoader;
import dev.logicojp.reviewer.instruction.CustomInstructionSafetyValidator;
import dev.logicojp.reviewer.instruction.InstructionSource;
import dev.logicojp.reviewer.target.ReviewTarget;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Resolves custom instructions for a review run.
@Singleton
public class ReviewCustomInstructionResolver {

    private static final Logger logger = LoggerFactory.getLogger(ReviewCustomInstructionResolver.class);

    public record InstructionOptions(
        List<Path> instructionPaths,
        boolean noInstructions,
        boolean noPrompts,
        boolean trustTarget
    ) {}

    private final CustomInstructionLoader instructionLoader;
    private final CliOutput output;

    @Inject
    public ReviewCustomInstructionResolver(CustomInstructionLoader instructionLoader, CliOutput output) {
        this.instructionLoader = instructionLoader;
        this.output = output;
    }

    public List<CustomInstruction> resolve(ReviewTarget target, InstructionOptions options) {
        if (options.noInstructions()) {
            logger.info("Custom instructions disabled by --no-instructions flag");
            return List.of();
        }

        List<CustomInstruction> instructions = new ArrayList<>();
        loadInstructionsFromExplicitPaths(options.instructionPaths(), instructions);
        loadInstructionsFromTrustedTarget(target, options, instructions);

        return List.copyOf(instructions);
    }

    private void loadInstructionsFromExplicitPaths(List<Path> instructionPaths,
                                                   List<CustomInstruction> instructions) {
        if (instructionPaths == null || instructionPaths.isEmpty()) {
            return;
        }

        for (Path path : instructionPaths) {
            Optional<CustomInstruction> loaded = loadInstructionFromPath(path);
            loaded.ifPresent(instruction ->
                addIfSafe(instruction, instructions, "  ✓ Loaded instructions: ", true));
        }
    }

    private Optional<CustomInstruction> loadInstructionFromPath(Path path) {
        try {
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                logger.warn("Instruction file not found: {}", path);
                return Optional.empty();
            }

            String content = Files.readString(path);
            if (content.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(new CustomInstruction(
                path.toString(), content.trim(),
                InstructionSource.LOCAL_FILE, null, null));
        } catch (IOException | SecurityException e) {
            logger.warn("Failed to read instruction file {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private void loadInstructionsFromTrustedTarget(ReviewTarget target,
                                                   InstructionOptions options,
                                                   List<CustomInstruction> instructions) {
        if (!canLoadTargetInstructions(target)) {
            return;
        }

        if (!isTargetTrusted(options)) {
            output.println("ℹ  Target instructions skipped (use --trust to load from review target).");
            return;
        }

        output.println("⚠  --trust enabled: loading custom instructions from the review target.");
        CustomInstructionLoader targetLoader = resolveTargetLoader(options);
        List<CustomInstruction> targetInstructions = targetLoader.loadForTarget(target);
        logTrustAuditTrail(targetInstructions);
        addTargetInstructions(targetInstructions, instructions);
    }

    private void logTrustAuditTrail(List<CustomInstruction> instructions) {
        for (CustomInstruction instruction : instructions) {
            logger.info("[TRUST AUDIT] Loaded instruction from: {} (size: {} bytes)",
                instruction.sourcePath(),
                instruction.content() != null ? instruction.content().length() : 0);
        }
    }

    private boolean canLoadTargetInstructions(ReviewTarget target) {
        return target.isLocal();
    }

    private boolean isTargetTrusted(InstructionOptions options) {
        return options.trustTarget();
    }

    private void addTargetInstructions(List<CustomInstruction> targetInstructions,
                                       List<CustomInstruction> instructions) {
        for (CustomInstruction instruction : targetInstructions) {
            addIfSafe(instruction, instructions, "  ✓ Loaded instructions from target: ", false);
        }
    }

    private CustomInstructionLoader resolveTargetLoader(InstructionOptions options) {
        if (options.noPrompts()) {
            return CustomInstructionLoader.withSettings(null, false);
        }
        return instructionLoader;
    }

    private void addIfSafe(CustomInstruction instruction,
                           List<CustomInstruction> instructions,
                           String loadedPrefix,
                           boolean trusted) {
        List<CustomInstruction> safe = CustomInstructionSafetyValidator.filterSafe(
            List.of(instruction),
            "Skipped unsafe instruction",
            trusted
        );
        if (!safe.isEmpty()) {
            instructions.add(safe.getFirst());
            output.println(loadedPrefix + instruction.sourcePath());
        }
    }
}