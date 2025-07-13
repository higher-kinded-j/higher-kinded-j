// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
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
}
