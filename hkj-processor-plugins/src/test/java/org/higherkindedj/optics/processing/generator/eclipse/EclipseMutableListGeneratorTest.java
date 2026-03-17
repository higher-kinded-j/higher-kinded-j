// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.eclipse;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
import org.higherkindedj.optics.processing.TraversalProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EclipseMutableListGenerator")
public class EclipseMutableListGeneratorTest {
  @Test
  @DisplayName("should generate correct traversal for MutableList fields")
  void shouldGenerateCorrectTraversal() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Article",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import org.eclipse.collections.api.list.MutableList;

            @GenerateTraversals
            public record Article(long id, MutableList<String> keywords) {}
            """);

    final String expectedBody =
        """
        final var sourceList = source.keywords().into(new ArrayList<>(source.keywords().size()));
        final var effectOfList = Traversals.traverseList(sourceList, f, applicative);
        final var effectOfConvertBack = applicative.map(newList -> Lists.mutable.ofAll(newList), effectOfList);
        return applicative.map(converted -> new Article(source.id(), converted), effectOfConvertBack);
        """;

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(compilation, "com.example.ArticleTraversals", expectedBody);
  }
}
