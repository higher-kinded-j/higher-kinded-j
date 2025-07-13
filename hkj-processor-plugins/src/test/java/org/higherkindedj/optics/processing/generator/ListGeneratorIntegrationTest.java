// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
import org.higherkindedj.optics.processing.TraversalProcessor;
import org.junit.jupiter.api.Test;

public class ListGeneratorIntegrationTest {
  @Test
  void shouldGenerateCorrectTraversalForList() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Product",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.util.List;
            import org.higherkindedj.optics.util.Traversals;

            @GenerateTraversals
            public record Product(String sku, List<String> tags) {}
            """);

    final String expectedBody =
        """
        final var effectOfList = Traversals.traverseList(source.tags(), f, applicative);
        return applicative.map(newList -> new Product(source.sku(), newList), effectOfList);
        """;

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(compilation, "com.example.ProductTraversals", expectedBody);
  }
}
