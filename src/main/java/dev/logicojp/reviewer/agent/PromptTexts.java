package dev.logicojp.reviewer.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Centralized prompt text loader for agent prompt snippets.
/// Reads from templates directory when available and falls back to built-in text.
final class PromptTexts {

    private static final Logger logger = LoggerFactory.getLogger(PromptTexts.class);
    private static final String DEFAULT_TEMPLATE_DIR = "templates";
    private static final String TEMPLATE_DIR_PROPERTY = "reviewer.templates.directory";

    static final String FOCUS_AREAS_GUIDANCE = load(
        "agent-focus-areas-guidance.md",
        "以下の観点 **のみ** に基づいてレビューしてください。これ以外の観点での指摘は行わないでください。"
    );

    static final String LOCAL_SOURCE_HEADER = load(
        "local-source-header.md",
        "以下は対象ディレクトリのソースコードです。読み込んだらレビューを開始してください。"
    );

    static final String LOCAL_RESULT_REQUEST = load(
        "local-review-result-request.md",
        "ソースコードを読み込んだ内容に基づいて、指定された出力形式でレビュー結果を返してください。"
    );

    private PromptTexts() {
    }

    private static String load(String templateName, String fallback) {
        String templateDir = resolveTemplateDirectory();
        Path path = Path.of(templateDir, templateName);
        if (Files.exists(path)) {
            try {
                String fileContent = Files.readString(path, StandardCharsets.UTF_8).trim();
                if (!fileContent.isBlank()) {
                    return fileContent;
                }
            } catch (IOException e) {
                logger.debug("Failed to load prompt template '{}' from file: {}", templateName, e.getMessage());
            }
        }

        String resourcePath = templateDir + "/" + templateName;
        try (InputStream in = PromptTexts.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in != null) {
                String resourceContent = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!resourceContent.isBlank()) {
                    return resourceContent;
                }
            }
        } catch (IOException e) {
            logger.debug("Failed to load prompt template '{}' from classpath: {}", templateName, e.getMessage());
        }

        return fallback;
    }

    private static String resolveTemplateDirectory() {
        String configured = System.getProperty(TEMPLATE_DIR_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_TEMPLATE_DIR;
        }
        return configured;
    }
}
