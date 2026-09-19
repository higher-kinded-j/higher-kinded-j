// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.hkj;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.traversalsJavac;

import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EitherGenerator")
public class EitherGeneratorTest {
  @Test
  @DisplayName("should generate a traversal for Either fields that draws no lint warning")
  void shouldGenerateCorrectTraversalForEither() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Response",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import org.higherkindedj.hkt.either.Either;
            import org.higherkindedj.hkt.Kind;

            @GenerateTraversals
            public record Response(Either<String, Integer> data) {}
            """);

    final String expectedBody =
        """
        final Either<String, Integer> either = source.data();
        if (either.isRight()) {
          final var g_of_b = f.apply(either.getRight());
          return applicative.map(newValue -> new Response(Either.right(newValue)), g_of_b);
        } else {
          return applicative.of(source);
        }
        """;

    var compilation = traversalsJavac().withOptions("-Xlint:all", "-Werror").compile(sourceFile);

    assertThat(compilation).succeededWithoutWarnings();
    assertGeneratedCodeContains(compilation, "com.example.ResponseTraversals", expectedBody);
  }
}
