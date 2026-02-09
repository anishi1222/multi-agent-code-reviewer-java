package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.skill.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Configuration model for a review agent.
 * Loaded from YAML files in the agents/ directory.
 */
public record AgentConfig(
    String name,
    String displayName,
    String model,
    String systemPrompt,
    String reviewPrompt,
    String outputFormat,
    List<String> focusAreas,
    List<SkillDefinition> skills
) {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfig.class);

    // Required sections in outputFormat
    private static final List<String> REQUIRED_OUTPUT_SECTIONS = List.of(
        "Priority",
        "指摘の概要",
        "推奨対応",
        "効果"
    );

    private static final Pattern PRIORITY_PATTERN = Pattern.compile(
        "Critical|High|Medium|Low", Pattern.CASE_INSENSITIVE);

    private static final String DEFAULT_OUTPUT_FORMAT = """
        ## 出力フォーマット

        レビュー結果は必ず以下の形式で出力してください。複数の指摘がある場合は、それぞれについて以下の形式で記載してください。

        ---

        ### [指摘番号]. [タイトル]

        | 項目 | 内容 |
        |------|------|
        | **Priority** | Critical / High / Medium / Low のいずれか |
        | **指摘の概要** | 何が問題かの簡潔な説明 |
        | **修正しない場合の影響** | 放置した場合のリスクや影響 |
        | **該当箇所** | ファイルパスと行番号（例: `src/main/java/Example.java` L42-50） |

        **推奨対応**

        具体的な修正方法の説明。可能な場合はコード例を含める：

        ```
        // 修正前
        問題のあるコード

        // 修正後
        推奨されるコード
        ```

        **効果**

        この修正による改善効果の説明。

        ---

        ## Priority の基準
        - **Critical**: セキュリティ脆弱性、データ損失、本番障害につながる問題。即時対応必須
        - **High**: 重大なバグ、パフォーマンス問題、重要な機能の不具合。早急な対応が必要
        - **Medium**: コード品質の問題、保守性の低下、軽微なバグ。計画的に対応
        - **Low**: スタイルの問題、軽微な改善提案。時間があれば対応

        指摘がない場合は「指摘事項なし」と記載してください。
        """;

    public AgentConfig {
        name = name == null ? "" : name;
        displayName = (displayName == null || displayName.isBlank()) ? name : displayName;
        model = (model == null || model.isBlank()) ? "claude-sonnet-4" : model;
        outputFormat = normalizeOutputFormat(outputFormat);
        focusAreas = focusAreas == null ? List.of() : List.copyOf(focusAreas);
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getModel() {
        return model;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getReviewPrompt() {
        return reviewPrompt;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public List<String> getFocusAreas() {
        return focusAreas;
    }

    public List<SkillDefinition> getSkills() {
        return skills;
    }

    public AgentConfig withModel(String overrideModel) {
        return new AgentConfig(
            name,
            displayName,
            overrideModel,
            systemPrompt,
            reviewPrompt,
            outputFormat,
            focusAreas,
            skills
        );
    }

    public void validateRequired() {
        StringBuilder missing = new StringBuilder();
        if (name == null || name.isBlank()) {
            missing.append("name");
        }
        if (systemPrompt == null || systemPrompt.isBlank()) {
            if (missing.length() > 0) {
                missing.append(", ");
            }
            missing.append("systemPrompt");
        }
        if (reviewPrompt == null || reviewPrompt.isBlank()) {
            if (missing.length() > 0) {
                missing.append(", ");
            }
            missing.append("reviewPrompt");
        }
        if (outputFormat == null || outputFormat.isBlank()) {
            if (missing.length() > 0) {
                missing.append(", ");
            }
            missing.append("outputFormat");
        }
        if (missing.length() > 0) {
            throw new IllegalArgumentException("Missing required agent fields: " + missing);
        }
        if (focusAreas == null || focusAreas.isEmpty()) {
            logger.warn("Agent '{}' has no focusAreas; proceeding with defaults.", name);
        }

        // Validate outputFormat required sections
        validateOutputFormat();
    }

    private void validateOutputFormat() {
        if (outputFormat == null || outputFormat.isBlank()) {
            return;
        }

        List<String> missingSections = new ArrayList<>();
        for (String section : REQUIRED_OUTPUT_SECTIONS) {
            if (!outputFormat.contains(section)) {
                missingSections.add(section);
            }
        }

        if (!missingSections.isEmpty()) {
            logger.warn("Agent '{}' outputFormat is missing recommended sections: {}",
                name, String.join(", ", missingSections));
        }

        // Check for Priority keywords
        if (!PRIORITY_PATTERN.matcher(outputFormat).find()) {
            logger.warn("Agent '{}' outputFormat does not contain Priority levels (Critical/High/Medium/Low).", name);
        }
    }

    private static String normalizeOutputFormat(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_OUTPUT_FORMAT;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("##")) {
            return trimmed;
        }
        return "## 出力フォーマット\n\n" + trimmed;
    }

    /**
     * Builds the complete system prompt including output format instructions.
     */
    public String buildFullSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sb.append(systemPrompt.trim()).append("\n\n");
        }

        if (focusAreas != null && !focusAreas.isEmpty()) {
            sb.append("## レビュー観点\n\n");
            sb.append("以下の観点 **のみ** に基づいてレビューしてください。これ以外の観点での指摘は行わないでください。\n\n");
            for (String area : focusAreas) {
                sb.append("- ").append(area).append("\n");
            }
            sb.append("\n");
        }

        if (outputFormat != null && !outputFormat.isBlank()) {
            String outputText = outputFormat.trim();
            if (!outputText.startsWith("##")) {
                sb.append("## 出力フォーマット\n\n");
            }
            sb.append(outputText).append("\n");
        }

        return sb.toString();
    }

    public String buildReviewPrompt(String repository) {
        if (reviewPrompt == null || reviewPrompt.isBlank()) {
            throw new IllegalStateException("Review prompt is not configured for agent: " + name);
        }

        String focusAreaText = formatFocusAreas();
        return reviewPrompt
            .replace("${repository}", repository)
            .replace("${displayName}", displayName != null ? displayName : name)
            .replace("${name}", name)
            .replace("${focusAreas}", focusAreaText);
    }

    /**
     * Builds the review prompt for a local directory review.
     * Embeds the source code content directly in the prompt.
     * @param targetName Display name of the target directory
     * @param sourceContent Collected source code content
     * @return The formatted review prompt with embedded source code
     */
    public String buildLocalReviewPrompt(String targetName, String sourceContent) {
        if (reviewPrompt == null || reviewPrompt.isBlank()) {
            throw new IllegalStateException("Review prompt is not configured for agent: " + name);
        }

        String focusAreaText = formatFocusAreas();
        
        // Build embedded source content section
        String embeddedContent = """
            
            以下は対象ディレクトリのソースコードです:
            
            %s
            """.formatted(sourceContent);
        
        // Replace placeholders and append source content
        String basePrompt = reviewPrompt
            .replace("${repository}", targetName)
            .replace("${displayName}", displayName != null ? displayName : name)
            .replace("${name}", name)
            .replace("${focusAreas}", focusAreaText);
        
        return basePrompt + embeddedContent;
    }

    private String formatFocusAreas() {
        if (focusAreas == null || focusAreas.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String area : focusAreas) {
            sb.append("- ").append(area).append("\n");
        }
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "AgentConfig{name='" + name + "', displayName='" + displayName + "'}";
    }
}
