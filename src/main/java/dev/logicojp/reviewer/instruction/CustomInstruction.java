package dev.logicojp.reviewer.instruction;

/**
 * Represents a loaded custom instruction with its source information.
 * 
 * @param sourcePath The path where the instruction was loaded from
 * @param content The instruction content
 * @param source The type of source (local file, GitHub, merged)
 */
public record CustomInstruction(
    String sourcePath,
    String content,
    InstructionSource source
) {
    /**
     * Checks if this instruction has no meaningful content.
     * 
     * @return true if content is null or blank
     */
    public boolean isEmpty() {
        return content == null || content.isBlank();
    }
    
    /**
     * Creates a prompt section for this custom instruction.
     * 
     * @return Formatted prompt section string
     */
    public String toPromptSection() {
        return """
            ## カスタムインストラクション
            
            以下のプロジェクト固有の指示に従ってください:
            
            %s
            """.formatted(content);
    }
}
