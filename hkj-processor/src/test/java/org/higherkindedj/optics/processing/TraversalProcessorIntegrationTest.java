// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

public class TraversalProcessorIntegrationTest {

  @Test
  void shouldGenerateTraversalForRecordComponent() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Playlist",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.util.List;

            @GenerateTraversals
            public record Playlist(String name, List<String> songTitles) {}
            """);

    final String expectedTraversal =
        """
        public static Traversal<Playlist, String> songTitles() {
            return new Traversal<Playlist, String>() {
                @Override
                public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Playlist> modifyF(
                    Function<String, Kind<F, String>> f,
                    Playlist source,
                    Applicative<F> applicative
                ) {
                    final var effectOfList = Traversals.traverseList(source.songTitles(), f, applicative);
                    return applicative.map(newList -> new Playlist(source.name(), newList), effectOfList);
                }
            };
        }
        """;

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();

    final String generatedClassName = "com.example.PlaylistTraversals";
    assertGeneratedCodeContains(compilation, generatedClassName, expectedTraversal);
  }
}
