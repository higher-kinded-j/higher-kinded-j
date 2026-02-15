// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
import org.higherkindedj.optics.processing.TraversalProcessor;
import org.junit.jupiter.api.Test;

public class EitherGeneratorIntegrationTest {
  @Test
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
          @SuppressWarnings("unchecked") final var g_of_b_casted = (Kind<F, Integer>) g_of_b;
          return applicative.map(newValue -> new Response(Either.right(newValue)), g_of_b_casted);
        } else {
          return applicative.of(source);
        }
        """;

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(compilation, "com.example.ResponseTraversals", expectedBody);
  }
}
