// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.hkj;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.traversalsJavac;

import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeGenerator")
public class MaybeGeneratorTest {
  @Test
  @DisplayName("should generate a traversal for Maybe fields that draws no lint warning")
  void shouldGenerateCorrectTraversalForMaybe() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.QueryResult",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import org.higherkindedj.hkt.maybe.Maybe;
            import org.higherkindedj.hkt.Kind;

            @GenerateTraversals
            public record QueryResult(Maybe<String> result) {}
            """);

    final String expectedBody =
        """
        final Maybe<String> maybe = source.result();
        if (maybe.isJust()) {
          final var g_of_b = f.apply(maybe.get());
          return applicative.map(newValue -> new QueryResult(Maybe.just(newValue)), g_of_b);
        } else {
          return applicative.of(source);
        }
        """;

    var compilation = traversalsJavac().withOptions("-Xlint:all", "-Werror").compile(sourceFile);

    assertThat(compilation).succeededWithoutWarnings();
    assertGeneratedCodeContains(compilation, "com.example.QueryResultTraversals", expectedBody);
  }
}
