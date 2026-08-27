// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
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

  private static Compilation compileIso(String name, String body) {
    return javac()
        .withProcessors(new IsoProcessor())
        .compile(
            JavaFileObjects.forSourceString(
                "com.example." + name,
                """
                package com.example;

                import org.higherkindedj.optics.Iso;
                import org.higherkindedj.optics.annotations.GenerateIsos;

                %s
                """
                    .formatted(body)));
  }

  @Test
  @DisplayName("refuses a method the generated field cannot call")
  void refusesAMethodTheFieldCannotCall() {
    var takesArguments =
        compileIso(
            "Args",
            """
            public class Args {
                public record Box(String value) {}
                @GenerateIsos
                public static Iso<Box, String> boxIso(String prefix) {
                    return Iso.of(Box::value, Box::new);
                }
            }""");
    assertThat(takesArguments).failed();
    assertThat(takesArguments).hadErrorContaining("'boxIso' takes parameters");

    var notVisible =
        compileIso(
            "Hidden",
            """
            public class Hidden {
                public record Box(String value) {}
                @GenerateIsos
                private static Iso<Box, String> boxIso() { return Iso.of(Box::value, Box::new); }
            }""");
    assertThat(notVisible).failed();
    assertThat(notVisible).hadErrorContaining("cannot be reached from 'com.example'");

    // Reaching the method is not enough: the field writes its own type out, so what the iso names
    // has to be visible where the field is declared.
    var typeArgumentNotVisible =
        compileIso(
            "Conv",
            """
            public class Conv {
                private record Secret(String v) {}
                @GenerateIsos
                public static Iso<Secret, String> secretIso() {
                    return Iso.of(Secret::v, Secret::new);
                }
            }""");
    assertThat(typeArgumentNotVisible).failed();
    assertThat(typeArgumentNotVisible)
        .hadErrorContaining("names 'Secret', which cannot be reached from 'com.example'");

    // And it looks inside the arguments, not only at their heads.
    var nestedArgumentNotVisible =
        compileIso(
            "Nested",
            """
            public class Nested {
                private record Secret(String v) {}
                public record Box<A>(A value) {}
                @GenerateIsos
                public static Iso<Box<Secret>, String> boxIso() { return null; }
            }""");
    assertThat(nestedArgumentNotVisible).failed();
    assertThat(nestedArgumentNotVisible).hadErrorContaining("names 'Secret'");

    // Including through a wildcard bound, which is written out with the rest of the type.
    var wildcardBoundNotVisible =
        compileIso(
            "Wild",
            """
            public class Wild {
                private interface Secret {}
                public record Box<A>(A value) {}
                @GenerateIsos
                public static Iso<Box<? extends Secret>, String> boxIso() { return null; }
            }""");
    assertThat(wildcardBoundNotVisible).failed();
    assertThat(wildcardBoundNotVisible).hadErrorContaining("names 'Secret'");

    // The enclosing type has to be visible too: the field names it to make the call.
    var enclosingNotVisible =
        compileIso(
            "Outer",
            """
            public class Outer {
                public record Box(String value) {}
                private static class Nested {
                    @GenerateIsos
                    public static Iso<Box, String> boxIso() { return Iso.of(Box::value, Box::new); }
                }
            }""");
    assertThat(enclosingNotVisible).failed();
    assertThat(enclosingNotVisible).hadErrorContaining("cannot be reached from 'com.example'");

    // Package access is enough when the field lands in that same package.
    var packagePrivateSamePackage =
        compileIso(
            "Near",
            """
            public class Near {
                public record Box(String value) {}
                @GenerateIsos
                static Iso<Box, String> boxIso() { return Iso.of(Box::value, Box::new); }
            }""");
    assertThat(packagePrivateSamePackage).succeeded();

    // targetPackage moves the field away from what package access reaches.
    var packagePrivateAcrossPackages =
        compileIso(
            "Moved",
            """
            public class Moved {
                public record Box(String value) {}
                @GenerateIsos(targetPackage = "com.other")
                static Iso<Box, String> boxIso() { return Iso.of(Box::value, Box::new); }
            }""");
    assertThat(packagePrivateAcrossPackages).failed();
    assertThat(packagePrivateAcrossPackages)
        .hadErrorContaining("cannot be reached from 'com.other'");
  }

  @Test
  @DisplayName("refuses a return type it cannot read two arguments off, without crashing")
  void refusesAReturnTypeItCannotRead() {
    for (String returned :
        java.util.List.of(
            "public static void boxIso() {}",
            "public static int boxIso() { return 0; }",
            "public static String[] boxIso() { return null; }",
            "public static java.util.Map<String, Integer> boxIso() { return null; }")) {
      var compilation =
          compileIso(
              "Ret",
              """
              public class Ret {
                  @GenerateIsos
                  %s
              }"""
                  .formatted(returned));

      // Every one of these used to reach a cast: void, primitive and array threw
      // ClassCastException out of the processor with no diagnostic at all. A raw Iso reached the
      // arity check instead, and keeps its own case in ProcessorCoverageTest.
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("does not return an Iso with both type arguments");
    }
  }

  @Test
  @DisplayName("sees a type variable through every layer it can be buried in")
  void seesATypeVariableThroughEveryLayer() {
    // One method per layer the walk has an arm for. Each is refused on its own, so a layer the
    // walk cannot see through would generate a field naming the variable it missed.
    for (String returned :
        java.util.List.of(
            "Iso<Box<T[]>, String>",
            "Iso<Box<? extends T>, String>",
            "Iso<Box<? super T>, String>",
            "Iso<Outer<T>.Inner, String>",
            "Iso<Box<java.util.List<T>>, String>")) {
      var compilation =
          compileIso(
              "Layers",
              """
              public class Layers {
                  public record Box<A>(A value) {}
                  public static class Outer<A> { public class Inner {} }
                  @GenerateIsos
                  public static <T> %s buried() { return null; }
              }"""
                  .formatted(returned));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("the iso returned by 'buried' names a type variable");
    }

    // The controls: a wildcard with no bound at all, and bounds naming no variable, are layers
    // the walk has to see all the way through and find nothing.
    for (String returned :
        java.util.List.of(
            "Iso<Box<String[]>, String>",
            "Iso<Box<?>, String>",
            "Iso<Box<? extends CharSequence>, String>",
            "Iso<Box<? super String>, String>")) {
      var concrete =
          compileIso(
              "Plain",
              """
              public class Plain {
                  public record Box<A>(A value) {}
                  @GenerateIsos
                  public static %s buried() { return null; }
              }"""
                  .formatted(returned));
      assertThat(concrete).succeeded();
    }
  }
}
