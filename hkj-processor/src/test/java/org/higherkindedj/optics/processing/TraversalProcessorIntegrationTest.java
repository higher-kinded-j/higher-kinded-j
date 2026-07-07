// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;

import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
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

  @Test
  @DisplayName("a component whose generator focus index exceeds its type arguments is skipped")
  void shouldSkipComponentWhenGeneratorFocusIndexExceedsTypeArguments() {
    // The test-scope BoxIndexOneGenerator supports com.example.hkjtest.Box but focuses on type
    // argument index 1, which a Box<T> never has, so the traversal method is skipped.
    final var markerSource =
        JavaFileObjects.forSourceString(
            "com.example.hkjtest.Box",
            """
            package com.example.hkjtest;

            public class Box<T> {}
            """);

    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.BoxRecord",
            """
            package com.example;

            import com.example.hkjtest.Box;
            import org.higherkindedj.optics.annotations.GenerateTraversals;

            @GenerateTraversals
            public record BoxRecord(Box<String> boxed) {}
            """);

    var compilation =
        javac().withProcessors(new TraversalProcessor()).compile(markerSource, sourceFile);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("com.example.BoxRecordTraversals").isNotNull();
    assertGeneratedCodeDoesNotContain(compilation, "com.example.BoxRecordTraversals", "boxed");
  }

  @Test
  @DisplayName("a component that is neither an array nor a declared type is skipped")
  void shouldSkipComponentThatIsNeitherArrayNorDeclared() {
    // The test-scope TypeVariableGenerator supports type variables named TRAVMARKER, steering a
    // type-variable component into createTraversalMethod, which cannot handle it and skips it.
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.VarRecord",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;

            @GenerateTraversals
            public record VarRecord<TRAVMARKER>(TRAVMARKER value) {}
            """);

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("com.example.VarRecordTraversals").isNotNull();
    assertGeneratedCodeDoesNotContain(compilation, "com.example.VarRecordTraversals", "value");
  }
}
