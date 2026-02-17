package dev.logicojp.reviewer.report;

import java.util.List;
import java.util.regex.Pattern;

/// Sanitizes review content returned by LLM models.
///
/// Removes artifacts that should not appear in the final review output:
/// - Chain-of-Thought (CoT) reasoning blocks (`<thinking>`, `<antThinking>`, etc.)
/// - Model preamble / filler text before the actual review content
/// - Excessive whitespace
public final class ContentSanitizer {

    @FunctionalInterface
    interface SanitizationStrategy {
        String apply(String content);
    }

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

    /// Combined pattern to strip CoT-like blocks in a single pass.
    private static final Pattern COT_BLOCK_PATTERN = Pattern.compile(
        "(?:" + THINKING_BLOCK_PATTERN.pattern() + ")|(?:" + DETAILS_THINKING_PATTERN.pattern() + ")",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    /// Pattern to collapse three or more consecutive blank lines into two.
    private static final Pattern EXCESSIVE_BLANK_LINES = Pattern.compile(
        "\\n{3,}"
    );

    /// Pattern to match dangerous HTML elements that could enable XSS when rendered.
    private static final Pattern DANGEROUS_HTML_PATTERN = Pattern.compile(
        "<\\s*(script|iframe|object|embed|form|input|base|link|meta|style)\\b[^>]*>.*?</\\s*\\1\\s*>|" +
        "<\\s*(script|iframe|object|embed|form|input|base|link|meta|style)\\b[^>]*/?>|" +
        "\\bon\\w+\\s*=",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    /// Combined pattern to remove CoT blocks and dangerous HTML in a single pass.
    private static final Pattern REMOVABLE_BLOCKS_PATTERN = Pattern.compile(
        "(?:" + COT_BLOCK_PATTERN.pattern() + ")|(?:" + DANGEROUS_HTML_PATTERN.pattern() + ")",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    private static final ContentSanitizationPipeline PIPELINE = new ContentSanitizationPipeline(
        List.of(
            new ContentSanitizationRule(REMOVABLE_BLOCKS_PATTERN, ""),
            new ContentSanitizationRule(EXCESSIVE_BLANK_LINES, "\n\n")
        )
    );

    /// Sanitizes the given review content.
    ///
    /// @param content Raw content from the LLM
    /// @return Sanitized content suitable for reports, or null if input is null
    public static String sanitize(String content) {
        return sanitize(content, PIPELINE::apply);
    }

    static String sanitize(String content, SanitizationStrategy strategy) {
        if (content == null) {
            return null;
        }

        return strategy.apply(content).strip();
    }
}
