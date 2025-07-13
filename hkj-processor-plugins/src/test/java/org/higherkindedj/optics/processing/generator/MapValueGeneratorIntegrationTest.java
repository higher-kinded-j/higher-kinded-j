// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
import org.higherkindedj.optics.processing.TraversalProcessor;
import org.junit.jupiter.api.Test;

public class MapValueGeneratorIntegrationTest {
  @Test
  void shouldGenerateCorrectTraversalForMapValue() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Config",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.util.Map;
            import java.util.ArrayList;
            import java.util.function.Function;
            import java.util.stream.Collectors;
            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.optics.util.Traversals;

            @GenerateTraversals
            public record Config(String id, Map<String, Integer> properties) {}
            """);

    final String expectedBody =
        """
        final var sourceEntries = new ArrayList<>(source.properties().entrySet());
        final Function<Map.Entry<String, Integer>, Kind<F, Map.Entry<String, Integer>>> entryF =
            entry -> applicative.map(newValue -> Map.entry(entry.getKey(), newValue), f.apply(entry.getValue()));
        final var effectOfEntries = Traversals.traverseList(sourceEntries, entryF, applicative);
        final var effectOfMap = applicative.map(
            newEntries -> newEntries.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
            effectOfEntries);
        return applicative.map(newMap -> new Config(source.id(), newMap), effectOfMap);
        """;

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(compilation, "com.example.ConfigTraversals", expectedBody);
  }
}
