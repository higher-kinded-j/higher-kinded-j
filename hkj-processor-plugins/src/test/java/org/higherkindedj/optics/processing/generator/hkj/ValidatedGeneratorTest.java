// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.hkj;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.traversalsJavac;

import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedGenerator")
public class ValidatedGeneratorTest {
  @Test
  @DisplayName("should generate a traversal for Validated fields that draws no lint warning")
  void shouldGenerateCorrectTraversalForValidated() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Input",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.hkt.Kind;

            @GenerateTraversals
            public record Input(Validated<String, String> value) {}
            """);

    final String expectedBody =
        """
        final Validated<String, String> validated = source.value();
        if (validated.isValid()) {
          final var g_of_b = f.apply(validated.get());
          return applicative.map(newValue -> new Input(Validated.valid(newValue)), g_of_b);
        } else {
          return applicative.of(source);
        }
        """;

    var compilation = traversalsJavac().withOptions("-Xlint:all", "-Werror").compile(sourceFile);

    assertThat(compilation).succeededWithoutWarnings();
    assertGeneratedCodeContains(compilation, "com.example.InputTraversals", expectedBody);
  }
}
