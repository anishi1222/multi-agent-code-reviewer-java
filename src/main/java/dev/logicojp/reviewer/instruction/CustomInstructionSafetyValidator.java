package dev.logicojp.reviewer.instruction;

import java.text.Normalizer;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/// Validates custom instruction content for basic prompt-injection safeguards.
public final class CustomInstructionSafetyValidator {

    private static final int MAX_INSTRUCTION_SIZE = 32 * 1024;
    private static final List<Pattern> SUSPICIOUS_PATTERNS = List.of(
        Pattern.compile("ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("disregard\\s+(all\\s+)?(previous|prior|above)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("forget\\s+(all\\s+)?(previous|prior)\\s+instructions?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(ignore|forget|discard)\\s+(the\\s+)?(rules|guardrails|policy|constraints)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(bypass|disable|turn\\s+off)\\s+(the\\s+)?(safety|guardrails|restrictions)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(override|replace)\\s+(the\\s+)?(system|developer)\\s+prompt", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(you\\s+are\\s+now|act\\s+as\\s+if\\s+you\\s+are)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(follow\\s+only|prioritize\\s+only)\\s+(the\\s+)?(next|following)\\s+instructions?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(以下|上記|これまで|前|以前)\\s*の?\\s*指示\\s*を\\s*無視"),
        Pattern.compile("(ルール|方針|制約)\\s*を\\s*(忘れて|無視して)"),
        Pattern.compile("システム\\s*プロンプト\\s*(を)?\\s*(上書き|無視|無効化)"),
        Pattern.compile("(모든|이전)\\s*지시\\s*(를)?\\s*무시", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(忽略|无视)\\s*(所有)?\\s*(之前|以上)\\s*的?\\s*指[示令]")
    );
    private static final Pattern SUSPICIOUS_COMBINED_PATTERN = Pattern.compile(
        SUSPICIOUS_PATTERNS.stream().map(Pattern::pattern).collect(Collectors.joining("|")),
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile("[\\p{Cf}\\p{Cc}]");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

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
        if (SUSPICIOUS_COMBINED_PATTERN.matcher(normalized).find()) {
            return new ValidationResult(false, "potential prompt-injection pattern");
        }

        return new ValidationResult(true, "ok");
    }

    private static String normalize(String content) {
        String normalized = Normalizer.normalize(content, Normalizer.Form.NFKC);
        String withoutControlChars = CONTROL_CHARS_PATTERN.matcher(normalized).replaceAll("");
        return WHITESPACE_PATTERN.matcher(withoutControlChars).replaceAll(" ");
    }
}
