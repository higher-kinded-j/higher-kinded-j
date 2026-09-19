// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.hkj;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.traversalsJavac;

import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TryGenerator")
public class TryGeneratorTest {
  @Test
  @DisplayName("should generate a traversal for Try fields that draws no lint warning")
  void shouldGenerateCorrectTraversalForTry() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Computation",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import org.higherkindedj.hkt.trymonad.Try;
            import org.higherkindedj.hkt.Kind;

            @GenerateTraversals
            public record Computation(Try<Double> result) {}
            """);

    final String expectedBody =
        """
        final Try<Double> tryA = source.result();
        return tryA.foldFailureFirst(
            cause -> {
                return applicative.of(source);
            },
            successValue -> {
                final var g_of_b = f.apply(successValue);
                return applicative.map(newValue -> new Computation(Try.success(newValue)), g_of_b);
            }
        );
        """;

    var compilation = traversalsJavac().withOptions("-Xlint:all", "-Werror").compile(sourceFile);

    assertThat(compilation).succeededWithoutWarnings();
    assertGeneratedCodeContains(compilation, "com.example.ComputationTraversals", expectedBody);
  }
}
