package dev.logicojp.reviewer.cli;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;

import java.io.PrintStream;

/// Factory providing standard I/O {@link PrintStream} beans for DI.
@Factory
class CliOutputFactory {

    @Bean
    @Named("stdout")
    PrintStream stdout() {
        return System.out;
    }

    @Bean
    @Named("stderr")
    PrintStream stderr() {
        return System.err;
    }
}
