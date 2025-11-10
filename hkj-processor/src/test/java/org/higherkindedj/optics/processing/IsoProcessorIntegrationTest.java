// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

public class IsoProcessorIntegrationTest {

  @Test
  void shouldGenerateIsoFromAnnotatedMethod() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.PointConverters",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateIsos;
            import org.higherkindedj.optics.Iso;
            import org.higherkindedj.hkt.tuple.Tuple;
            import org.higherkindedj.hkt.tuple.Tuple2;

            public class PointConverters {

                public record Point(int x, int y) {}

                @GenerateIsos
                public static Iso<Point, Tuple2<Integer, Integer>> pointToTuple() {
                    return Iso.of(
                        point -> Tuple.of(point.x(), point.y()),
                        tuple -> new Point(tuple._1(), tuple._2())
                    );
                }
            }
            """);

    // The processor should generate a static final field in a new class,
    // initialised by calling the annotated method.
    final String expectedIsoField =
        """
        public static final Iso<PointConverters.Point, Tuple2<Integer, Integer>> pointToTuple = PointConverters.pointToTuple();
        """;

    var compilation = javac().withProcessors(new IsoProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();

    // Verify the generated class and its content
    final String generatedClassName = "com.example.PointConvertersIsos";
    assertGeneratedCodeContains(compilation, generatedClassName, expectedIsoField);
  }
}
