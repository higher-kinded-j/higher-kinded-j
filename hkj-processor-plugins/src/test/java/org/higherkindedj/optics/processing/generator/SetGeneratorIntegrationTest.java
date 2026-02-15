// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
import org.higherkindedj.optics.processing.TraversalProcessor;
import org.junit.jupiter.api.Test;

public class SetGeneratorIntegrationTest {
  @Test
  void shouldGenerateCorrectTraversalForSet() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Article",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.util.Set;
            import java.util.ArrayList;
            import java.util.stream.Collectors;
            import org.higherkindedj.optics.util.Traversals;

            @GenerateTraversals
            public record Article(long id, Set<String> keywords) {}
            """);

    final String expectedBody =
        """
        final var sourceList = new ArrayList<>(source.keywords());
        final var effectOfList = Traversals.traverseList(sourceList, f, applicative);
        final var effectOfSet = applicative.map(newList -> newList.stream().collect(Collectors.toSet()), effectOfList);
        return applicative.map(newSet -> new Article(source.id(), newSet), effectOfSet);
        """;

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(compilation, "com.example.ArticleTraversals", expectedBody);
  }
}
