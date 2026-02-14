package dev.logicojp.reviewer.instruction;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/// Validates custom instruction content for basic prompt-injection safeguards.
public final class CustomInstructionSafetyValidator {

    private static final int MAX_INSTRUCTION_SIZE = 64 * 1024;
    private static final List<Pattern> SUSPICIOUS_PATTERNS = List.of(
        Pattern.compile("ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("disregard\\s+(all\\s+)?(previous|prior|above)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("forget\\s+(all\\s+)?(previous|prior)\\s+instructions?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(override|replace)\\s+(the\\s+)?(system|developer)\\s+prompt", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(you\\s+are\\s+now|act\\s+as\\s+if\\s+you\\s+are)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(以下|上記|これまで|前|以前)\\s*の?\\s*指示\\s*を\\s*無視"),
        Pattern.compile("システム\\s*プロンプト\\s*(を)?\\s*(上書き|無視|無効化)")
    );

    public record ValidationResult(boolean safe, String reason) {}

    private CustomInstructionSafetyValidator() {
    }

    public static ValidationResult validate(CustomInstruction instruction) {
        if (instruction == null || instruction.isEmpty()) {
            return new ValidationResult(true, "empty");
        }

        String content = instruction.content();
        if (content.length() > MAX_INSTRUCTION_SIZE) {
            return new ValidationResult(false, "size limit exceeded");
        }

        String normalized = normalize(content);
        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return new ValidationResult(false, "potential prompt-injection pattern");
            }
        }

        return new ValidationResult(true, "ok");
    }

    private static String normalize(String content) {
        return Normalizer.normalize(content, Normalizer.Form.NFKC)
            .replaceAll("\\s+", " ");
    }
}
