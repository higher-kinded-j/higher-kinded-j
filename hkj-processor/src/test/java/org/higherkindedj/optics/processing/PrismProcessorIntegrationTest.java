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
            public sealed interface GShape<T> permits GCircle, GTagged {}
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
    var compilation = javac().withProcessors(new PrismProcessor()).compile(shape, circle, tagged);

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
    // Nothing narrows uncheckedly: a subtype the hierarchy cannot pin is refused, not suppressed.
    assertGeneratedCodeDoesNotContain(compilation, "com.example.GShapePrisms", "@SuppressWarnings");
  }

  @Test
  @DisplayName("should refuse a permitted subtype the hierarchy cannot pin")
  void shouldRefuseASubtypeTheHierarchyCannotPin() {
    final var shape =
        JavaFileObjects.forSourceString(
            "com.example.UShape",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GeneratePrisms;

            @GeneratePrisms
            public sealed interface UShape<T> permits UPair {}
            """);
    final var pair =
        JavaFileObjects.forSourceString(
            "com.example.UPair",
            """
            package com.example;

            public record UPair<A, B>(A a, B b) implements UShape<A> {}
            """);

    var compilation = javac().withProcessors(new PrismProcessor()).compile(shape, pair);

    // B is pinned by nothing, so the prism would let two callers read one value at different types.
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("'UPair' declares [B], which 'UShape' does not bind");
    assertThat(compilation)
        .hadErrorContaining(
            "Bind [B] in the clause, as 'UShape<A, B>', giving 'UShape' a type parameter for each"
                + " one it has no room for, or write the prism by hand.");
  }

  @Test
  @DisplayName("a raw bound on a permitted subtype is answered on the prism factory")
  void rawBoundCompilesUnderWerror() {
    // The prism is written in the subtype's vocabulary, so the subtype's bounds land in the
    // factory's own type-parameter clause.
    var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Shape",
            """
            package com.example;
            import java.util.List;
            import org.higherkindedj.optics.annotations.GeneratePrisms;
            @GeneratePrisms
            @SuppressWarnings("rawtypes")
            public sealed interface Shape<T extends List> permits Shape.Tagged, Shape.Plain {
              record Tagged<T extends List>(T value) implements Shape<T> {}

              record Plain<T extends List>(String id) implements Shape<T> {}
            }
            """);
    var compilation =
        javac()
            .withProcessors(new PrismProcessor())
            .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
            .compile(sourceFile);

    assertThat(compilation).succeededWithoutWarnings();
    assertGeneratedCodeContains(
        compilation,
        "com.example.ShapePrisms",
        "@SuppressWarnings(\"rawtypes\") public static <T extends List> Prism<Shape<T>, Shape.Tagged<T>> tagged()");
  }

  @Test
  @DisplayName("a permitted inner class of a generic class is named under its enclosing arguments")
  void innerClassOfAGenericClassIsNamedUnderItsEnclosingArguments() {
    // Left without the enclosing class's arguments an inner class is raw, even where it declares
    // none of its own. An enclosing parameter the clause binds is declared, as Pinned's X is, and
    // one it leaves free is a wildcard: a Circle of some Shapes is all the instanceof establishes.
    var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Shapes",
            """
            package com.example;
            import java.util.List;
            import org.higherkindedj.optics.annotations.GeneratePrisms;
            @SuppressWarnings("rawtypes")
            public class Shapes<X extends List, Z> {
              @GeneratePrisms
              public sealed interface Shape
                  permits Shapes.Circle, Shapes.Square, Shapes.Mid.Deep, Shapes.Plain.Leaf {}

              public final class Circle implements Shape {}

              public static final class Square implements Shape {}

              public class Mid {
                public final class Deep implements Shape {}
              }

              public static class Plain {
                public final class Leaf implements Shape {}
              }

              @GeneratePrisms
              public sealed interface Tagged<T> permits Shapes.Pinned, Shapes.Box {}

              public final class Pinned implements Tagged<X> {}

              public final class Box<Y> implements Tagged<Y> {}
            }
            """);
    var compilation =
        javac()
            .withProcessors(new PrismProcessor())
            .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
            .compile(sourceFile);

    assertThat(compilation).succeededWithoutWarnings();
    assertGeneratedCodeContains(
        compilation,
        "com.example.ShapePrisms",
        "public static Prism<Shapes.Shape, Shapes<?, ?>.Circle> circle() { return Prism.of(source"
            + " -> source instanceof Shapes.Circle ? Optional.of((Shapes<?, ?>.Circle) source) :"
            + " Optional.empty(), value -> value); }");
    // A static member has no enclosing instance type, so it is named as it was before.
    assertGeneratedCodeContains(
        compilation,
        "com.example.ShapePrisms",
        "public static Prism<Shapes.Shape, Shapes.Square> square()");
    assertGeneratedCodeContains(
        compilation,
        "com.example.ShapePrisms",
        "public static Prism<Shapes.Shape, Shapes<?, ?>.Mid.Deep> deep()");
    // Leaf is an inner class too, but of a static class, so no link of its chain takes arguments.
    assertGeneratedCodeContains(
        compilation,
        "com.example.ShapePrisms",
        "public static Prism<Shapes.Shape, Shapes.Plain.Leaf> leaf()");
    // X is declared with its bound, so the bound's raw List is answered on the factory.
    assertGeneratedCodeContains(
        compilation,
        "com.example.TaggedPrisms",
        "@SuppressWarnings(\"rawtypes\") public static <X extends List> Prism<Shapes.Tagged<X>,"
            + " Shapes<X, ?>.Pinned> pinned()");
    assertGeneratedCodeContains(
        compilation,
        "com.example.TaggedPrisms",
        "public static <Y> Prism<Shapes.Tagged<Y>, Shapes<?, ?>.Box<Y>> box()");
  }

  @Test
  @DisplayName("should refuse an inner subtype bounded by an enclosing parameter nothing binds")
  void shouldRefuseAnInnerSubtypeBoundedByAnEnclosingParameterNothingBinds() {
    // X is written as a wildcard, so the prism has no X for Y's bound to name. Self's bound names X
    // too, though the source never spells it: an inner class is read under its enclosing arguments.
    // The refusals are the only errors, so no prism naming the missing X was written.
    var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Shapes",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GeneratePrisms;
            public class Shapes<X> {
              @GeneratePrisms
              public sealed interface Shape<T> permits Shapes.Box, Shapes.Self, Shapes.Ok {}

              public final class Box<Y extends X> implements Shape<Y> {}

              public final class Self<Y extends Self<Y>> implements Shape<Y> {}

              public final class Ok<Y> implements Shape<Y> {}
            }
            """);

    var compilation = javac().withProcessors(new PrismProcessor()).compile(sourceFile);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(2);
    assertThat(compilation)
        .hadErrorContaining(
            "The prism for 'Box' declares Y extends X, and 'Shape' does not bind [X].");
    assertThat(compilation)
        .hadErrorContaining(
            "The prism for 'Self' declares Y extends Shapes<X>.Self<Y>, and 'Shape' does not bind"
                + " [X].");
    assertThat(compilation).hadErrorContaining("Bind [X] in the clause, as 'Shape<Y, X>'");
  }
}
