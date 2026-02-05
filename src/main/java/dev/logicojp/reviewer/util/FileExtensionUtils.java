package dev.logicojp.reviewer.util;

import java.util.Map;

/**
 * Utility class for file extension operations.
 * Provides methods to extract file extensions and map them to programming languages.
 */
public final class FileExtensionUtils {

    private FileExtensionUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Mapping of file extensions to language identifiers for syntax highlighting.
     */
    private static final Map<String, String> EXTENSION_TO_LANGUAGE = Map.ofEntries(
        // JVM languages
        Map.entry("java", "java"),
        Map.entry("kt", "kotlin"),
        Map.entry("scala", "scala"),
        Map.entry("groovy", "groovy"),
        
        // Scripting languages
        Map.entry("py", "python"),
        Map.entry("rb", "ruby"),
        Map.entry("php", "php"),
        
        // JavaScript/TypeScript
        Map.entry("js", "javascript"),
        Map.entry("jsx", "javascript"),
        Map.entry("ts", "typescript"),
        Map.entry("tsx", "typescript"),
        
        // Systems languages
        Map.entry("go", "go"),
        Map.entry("rs", "rust"),
        Map.entry("c", "c"),
        Map.entry("h", "c"),
        Map.entry("cpp", "cpp"),
        Map.entry("hpp", "cpp"),
        
        // .NET languages
        Map.entry("cs", "csharp"),
        Map.entry("fs", "fsharp"),
        Map.entry("vb", "vb"),
        
        // Query languages
        Map.entry("sql", "sql"),
        Map.entry("graphql", "graphql"),
        
        // Config files
        Map.entry("yaml", "yaml"),
        Map.entry("yml", "yaml"),
        Map.entry("json", "json"),
        Map.entry("xml", "xml"),
        Map.entry("toml", "toml"),
        
        // Documentation
        Map.entry("md", "markdown"),
        Map.entry("rst", "rst"),
        
        // Shell scripts
        Map.entry("sh", "bash"),
        Map.entry("bash", "bash"),
        Map.entry("zsh", "bash"),
        Map.entry("ps1", "powershell"),
        
        // Infrastructure
        Map.entry("dockerfile", "dockerfile"),
        Map.entry("tf", "hcl"),
        Map.entry("hcl", "hcl")
    );

    /**
     * Extracts the file extension from a filename.
     * 
     * @param fileName The filename to extract extension from
     * @return The lowercase extension without the dot, or empty string if none
     */
    public static String getExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Gets the language identifier for syntax highlighting based on file path.
     * 
     * @param filePath The file path or filename
     * @return The language identifier, or empty string if unknown
     */
    public static String getLanguageForFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        
        String ext = getExtension(filePath);
        return EXTENSION_TO_LANGUAGE.getOrDefault(ext, "");
    }

    /**
     * Checks if the given extension is a known language extension.
     * 
     * @param extension The file extension (without dot)
     * @return true if the extension maps to a known language
     */
    public static boolean isKnownExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        return EXTENSION_TO_LANGUAGE.containsKey(extension.toLowerCase());
    }
}
