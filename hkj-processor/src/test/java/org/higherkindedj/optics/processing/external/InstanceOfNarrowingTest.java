// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.ImportOpticsProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What an {@code @InstanceOf} test is allowed to promise about its target's type arguments.
 *
 * <p>The annotation carries a class constant, which is raw, and the generated {@code instanceof}
 * runs after erasure. So a prism may only promise the arguments the source type pins down: {@code
 * Circle<X> implements Shape<X>} narrowed from {@code Shape<U>} pins {@code X}, while {@code
 * Circle<X> extends Shape} narrowed from a {@code Shape} that declares nothing pins none of it and
 * would hand any instantiation back as the one the caller asked for (issue #733).
 *
 * <p>Every test that expects generated code compiles the generated source under the lints a
 * consuming build turns on, so a test earns its "no suppression needed" by javac agreeing.
 */
@DisplayName("@InstanceOf narrowing")
class InstanceOfNarrowingTest {

  /** A base that declares no type parameters, so it can pin none of its subtypes'. */
  private static final JavaFileObject SHAPE =
      JavaFileObjects.forSourceString(
          "com.external.Shape",
          """
          package com.external;

          public class Shape {}
          """);

  /** A parameterised subtype of a base that says nothing about the parameter. */
  private static final JavaFileObject CIRCLE =
      JavaFileObjects.forSourceString(
          "com.external.Circle",
          """
          package com.external;

          public class Circle<X> extends Shape {
              private final X tag;
              public Circle(X tag) { this.tag = tag; }
              public X tag() { return tag; }
          }
          """);

  /** A base that carries the parameter its subtypes are reached under. */
  private static final JavaFileObject NODE =
      JavaFileObjects.forSourceString(
          "com.external.Node",
          """
          package com.external;

          public class Node<X> {}
          """);

  private static final JavaFileObject LEAF =
      JavaFileObjects.forSourceString(
          "com.external.Leaf",
          """
          package com.external;

          public class Leaf<X> extends Node<X> {}
          """);

  private static final JavaFileObject PAIR =
      JavaFileObjects.forSourceString(
          "com.external.Pair",
          """
          package com.external;

          public class Pair<A, B> {}
          """);

  /** A subtype declaring one parameter more than the base can pin. */
  private static final JavaFileObject WEDGE =
      JavaFileObjects.forSourceString(
          "com.external.Wedge",
          """
          package com.external;

          public class Wedge<X, Y> extends Node<X> {}
          """);

  /** A subtype reached through an array of its own parameter. */
  private static final JavaFileObject GRID =
      JavaFileObjects.forSourceString(
          "com.external.Grid",
          """
          package com.external;

          public class Grid<X> extends Node<X[]> {}
          """);

  /** A subtype asking for one parameter in two places at once. */
  private static final JavaFileObject TWIN =
      JavaFileObjects.forSourceString(
          "com.external.Twin",
          """
          package com.external;

          public class Twin<X> extends Node<Pair<X, X>> {}
          """);

  /**
   * Compiles a spec interface body, with the generated source held to the lints a consuming build
   * turns on: a narrowing the processor could not justify must not reach the user as a warning it
   * has silenced on their behalf.
   */
  private Compilation compile(String specBody) {
    var specInterface =
        JavaFileObjects.forSourceString(
            "com.myapp.SubjectOpticsSpec",
            """
            package com.myapp;

            import com.external.Circle;
            import com.external.Grid;
            import com.external.Leaf;
            import com.external.Node;
            import com.external.Pair;
            import com.external.Shape;
            import com.external.Twin;
            import com.external.Wedge;
            import org.higherkindedj.optics.Prism;
            import org.higherkindedj.optics.annotations.ImportOptics;
            import org.higherkindedj.optics.annotations.InstanceOf;
            import org.higherkindedj.optics.annotations.OpticsSpec;

            @ImportOptics
            %s
            """
                .formatted(specBody));

    return javac()
        .withProcessors(new ImportOpticsProcessor())
        .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
        .compile(SHAPE, CIRCLE, NODE, LEAF, PAIR, WEDGE, TWIN, GRID, specInterface);
  }

  @Nested
  @DisplayName("the source pins the argument")
  class Pinned {

    @Test
    @DisplayName("checks the argument in the test, so nothing needs suppressing")
    void checksThePinnedArgument() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Leaf.class)
                  Prism<Node<U>, Leaf<U>> leaf();
              }""");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Leaf<U>");
      assertGeneratedCodeDoesNotContain(
          compilation, "com.myapp.SubjectOptics", "@SuppressWarnings");
    }

    @Test
    @DisplayName("pins through the arguments the subtype's own supertype names")
    void pinsThroughTheSupertypeInstantiation() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<Pair<U, U>>> {
                  @InstanceOf(Twin.class)
                  Prism<Node<Pair<U, U>>, Twin<U>> twin();
              }""");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Twin<U>");
    }

    @Test
    @DisplayName("pins through an array the subtype's supertype is reached under")
    void pinsThroughAnArrayComponent() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Node<String[]>> {
                  @InstanceOf(Grid.class)
                  Prism<Node<String[]>, Grid<String>> grid();
              }""");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Grid<String>");
    }

    @Test
    @DisplayName("pins a concrete instantiation just as it pins a parameter")
    void pinsAConcreteInstantiation() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Node<String>> {
                  @InstanceOf(Leaf.class)
                  Prism<Node<String>, Leaf<String>> leaf();
              }""");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Leaf<String>");
    }

    @Test
    @DisplayName("leaves a non-generic target exactly as the annotation names it")
    void leavesANonGenericTargetAlone() {
      var square =
          JavaFileObjects.forSourceString(
              "com.external.Square",
              """
              package com.external;

              public class Square extends Shape {}
              """);
      var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.SubjectOpticsSpec",
              """
              package com.myapp;

              import com.external.Shape;
              import com.external.Square;
              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface SubjectOpticsSpec extends OpticsSpec<Shape> {
                  @InstanceOf(Square.class)
                  Prism<Shape, Square> square();
              }""");

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(SHAPE, square, specInterface);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Square");
    }

    @Test
    @DisplayName("leaves a reifiable array target alone, which checks itself")
    void leavesAnArrayTargetAlone() {
      var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.SubjectOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface SubjectOpticsSpec extends OpticsSpec<Object> {
                  @InstanceOf(int[].class)
                  Prism<Object, int[]> counts();
              }""");

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(specInterface);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof int[]");
    }
  }

  @Nested
  @DisplayName("the source pins nothing")
  class Free {

    @Test
    @DisplayName("rejects a focus that asks for an argument erasure cannot check")
    void rejectsAnArgumentTheTestCannotCheck() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<T> extends OpticsSpec<Shape> {
                  @InstanceOf(Circle.class)
                  Prism<Shape, Circle<T>> circle();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("declares its focus as 'Circle<T>', which the test cannot narrow to");
      assertThat(compilation).hadErrorContaining("'Shape' pins nothing to X");
      assertThat(compilation).hadErrorContaining("@MatchWhen");
      assertThat(compilation).hadErrorContaining("declare the focus as 'Circle<?>'");
      // One problem, one error: a rejected hint must not also draw the missing-hint error.
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("accepts the wildcard focus the test does earn")
    void acceptsTheWildcardFocus() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Shape> {
                  @InstanceOf(Circle.class)
                  Prism<Shape, Circle<?>> circle();
              }""");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Circle<?>");
      assertGeneratedCodeDoesNotContain(
          compilation, "com.myapp.SubjectOptics", "@SuppressWarnings");
    }

    @Test
    @DisplayName("accepts a focus that asks nothing of the argument at all")
    void acceptsASupertypeFocus() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Shape> {
                  @InstanceOf(Circle.class)
                  Prism<Shape, Shape> circle();
              }""");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Circle<?>");
    }

    @Test
    @DisplayName("rejects the one argument the source cannot reach, keeping the ones it can")
    void rejectsOnlyTheUnreachableArgument() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Wedge.class)
                  Prism<Node<U>, Wedge<U, String>> wedge();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'Node<U>' pins nothing to Y");
      assertThat(compilation).hadErrorContaining("declare the focus as 'Wedge<U, ?>'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("accepts a wildcard in the position the source cannot reach")
    void acceptsAWildcardInTheUnreachablePosition() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Wedge.class)
                  Prism<Node<U>, Wedge<U, ?>> wedge();
              }""");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Wedge<U, ?>");
    }

    @Test
    @DisplayName("pins nothing from a bare parameter, where the target reaches a declared type")
    void pinsNothingFromABareParameterThroughADeclaredType() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Twin.class)
                  Prism<Node<U>, Leaf<U>> twin();
              }""");

      // Twin reaches Node as Node<Pair<X, X>>, and a source carrying a bare parameter offers that
      // shape nothing to match, so the test earns only the wildcard the diagnostic names.
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("narrows to 'Twin<?>', which is not a 'Leaf<U>'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("pins nothing from a bare parameter, where the target reaches an array")
    void pinsNothingFromABareParameterThroughAnArray() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Grid.class)
                  Prism<Node<U>, Leaf<U>> grid();
              }""");

      // Grid reaches Node as Node<X[]>, and the same holds one layer down.
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("narrows to 'Grid<?>', which is not a 'Leaf<U>'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("pins nothing when the target asks for one argument in two places at once")
    void pinsNothingWhenTheTargetAsksForTwo() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U, V> extends OpticsSpec<Node<Pair<U, V>>> {
                  @InstanceOf(Twin.class)
                  Prism<Node<Pair<U, V>>, Twin<U>> twin();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("pins nothing to X");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("pins nothing when the source names a different class of the same arity")
    void pinsNothingFromADifferentClassOfTheSameArity() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<Wedge<U, U>>> {
                  @InstanceOf(Twin.class)
                  Prism<Node<Wedge<U, U>>, Twin<U>> twin();
              }""");

      // Twin reaches Node as Node<Pair<X, X>>. Wedge takes two arguments as well, so a match that
      // read position by position without checking the class would pin X to U and write a test
      // javac refuses.
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'Node<Wedge<U, U>>' pins nothing to X");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("pins nothing from a raw source type")
    void pinsNothingFromARawSource() {
      var compilation =
          compile(
              """
              @SuppressWarnings("rawtypes")
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node> {
                  @InstanceOf(Leaf.class)
                  Prism<Node, Leaf<U>> leaf();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("pins nothing to X");
      assertThat(compilation).hadErrorCount(1);
    }
  }

  @Nested
  @DisplayName("the test cannot name the target")
  class Unnameable {

    @Test
    @DisplayName("rejects a parameterised member of a generic type")
    void rejectsAParameterisedMemberOfAGenericType() {
      var outer =
          JavaFileObjects.forSourceString(
              "com.external.Outer",
              """
              package com.external;

              public class Outer<X> {
                  public class Inner<Y> extends Node<Y> {}
              }
              """);
      var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.SubjectOpticsSpec",
              """
              package com.myapp;

              import com.external.Node;
              import com.external.Outer;
              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Outer.Inner.class)
                  Prism<Node<U>, Outer<?>.Inner<U>> inner();
              }""");

      // Node pins Inner's Y perfectly well. It is the enclosing Outer<X> that has nowhere to be
      // written, and 'Outer.Inner<U>' is not a type - so there is no test to generate.
      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(NODE, outer, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("names 'Outer.Inner', which carries type parameters of its own");
      assertThat(compilation).hadErrorContaining("Declare 'Inner' static");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("leaves a member of a non-generic type alone, which can be named")
    void leavesAMemberOfANonGenericTypeAlone() {
      var outer =
          JavaFileObjects.forSourceString(
              "com.external.Outer",
              """
              package com.external;

              public class Outer {
                  public class Inner<Y> extends Node<Y> {}
              }
              """);
      var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.SubjectOpticsSpec",
              """
              package com.myapp;

              import com.external.Node;
              import com.external.Outer;
              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Outer.Inner.class)
                  Prism<Node<U>, Outer.Inner<U>> inner();
              }""");

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(NODE, outer, specInterface);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Outer.Inner<U>");
    }
  }

  @Nested
  @DisplayName("the test narrows to something the focus does not accept")
  class Mismatched {

    @Test
    @DisplayName("rejects a target the focus type is not a supertype of")
    void rejectsATargetTheFocusDoesNotAccept() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Leaf.class)
                  Prism<Node<U>, Wedge<U, ?>> wedge();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("narrows to 'Leaf<U>', which is not a 'Wedge<U, ?>'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("names the mismatch, not the free argument, when the classes are unrelated")
    void namesTheMismatchWhenTheClassesAreUnrelated() {
      var square =
          JavaFileObjects.forSourceString(
              "com.external.Square",
              """
              package com.external;

              public class Square extends Shape {}
              """);
      var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.SubjectOpticsSpec",
              """
              package com.myapp;

              import com.external.Circle;
              import com.external.Shape;
              import com.external.Square;
              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface SubjectOpticsSpec extends OpticsSpec<Shape> {
                  @InstanceOf(Circle.class)
                  Prism<Shape, Square> square();
              }""");

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(SHAPE, CIRCLE, square, specInterface);

      assertThat(compilation).failed();
      // Circle's X is free here too, but the wildcard focus is no remedy for a Square.
      assertThat(compilation).hadErrorContaining("narrows to 'Circle<?>', which is not a 'Square'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("rejects a focus asking for an argument the source pins to something else")
    void rejectsAnArgumentPinnedToSomethingElse() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<String>> {
                  @InstanceOf(Leaf.class)
                  Prism<Node<String>, Leaf<U>> leaf();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("narrows to 'Leaf<String>', which is not a 'Leaf<U>'");
      assertThat(compilation).hadErrorCount(1);
    }
  }
}
