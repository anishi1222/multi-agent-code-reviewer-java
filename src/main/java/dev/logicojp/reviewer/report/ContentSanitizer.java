package dev.logicojp.reviewer.report;

import java.util.regex.Pattern;

/// Sanitizes review content returned by LLM models.
///
/// Removes artifacts that should not appear in the final review output:
/// - Chain-of-Thought (CoT) reasoning blocks (`<thinking>`, `<antThinking>`, etc.)
/// - Model preamble / filler text before the actual review content
/// - Excessive whitespace
public final class ContentSanitizer {

    private ContentSanitizer() {
        // Utility class — not instantiable
    }

    /// Pattern to match common CoT / thinking XML-style blocks.
    /// Handles `<thinking>`, `<antThinking>`, `<reflection>`, `<inner_monologue>`, etc.
    /// Uses possessive quantifier (`*+`) to prevent catastrophic backtracking on large inputs.
    private static final Pattern THINKING_BLOCK_PATTERN = Pattern.compile(
        "<(thinking|antThinking|reflection|inner_monologue|scratchpad)>" +
        "(?:(?!</\\1>).)*+" +
        "</\\1>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    /// Pattern to match `<details>` blocks often used to hide reasoning.
    private static final Pattern DETAILS_THINKING_PATTERN = Pattern.compile(
        "<details>\\s*<summary>\\s*(?:Thinking|思考|推論).*?</summary>.*?</details>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    /// Pattern to collapse three or more consecutive blank lines into two.
    private static final Pattern EXCESSIVE_BLANK_LINES = Pattern.compile(
        "\\n{3,}"
    );

    /// Sanitizes the given review content.
    ///
    /// @param content Raw content from the LLM
    /// @return Sanitized content suitable for reports, or null if input is null
    public static String sanitize(String content) {
        if (content == null) {
            return null;
        }

        String result = content;

        // Remove CoT / thinking blocks
        result = THINKING_BLOCK_PATTERN.matcher(result).replaceAll("");

        // Remove <details> blocks that wrap reasoning
        result = DETAILS_THINKING_PATTERN.matcher(result).replaceAll("");

        // Collapse excessive blank lines
        result = EXCESSIVE_BLANK_LINES.matcher(result).replaceAll("\n\n");

        return result.strip();
    }
}
