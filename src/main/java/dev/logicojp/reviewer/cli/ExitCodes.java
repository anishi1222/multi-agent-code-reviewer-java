package dev.logicojp.reviewer.cli;

/// CLI exit codes following sysexits.h conventions.
///
/// | Code | sysexits.h equivalent | Meaning                       |
/// |------|-----------------------|-------------------------------|
/// | 0    | EX_OK                 | Successful execution          |
/// | 1    | EX_SOFTWARE (70)      | Internal software error       |
/// | 2    | EX_USAGE (64)         | Invalid command-line usage     |
///
/// Future additions should reference sysexits.h:
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

