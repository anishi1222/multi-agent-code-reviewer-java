package dev.logicojp.reviewer.cli;

/// CLI exit codes using simplified values commonly used in CLI tools.
///
/// Note: These values are NOT the actual sysexits.h codes but simplified
/// values commonly used in CLI tools (0=success, 1=error, 2=usage).
///
/// | Code | Meaning                       | sysexits.h reference |
/// |------|-------------------------------|----------------------|
/// | 0    | Successful execution          | EX_OK (0)            |
/// | 1    | Internal software error       | cf. EX_SOFTWARE (70) |
/// | 2    | Invalid command-line usage    | cf. EX_USAGE (64)    |
///
/// Future additions may reference sysexits.h:
/// - EX_DATAERR (65): input data error
/// - EX_NOINPUT (66): input file not found
/// - EX_IOERR (74): I/O error
/// - EX_TEMPFAIL (75): temporary failure, retry later
public final class ExitCodes {
    public static final int OK = 0;
    public static final int USAGE = 2;
    public static final int SOFTWARE = 1;

    private ExitCodes() {
    }
}

