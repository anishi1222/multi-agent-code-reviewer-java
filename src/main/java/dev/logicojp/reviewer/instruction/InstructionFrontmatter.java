package dev.logicojp.reviewer.instruction;

import dev.logicojp.reviewer.util.FrontmatterParser;

/// Shared frontmatter parsing helpers for instruction and prompt loaders.
final class InstructionFrontmatter {

    private InstructionFrontmatter() {
    }

    static FrontmatterParser.Parsed parse(String rawContent) {
        return FrontmatterParser.parse(rawContent);
    }

    static String bodyOrRaw(FrontmatterParser.Parsed parsed, String rawContent) {
        if (!parsed.hasFrontmatter()) {
            return rawContent;
        }
        String body = parsed.body().trim();
        return body.isEmpty() ? rawContent : body;
    }
}
