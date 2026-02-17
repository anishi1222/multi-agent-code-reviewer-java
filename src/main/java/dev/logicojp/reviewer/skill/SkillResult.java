package dev.logicojp.reviewer.skill;

import java.time.LocalDateTime;

/// Result of a skill execution.
public record SkillResult(
    String skillId,
    boolean success,
    String content,
    String errorMessage,
    LocalDateTime timestamp
) {

    public SkillResult {
        timestamp = (timestamp == null) ? LocalDateTime.now() : timestamp;
    }

    /// Creates a successful result.
    public static SkillResult success(String skillId, String content) {
        return new SkillResult(skillId, true, content, null, LocalDateTime.now());
    }

    /// Creates a failure result.
    public static SkillResult failure(String skillId, String errorMessage) {
        return new SkillResult(skillId, false, null, errorMessage, LocalDateTime.now());
    }
}
