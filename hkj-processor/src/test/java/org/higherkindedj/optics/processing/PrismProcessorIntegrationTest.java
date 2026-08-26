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

public class PrismProcessorIntegrationTest {

  @Test
  void shouldGeneratePrismsForSealedInterface() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Shape",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GeneratePrisms;
            import org.higherkindedj.optics.Prism;
            import java.util.Optional;

            @GeneratePrisms
            public sealed interface Shape {
                record Circle(double radius) implements Shape {}
                record Square(double side) implements Shape {}
            }
            """);

    final String expectedCirclePrism =
        """
        public static Prism<Shape, Shape.Circle> circle() {
            return Prism.of(
                source -> source instanceof Shape.Circle ? Optional.of((Shape.Circle) source) : Optional.empty(),
                value -> value
            );
        }
        """;

    final String expectedSquarePrism =
        """
        public static Prism<Shape, Shape.Square> square() {
            return Prism.of(
                source -> source instanceof Shape.Square ? Optional.of((Shape.Square) source) : Optional.empty(),
                value -> value
            );
        }
        """;

    var compilation = javac().withProcessors(new PrismProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();

    final String generatedClassName = "com.example.ShapePrisms";
    assertGeneratedCodeContains(compilation, generatedClassName, expectedCirclePrism);
    assertGeneratedCodeContains(compilation, generatedClassName, expectedSquarePrism);
  }

  @Test
  void shouldGenerateEmptyPrismsClassForPlainInterface() {
    // A non-sealed, non-enum interface is accepted by the processor but produces a Prisms class
    // with no factory methods (neither the sealed nor the enum body applies).
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Plain",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GeneratePrisms;

            @GeneratePrisms
            public interface Plain {}
            """);

    var compilation = javac().withProcessors(new PrismProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("com.example.PlainPrisms").isNotNull();
  }

  @Test
  @DisplayName("should name both sides of a prism for a generic sealed hierarchy")
  void shouldNameBothSidesForAGenericHierarchy() {
    final var shape =
        JavaFileObjects.forSourceString(
            "com.example.GShape",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GeneratePrisms;

            @GeneratePrisms
            public sealed interface GShape<T> permits GCircle, GTagged, GPair {}
            """);
    final var circle =
        JavaFileObjects.forSourceString(
            "com.example.GCircle",
            """
            package com.example;

            public record GCircle<T>(T tag) implements GShape<T> {}
            """);
    final var tagged =
        JavaFileObjects.forSourceString(
            "com.example.GTagged",
            """
            package com.example;

            public record GTagged(String label) implements GShape<String> {}
            """);
    final var pair =
        JavaFileObjects.forSourceString(
            "com.example.GPair",
            """
            package com.example;

            public record GPair<A, B>(A a, B b) implements GShape<A> {}
            """);

    var compilation =
        javac().withProcessors(new PrismProcessor()).compile(shape, circle, tagged, pair);

    // The @GeneratePrisms and @ImportOptics generators carry the same reading, so both are pinned;
    // the @ImportOptics half is GenericImportedTypeAxisTest.
    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(
        compilation,
        "com.example.GShapePrisms",
        "public static <T> Prism<GShape<T>, GCircle<T>> gCircle()");
    assertGeneratedCodeContains(
        compilation,
        "com.example.GShapePrisms",
        "public static Prism<GShape<String>, GTagged> gTagged()");
    assertGeneratedCodeContains(
        compilation,
        "com.example.GShapePrisms",
        "public static <A, B> Prism<GShape<A>, GPair<A, B>> gPair()");
    // Only the subtype carrying a parameter the hierarchy does not bind narrows uncheckedly.
    assertGeneratedCodeDoesNotContain(
        compilation,
        "com.example.GShapePrisms",
        "@SuppressWarnings(\"unchecked\") public static <T> Prism<GShape<T>");
  }
}
