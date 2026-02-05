package dev.logicojp.reviewer.instruction;

/**
 * Source type of a custom instruction.
 * Identifies where the instruction was loaded from.
 */
public enum InstructionSource {
    /**
     * Instruction loaded from a local file.
     */
    LOCAL_FILE,
    
    /**
     * Instruction fetched from a GitHub repository.
     */
    GITHUB_REPOSITORY,
    
    /**
     * Multiple instructions merged together.
     */
    MERGED
}
