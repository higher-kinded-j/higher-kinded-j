// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
import org.higherkindedj.optics.processing.TraversalProcessor;
import org.junit.jupiter.api.Test;

public class OptionalGeneratorIntegrationTest {

  @Test
  void shouldGenerateCorrectTraversalForOptional() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.User",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.util.Optional;
            import org.higherkindedj.hkt.Kind;

            @GenerateTraversals
            public record User(String name, Optional<String> email) {}
            """);

    final String expectedModifyFBody =
        """
        final Optional<String> optional = source.email();
        if (optional.isPresent()) {
          final var g_of_b = f.apply(optional.get());
          @SuppressWarnings("unchecked") final var g_of_b_casted = (Kind<F, String>) g_of_b;
          return applicative.map(newValue -> new User(source.name(), Optional.of(newValue)), g_of_b_casted);
        } else {
          return applicative.of(source);
        }
        """;

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(compilation, "com.example.UserTraversals", expectedModifyFBody);
  }
}
