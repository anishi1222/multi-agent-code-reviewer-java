package dev.logicojp.reviewer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CliPathResolver")
class CliPathResolverTest {

    @Test
    @DisplayName("空の明示パスはemptyを返す")
    void blankExplicitPathReturnsEmpty() {
        Optional<java.nio.file.Path> resolved = CliPathResolver.resolveExplicitExecutable("  ", "gh");
        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("存在しないPATH候補の場合はemptyを返す")
    void notFoundInPathReturnsEmpty() {
        Optional<java.nio.file.Path> resolved = CliPathResolver.findExecutableInPath("___no_such_bin___");
        assertThat(resolved).isEmpty();
    }
}
