// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.eclipse;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
import org.higherkindedj.optics.processing.TraversalProcessor;
import org.junit.jupiter.api.Test;

public class EclipseImmutableSortedSetGeneratorTest {
  @Test
  void shouldGenerateCorrectTraversal() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Article",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import org.eclipse.collections.api.set.sorted.ImmutableSortedSet;

            @GenerateTraversals
            public record Article(long id, ImmutableSortedSet<String> keywords) {}
            """);

    final String expectedBody =
        """
        final var sourceList = source.keywords().into(new ArrayList<>(source.keywords().size()));
        final var effectOfList = Traversals.traverseList(sourceList, f, applicative);
        final var effectOfSet = applicative.map(newList -> Objects.isNull(source.keywords().comparator()) ? SortedSets.immutable.ofAll(newList) : SortedSets.immutable.ofAll(source.keywords().comparator(), newList), effectOfList);
        return applicative.map(converted -> new Article(source.id(), converted), effectOfSet);
        """;

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(compilation, "com.example.ArticleTraversals", expectedBody);
  }
}
