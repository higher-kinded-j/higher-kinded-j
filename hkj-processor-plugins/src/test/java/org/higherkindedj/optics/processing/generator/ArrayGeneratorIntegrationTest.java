// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
import org.higherkindedj.optics.processing.TraversalProcessor;
import org.junit.jupiter.api.Test;

public class ArrayGeneratorIntegrationTest {
  @Test
  void shouldGenerateCorrectTraversalForArray() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Game",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.util.Arrays;
            import java.util.stream.Collectors;
            import org.higherkindedj.optics.util.Traversals;

            @GenerateTraversals
            public record Game(String name, String[] players) {}
            """);

    final String expectedBody =
        """
        final var sourceList = Arrays.stream(source.players()).collect(Collectors.toList());
        final var effectOfList = Traversals.traverseList(sourceList, f, applicative);
        final var effectOfArray = applicative.map(newList -> newList.toArray(size -> new String[size]), effectOfList);
        return applicative.map(newArray -> new Game(source.name(), newArray), effectOfArray);
        """;

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(compilation, "com.example.GameTraversals", expectedBody);
  }
}
