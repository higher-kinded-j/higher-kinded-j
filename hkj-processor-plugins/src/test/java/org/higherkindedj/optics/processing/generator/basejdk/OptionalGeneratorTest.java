// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.basejdk;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.traversalsJavac;

import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OptionalGenerator")
public class OptionalGeneratorTest {

  @Test
  @DisplayName("should generate a traversal for Optional fields that draws no lint warning")
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
          return applicative.map(newValue -> new User(source.name(), Optional.of(newValue)), g_of_b);
        } else {
          return applicative.of(source);
        }
        """;

    var compilation = traversalsJavac().withOptions("-Xlint:all", "-Werror").compile(sourceFile);

    assertThat(compilation).succeededWithoutWarnings();
    assertGeneratedCodeContains(compilation, "com.example.UserTraversals", expectedModifyFBody);
  }
}
