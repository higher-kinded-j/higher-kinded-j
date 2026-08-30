// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.basejdk;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
import org.higherkindedj.optics.processing.TraversalProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CollectionGenerator")
public class CollectionGeneratorTest {
  @Test
  @DisplayName("should generate correct traversal for Collection fields")
  void shouldGenerateCorrectTraversalForCollection() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Job",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.util.Collection;

            @GenerateTraversals
            public record Job(long id, Collection<String> tags) {}
            """);

    final String expectedBody =
        """
        final var effectOfCollection = Traversals.traverseCollection(source.tags(), f, applicative);
        return applicative.map(newCollection -> new Job(source.id(), newCollection), effectOfCollection);
        """;

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertThat(compilation).hadWarningCount(0);
    assertGeneratedCodeContains(compilation, "com.example.JobTraversals", expectedBody);
  }
}
