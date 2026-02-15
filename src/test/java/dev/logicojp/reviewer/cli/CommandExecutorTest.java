package dev.logicojp.reviewer.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CommandExecutor")
class CommandExecutorTest {

    private static final Logger logger = LoggerFactory.getLogger(CommandExecutorTest.class);

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        @DisplayName("パーサーがemptyを返した場合はOKを返す")
        void returnsOkWhenParserReturnsEmpty() {
            int result = CommandExecutor.execute(
                new String[]{"--help"},
                _ -> Optional.empty(),
                _ -> ExitCodes.SOFTWARE,
                _ -> {},
                logger
            );
            assertThat(result).isEqualTo(ExitCodes.OK);
        }

        @Test
        @DisplayName("正常なパースと実行で実行結果を返す")
        void returnsExecutorResult() {
            int result = CommandExecutor.execute(
                new String[]{"arg"},
                _ -> Optional.of("parsed"),
                _ -> ExitCodes.OK,
                _ -> {},
                logger
            );
            assertThat(result).isEqualTo(ExitCodes.OK);
        }

        @Test
        @DisplayName("CliValidationExceptionでUSAGEを返す")
        void returnsUsageOnCliValidationException() {
            int result = CommandExecutor.execute(
                new String[]{"bad"},
                _ -> { throw new CliValidationException("bad option", true); },
                _ -> ExitCodes.OK,
                _ -> {},
                logger
            );
            assertThat(result).isEqualTo(ExitCodes.USAGE);
        }

        @Test
        @DisplayName("一般例外でSOFTWAREを返す")
        void returnsSoftwareOnGeneralException() {
            int result = CommandExecutor.execute(
                new String[]{"arg"},
                _ -> Optional.of("parsed"),
                _ -> { throw new RuntimeException("fail"); },
                _ -> {},
                logger
            );
            assertThat(result).isEqualTo(ExitCodes.SOFTWARE);
        }

        @Test
        @DisplayName("CliOutputを指定した場合はそのerrorストリームへ出力される")
        void writesErrorsToProvidedCliOutput() {
            ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
            CliOutput cliOutput = new CliOutput(new PrintStream(OutputStream.nullOutputStream()), new PrintStream(errBuffer));

            int result = CommandExecutor.execute(
                new String[]{"bad"},
                _ -> {
                    throw new CliValidationException("bad option", true);
                },
                _ -> ExitCodes.OK,
                _ -> {
                },
                logger,
                cliOutput
            );

            assertThat(result).isEqualTo(ExitCodes.USAGE);
            assertThat(errBuffer.toString()).contains("bad option");
        }
    }
}
