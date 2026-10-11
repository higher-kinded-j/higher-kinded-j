// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
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

  /** A subtype with no parameters, whose own clause writes a primitive array. */
  private static final JavaFileObject COUNTS =
      JavaFileObjects.forSourceString(
          "com.external.Counts",
          """
          package com.external;

          public class Counts extends Node<int[]> {}
          """);

  /** A subtype asking for one parameter in three places, one of them a level further down. */
  private static final JavaFileObject TRIPLE =
      JavaFileObjects.forSourceString(
          "com.external.Triple",
          """
          package com.external;

          public class Triple<X> extends Node<Pair<X, Pair<X, X>>> {}
          """);

  /** A subtype whose own clause writes a wildcard inside its argument. */
  private static final JavaFileObject LOOSE =
      JavaFileObjects.forSourceString(
          "com.external.Loose",
          """
          package com.external;

          public class Loose extends Node<Pair<?, String>> {}
          """);

  /** A subtype whose own clause writes a wildcard javac reads as {@code Object}. */
  private static final JavaFileObject LOWER =
      JavaFileObjects.forSourceString(
          "com.external.Lower",
          """
          package com.external;

          public class Lower<X> extends Node<Pair<X, ? super Object>> {}
          """);

  /** A subtype whose own clause writes a wildcard among its enclosing class's arguments. */
  private static final JavaFileObject WILD =
      JavaFileObjects.forSourceString(
          "com.external.Wild",
          """
          package com.external;

          public class Wild extends Outer<?>.Base {
              public Wild(Outer<?> outer) {
                  outer.super();
              }
          }
          """);

  /** A subtype whose own clause writes {@code Object} beside its parameter. */
  private static final JavaFileObject UPPER =
      JavaFileObjects.forSourceString(
          "com.external.Upper",
          """
          package com.external;

          public class Upper<X> extends Node<Pair<X, Object>> {}
          """);

  /** A subtype whose own clause writes a class beside an array of its parameter. */
  private static final JavaFileObject STRICT =
      JavaFileObjects.forSourceString(
          "com.external.Strict",
          """
          package com.external;

          public class Strict<X> extends Node<Pair<X[], String>> {}
          """);

  /** A subtype asking for one parameter in two places at once. */
  private static final JavaFileObject TWIN =
      JavaFileObjects.forSourceString(
          "com.external.Twin",
          """
          package com.external;

          public class Twin<X> extends Node<Pair<X, X>> {}
          """);

  /** A generic class whose inner classes reach the base in each way a member can. */
  private static final JavaFileObject OUTER =
      JavaFileObjects.forSourceString(
          "com.external.Outer",
          """
          package com.external;

          public class Outer<X> {
              public class Plain extends Node<String> {}

              public class Inner<Y> extends Node<Y> {}

              public class Pin extends Node<X> {}

              public class Rows extends Node<X[]> {}

              public class Base {}

              public class Sub extends Base {}

              public class Bounded<Y extends X> extends Node<Y> {}
          }
          """);

  /** A generic class with a parameter whose bound names another, and one bounded by it. */
  private static final JavaFileObject DEPENDENT =
      JavaFileObjects.forSourceString(
          "com.external.Dependent",
          """
          package com.external;

          import java.util.List;

          public class Dependent<X, Z extends List<X>, W extends X> {
              public class InZ extends Node<Z> {}

              public class InW extends Node<W> {}

              public class Both extends Node<Pair<X, W>> {}
          }
          """);

  /** Classes whose parameters bound the arguments a test may pin, in each way javac reads one. */
  private static final JavaFileObject LIMITS =
      JavaFileObjects.forSourceString(
          "com.external.Limits",
          """
          package com.external;

          import java.io.Serializable;
          import java.util.List;

          public class Limits {
              public static class Numeric<Y extends Number> extends Node<Y> {}

              public static class Ordered<Y extends Comparable<Y>> extends Node<Y> {}

              public static class Measured<Y extends Number & Comparable<Y>> extends Node<Y> {}

              public static class NumberLists<Y extends List<? extends Number>> extends Node<Y> {}

              public static class Comparing<A extends Comparable<A>, B extends List<? extends A>>
                  extends Node<B> {}

              public static class Partly<
                      A extends Number & Serializable, Y extends Number & Comparable<? extends A>>
                  extends Node<Y> {}

              public static class Same<A, B extends A> extends Node<Pair<A, B>> {}

              public static class Clashing<A extends Integer, B extends String>
                  extends Node<Pair<A, B>> {}

              public static class Mixed<A extends Number, B extends CharSequence>
                  extends Node<Pair<A, B>> {}

              public static class Maximal<Y extends Object & Comparable<Y>> extends Node<Y> {}

              public static class Narrowed<A extends Number, B extends Integer>
                  extends Node<Pair<A, B>> {}

              public static class Twins<A extends Number, B extends Number> extends Node<Pair<A, B>> {}

              @SuppressWarnings("rawtypes")
              public static class RawBound<Y extends List> extends Node<Y> {}

              public static class Scoped<X> {
                  public class In<Q> {}

                  public static class Target<A, B extends In<A>> extends Node<Pair<A, B>> {}
              }

              public static class Listed<A, B extends List<A>> extends Node<B> {}

              public static class UpperListed<A extends Number, B extends List<? extends A>>
                  extends Node<B> {}

              public static class MeetListed<A extends Number & Serializable, B extends List<? extends A>>
                  extends Node<B> {}

              public static class LowerListed<A extends Number, B extends List<? super A>>
                  extends Node<B> {}

              public static class LowerArray<A, B extends List<? super A[]>> extends Node<B> {}

              public static class MemberArray<A, B extends Outer<? super A[]>.Base> extends Node<B> {}

              public static class PairedUpper<A, B extends List<? extends A>> extends Node<Pair<A, B>> {}

              public static class PairedLower<A, B extends List<? super A>> extends Node<Pair<A, B>> {}

              public static class PairedArray<A, B extends List<A[]>> extends Node<Pair<A, B>> {}

              public static class PairedMember<A, B extends Outer<A>.Base> extends Node<Pair<A, B>> {}

              public static class Ranked<X extends Comparable<X>> {
                  public class Pin extends Node<X> {}
              }

              public static class Lettered<Y extends CharSequence> {
                  public class Sub extends Outer<Y>.Base {
                      public Sub(Outer<Y> outer) {
                          outer.super();
                      }
                  }
              }
          }
          """);

  /** The interface a spec body declares, which names the file it is compiled from. */
  private static final Pattern SPEC_NAME = Pattern.compile("public interface (\\w+)");

  /**
   * Compiles spec interface bodies, each in a file of its own named for the interface it declares,
   * with the generated source held to the lints a consuming build turns on: a narrowing the
   * processor could not justify must not reach the user as a warning it has silenced on their
   * behalf.
   */
  private Compilation compile(String... specBodies) {
    Stream<JavaFileObject> fixtures =
        Stream.of(
            SHAPE, CIRCLE, NODE, LEAF, PAIR, WEDGE, TWIN, GRID, COUNTS, TRIPLE, LOOSE, STRICT,
            LOWER, UPPER, WILD, OUTER, DEPENDENT, LIMITS);
    Stream<JavaFileObject> specs =
        Arrays.stream(specBodies)
            .map(
                specBody ->
                    JavaFileObjects.forSourceString(
                        "com.myapp."
                            + SPEC_NAME
                                .matcher(specBody)
                                .results()
                                .findFirst()
                                .orElseThrow()
                                .group(1),
                        """
            package com.myapp;

            import com.external.Circle;
            import com.external.Counts;
            import com.external.Dependent;
            import com.external.Grid;
            import com.external.Leaf;
            import com.external.Limits;
            import com.external.Loose;
            import com.external.Lower;
            import com.external.Node;
            import com.external.Outer;
            import com.external.Pair;
            import com.external.Shape;
            import com.external.Strict;
            import com.external.Triple;
            import com.external.Twin;
            import com.external.Upper;
            import com.external.Wild;
            import com.external.Wedge;
            import java.util.List;
            import org.higherkindedj.optics.Prism;
            import org.higherkindedj.optics.annotations.ImportOptics;
            import org.higherkindedj.optics.annotations.InstanceOf;
            import org.higherkindedj.optics.annotations.OpticsSpec;

            @ImportOptics
            %s
            """
                            .formatted(specBody)));

    return javac()
        .withProcessors(new ImportOpticsProcessor())
        .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
        .compile(Stream.concat(fixtures, specs).toList());
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
    @DisplayName("narrows an array target through its component")
    void narrowsAnArrayThroughItsComponent() {
      var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.SubjectOpticsSpec",
              """
              package com.myapp;

              import java.util.List;
              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface SubjectOpticsSpec extends OpticsSpec<Object> {
                  @InstanceOf(List[].class)
                  Prism<Object, List<?>[]> rows();
              }""");

      // List[].class is exactly as raw as List.class, and an Object source pins none of List's own
      // parameter, so the component earns the wildcard the whole array is then written under.
      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(specInterface);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof List<?>[]");
      assertGeneratedCodeDoesNotContain(
          compilation, "com.myapp.SubjectOptics", "@SuppressWarnings");
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

    @Test
    @DisplayName("narrows an array target whose component never reaches the source's class")
    void narrowsAnArrayWhoseComponentNeverReachesTheSource() {
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
              public interface SubjectOpticsSpec extends OpticsSpec<Cloneable> {
                  @InstanceOf(String[].class)
                  Prism<Cloneable, String[]> names();
              }""");

      // Every array is Cloneable, but String is not, so its component has no view of the source
      // to match against, and nothing to pin or to rule out.
      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(specInterface);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof String[]");
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
    @DisplayName("rejects an array focus asking for an argument its component cannot check")
    void rejectsAParameterisedArrayFocus() {
      var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.SubjectOpticsSpec",
              """
              package com.myapp;

              import java.util.List;
              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface SubjectOpticsSpec extends OpticsSpec<Object> {
                  @InstanceOf(List[].class)
                  Prism<Object, List<String>[]> rows();
              }""");

      var compilation = javac().withProcessors(new ImportOpticsProcessor()).compile(specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'Object' pins nothing to E");
      assertThat(compilation).hadErrorContaining("declare the focus as 'List<?>[]'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a raw source type is refused before narrowing runs")
    void rawSourceTypeIsRefusedBeforeNarrowing() {
      var compilation =
          compile(
              """
              @SuppressWarnings("rawtypes")
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node> {
                  @InstanceOf(Leaf.class)
                  Prism<Node, Leaf<U>> leaf();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'SubjectOpticsSpec' declares OpticsSpec<Node>, which names the raw type"
                  + " 'Node'.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("pins nothing through a raw extends clause on the way to the source")
    void pinsNothingThroughARawExtendsClause() {
      var rawLeaf =
          JavaFileObjects.forSourceString(
              "com.external.RawLeaf",
              """
              package com.external;

              @SuppressWarnings("rawtypes")
              public class RawLeaf<Y> extends Node {}
              """);
      var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.SubjectOpticsSpec",
              """
              package com.myapp;

              import com.external.Node;
              import com.external.RawLeaf;
              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(RawLeaf.class)
                  Prism<Node<U>, RawLeaf<U>> leaf();
              }""");

      // RawLeaf reaches Node through a raw extends clause, so its view of the source names the
      // class with no arguments to read against Node<U>'s one: nothing pins Y.
      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(NODE, rawLeaf, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'Node<U>' pins nothing to Y");
      assertThat(compilation).hadErrorCount(1);
    }
  }

  @Nested
  @DisplayName("no value of the source can be the target")
  class NeverMatching {

    @Test
    @DisplayName("refuses a parameter the source would pin to a primitive, at the declaration")
    void refusesAParameterPinnedToAPrimitive() {
      // Grid reaches Node as Node<X[]>, and a Node<int[]> would pin X to int, which no type
      // argument can be. Handed on as one, it reaches a builder that takes reference types only.
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Node<int[]>> {
                  @InstanceOf(Grid.class)
                  Prism<Node<int[]>, Node<int[]>> grid();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'SubjectOpticsSpec.grid' tests 'Grid', which no 'Node<int[]>' can be.");
      assertThat(compilation)
          .hadErrorContaining(
              "Every 'Grid<X>' is a 'Node<X[]>', which is a 'Node<int[]>' only with 'X' as 'int',"
                  + " and a type argument is never a primitive: the test would never match.");
      assertThat(compilation)
          .hadErrorContaining(
              "Name a class in @InstanceOf that a 'Node<int[]>' can be, or declare the prism on a"
                  + " spec of its own, whose source type a 'Grid' can be.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("refuses before asking about the focus, which no focus could satisfy")
    void refusesBeforeAskingAboutTheFocus() {
      // A Grid<?> focus is not a Node<int[]> the prism could build back, and a Node<int[]> focus
      // is not what the test narrows to, so a focus check would only send the reader to the other.
      var compilation =
          compile(
              """
              public interface WildOpticsSpec extends OpticsSpec<Node<int[]>> {
                  @InstanceOf(Grid.class)
                  Prism<Node<int[]>, Grid<?>> grid();
              }""",
              """
              public interface BoxedOpticsSpec extends OpticsSpec<Node<int[]>> {
                  @InstanceOf(Grid.class)
                  Prism<Node<int[]>, Grid<Integer>> grid();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("'WildOpticsSpec.grid' tests 'Grid', which no 'Node<int[]>' can be.");
      assertThat(compilation)
          .hadErrorContaining(
              "'BoxedOpticsSpec.grid' tests 'Grid', which no 'Node<int[]>' can be.");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("compiles once the prism moves to a spec of its own, leaving its sibling alone")
    void compilesOnceThePrismMovesToASpecOfItsOwn() {
      // The spec's other prism needs its Node<int[]> source, so the fix line offers a new spec
      // rather than a new source for this one.
      var compilation =
          compile(
              """
              public interface CountsOpticsSpec extends OpticsSpec<Node<int[]>> {
                  @InstanceOf(Counts.class)
                  Prism<Node<int[]>, Counts> counts();
              }""",
              """
              public interface GridOpticsSpec extends OpticsSpec<Node<Integer[]>> {
                  @InstanceOf(Grid.class)
                  Prism<Node<Integer[]>, Grid<Integer>> grid();
              }""");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.GridOptics", "source instanceof Grid<Integer>");
    }

    @Test
    @DisplayName("refuses an enclosing class's parameter the source would pin to a primitive")
    void refusesAnEnclosingParameterPinnedToAPrimitive() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Node<int[]>> {
                  @InstanceOf(Outer.Rows.class)
                  Prism<Node<int[]>, Node<int[]>> rows();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Every 'Outer<X>.Rows' is a 'Node<X[]>', which is a 'Node<int[]>' only with 'X' as"
                  + " 'int'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("refuses a primitive the target writes where the source has a type parameter")
    void refusesAPrimitiveWhereTheSourceHasAParameter() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<U[]>> {
                  @InstanceOf(Counts.class)
                  Prism<Node<U[]>, Counts> counts();
              }""");

      // U stands for a reference type, so no U[] is the int[] Counts names.
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Every 'Counts' is a 'Node<int[]>', which is a 'Node<U[]>' only with 'U' as 'int'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("refuses an array where the source writes a class")
    void refusesAnArrayWhereTheSourceWritesAClass() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Node<String>> {
                  @InstanceOf(Grid.class)
                  Prism<Node<String>, Node<String>> grid();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Every 'Grid<X>' is a 'Node<X[]>', and no 'Node<X[]>' is a 'Node<String>': the test"
                  + " would never match.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("refuses where the source names a different class of the same arity")
    void refusesADifferentClassOfTheSameArity() {
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
      assertThat(compilation)
          .hadErrorContaining("tests 'Twin', which no 'Node<Wedge<U, U>>' can be.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("refuses another primitive, for a target that declares no parameters")
    void refusesAnotherPrimitiveForATargetWithoutParameters() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Node<long[]>> {
                  @InstanceOf(Counts.class)
                  Prism<Node<long[]>, Node<long[]>> counts();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Every 'Counts' is a 'Node<int[]>', and no 'Node<int[]>' is a 'Node<long[]>': the"
                  + " test would never match.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("writes the test where the source writes the same primitive")
    void writesTheTestWhereTheSourceWritesTheSamePrimitive() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Node<int[]>> {
                  @InstanceOf(Counts.class)
                  Prism<Node<int[]>, Counts> counts();
              }""");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Counts");
    }

    @Test
    @DisplayName("refuses one parameter the source asks to be two different types")
    void refusesOneParameterAskedToBeTwoTypes() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Node<Pair<String, int[]>>> {
                  @InstanceOf(Twin.class)
                  Prism<Node<Pair<String, int[]>>, Node<Pair<String, int[]>>> twin();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Every 'Twin<X>' is a 'Node<Pair<X, X>>', and no 'Node<Pair<X, X>>' is a"
                  + " 'Node<Pair<String, int[]>>': the test would never match.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("refuses two different types for one parameter, whatever a third one names")
    void refusesTwoDifferentTypesWhateverAThirdNames() {
      // X would have to be U, String and Integer at once. Whatever U is, String is not Integer.
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U>
                  extends OpticsSpec<Node<Pair<U, Pair<String, Integer>>>> {
                  @InstanceOf(Triple.class)
                  Prism<Node<Pair<U, Pair<String, Integer>>>, Triple<?>> triple();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "tests 'Triple', which no 'Node<Pair<U, Pair<String, Integer>>>' can be.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("refuses a parameter where the source writes a wildcard inside an argument")
    void refusesAParameterAgainstAWildcardInsideAnArgument() {
      // Pair<?, String> is one type, and no Pair<X, X> is it: a type argument is never a wildcard.
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Node<Pair<?, String>>> {
                  @InstanceOf(Twin.class)
                  Prism<Node<Pair<?, String>>, Node<Pair<?, String>>> twin();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Every 'Twin<X>' is a 'Node<Pair<X, X>>', and no 'Node<Pair<X, X>>' is a"
                  + " 'Node<Pair<?, String>>': the test would never match.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("refuses an argument that names no variable and differs, wildcards and all")
    void refusesAnArgumentThatDiffersWildcardsAndAll() {
      // Inside an argument a wildcard is part of the one type the argument is, so a Pair<?, String>
      // is neither a Pair<?, Integer> nor a Pair<? extends Number, String>.
      var compilation =
          compile(
              """
              public interface IntegersOpticsSpec extends OpticsSpec<Node<Pair<?, Integer>>> {
                  @InstanceOf(Loose.class)
                  Prism<Node<Pair<?, Integer>>, Node<Pair<?, Integer>>> loose();
              }""",
              """
              public interface BoundedOpticsSpec
                  extends OpticsSpec<Node<Pair<? extends Number, String>>> {
                  @InstanceOf(Loose.class)
                  Prism<Node<Pair<? extends Number, String>>, Node<Pair<? extends Number, String>>>
                      loose();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Every 'Loose' is a 'Node<Pair<?, String>>', and no 'Node<Pair<?, String>>' is a"
                  + " 'Node<Pair<?, Integer>>': the test would never match.");
      assertThat(compilation)
          .hadErrorContaining(
              "'BoundedOpticsSpec.loose' tests 'Loose', which no"
                  + " 'Node<Pair<? extends Number, String>>' can be.");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName(
        "refuses one parameter asked to be two types that a wildcard inside one sets apart")
    void refusesOneParameterAskedToBeTwoTypesAWildcardSetsApart() {
      // List<?> is one type, as List<String> is, and Twin's X would have to be both.
      var compilation =
          compile(
              """
              public interface ListsOpticsSpec extends OpticsSpec<Node<Pair<List<?>, List<String>>>> {
                  @InstanceOf(Twin.class)
                  Prism<Node<Pair<List<?>, List<String>>>, Node<Pair<List<?>, List<String>>>> twin();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'ListsOpticsSpec.twin' tests 'Twin', which no 'Node<Pair<List<?>, List<String>>>'"
                  + " can be.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("writes the test where an argument is the same type, wildcards and all")
    void writesTheTestWhereAnArgumentIsTheSameType() {
      var compilation =
          compile(
              """
              public interface LooseOpticsSpec extends OpticsSpec<Node<Pair<?, String>>> {
                  @InstanceOf(Loose.class)
                  Prism<Node<Pair<?, String>>, Loose> loose();
              }""");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.myapp.LooseOptics", "source instanceof Loose");
    }

    @Test
    @DisplayName("pins a parameter to a type with a wildcard inside, where every place agrees")
    void pinsAParameterToATypeWithAWildcardInside() {
      var compilation =
          compile(
              """
              public interface ListsOpticsSpec extends OpticsSpec<Node<Pair<List<?>, List<?>>>> {
                  @InstanceOf(Twin.class)
                  Prism<Node<Pair<List<?>, List<?>>>, Twin<List<?>>> twin();
              }""");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.ListsOptics", "source instanceof Twin<List<?>>");
    }

    @Test
    @DisplayName("refuses a wildcard against a type, where a variable leaves the rest open")
    void refusesAWildcardAgainstATypeBesideAVariable() {
      var compilation =
          compile(
              """
              public interface LooseOpticsSpec<U> extends OpticsSpec<Node<Pair<Integer, U>>> {
                  @InstanceOf(Loose.class)
                  Prism<Node<Pair<Integer, U>>, Node<Pair<Integer, U>>> loose();
              }""",
              """
              public interface StrictOpticsSpec<U> extends OpticsSpec<Node<Pair<U[], ?>>> {
                  @InstanceOf(Strict.class)
                  Prism<Node<Pair<U[], ?>>, Node<Pair<U[], ?>>> strict();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'LooseOpticsSpec.loose' tests 'Loose', which no");
      assertThat(compilation)
          .hadErrorContaining("'StrictOpticsSpec.strict' tests 'Strict', which no");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("leaves a wildcard against the source's type variable to the checks that follow")
    void leavesAWildcardAgainstTheSourcesVariable() {
      // javac reads U as anything at all, the wildcard's own type included, and so does the match.
      var compilation =
          compile(
              """
              public interface LooseOpticsSpec<U> extends OpticsSpec<Node<Pair<U, String>>> {
                  @InstanceOf(Loose.class)
                  Prism<Node<Pair<U, String>>, Node<Pair<U, String>>> loose();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("narrows to 'Loose', which is not a 'Node<Pair<U, String>>'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("leaves a class against the source's type variable inside an argument alone")
    void leavesAClassAgainstTheSourcesVariableInsideAnArgument() {
      // The match rules nothing out against the source's V, so the focus check that follows is
      // what refuses it.
      var compilation =
          compile(
              """
              public interface StrictOpticsSpec<U, V> extends OpticsSpec<Node<Pair<U[], V>>> {
                  @InstanceOf(Strict.class)
                  Prism<Node<Pair<U[], V>>, Strict<U>> strict();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("focuses 'Strict<U>', which is not a 'Node<Pair<U[], V>>'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("writes the test where javac reads a wildcard as the one type against it")
    void writesTheTestWhereAWildcardIsTheOneTypeAgainstIt() {
      // ? super Object admits Object alone, so javac reads it as Object, on either side.
      var compilation =
          compile(
              """
              public interface LowerOpticsSpec extends OpticsSpec<Node<Pair<String, Object>>> {
                  @InstanceOf(Lower.class)
                  Prism<Node<Pair<String, Object>>, Lower<String>> lower();
              }""",
              """
              public interface UpperOpticsSpec
                  extends OpticsSpec<Node<Pair<String, ? super Object>>> {
                  @InstanceOf(Upper.class)
                  Prism<Node<Pair<String, ? super Object>>, Upper<String>> upper();
              }""");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.LowerOptics", "source instanceof Lower<String>");
      assertGeneratedCodeContains(
          compilation, "com.myapp.UpperOptics", "source instanceof Upper<String>");
    }

    @Test
    @DisplayName("refuses a wildcard that admits other types than the one against it")
    void refusesAWildcardThatAdmitsOtherTypes() {
      var compilation =
          compile(
              """
              public interface LowerOpticsSpec extends OpticsSpec<Node<Pair<String, String>>> {
                  @InstanceOf(Lower.class)
                  Prism<Node<Pair<String, String>>, Node<Pair<String, String>>> lower();
              }""",
              """
              public interface TwinOpticsSpec extends OpticsSpec<Node<Pair<? super Integer, String>>> {
                  @InstanceOf(Twin.class)
                  Prism<Node<Pair<? super Integer, String>>, Node<Pair<? super Integer, String>>>
                      twin();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'LowerOpticsSpec.lower' tests 'Lower', which no");
      assertThat(compilation).hadErrorContaining("'TwinOpticsSpec.twin' tests 'Twin', which no");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("compares two wildcards inside an argument beside a parameter, bounds and all")
    void comparesTwoWildcardsBesideAParameter() {
      // Lower's X keeps the argument open, so the match goes position by position, and meets one
      // wildcard against another there.
      var same =
          compile(
              """
              public interface SameOpticsSpec
                  extends OpticsSpec<Node<Pair<String, ? super Object>>> {
                  @InstanceOf(Lower.class)
                  Prism<Node<Pair<String, ? super Object>>, Lower<String>> lower();
              }""");
      var different =
          compile(
              """
              public interface AnyOpticsSpec extends OpticsSpec<Node<Pair<String, ?>>> {
                  @InstanceOf(Lower.class)
                  Prism<Node<Pair<String, ?>>, Node<Pair<String, ?>>> lower();
              }""");

      assertThat(same).succeeded();
      assertGeneratedCodeContains(same, "com.myapp.SameOptics", "source instanceof Lower<String>");
      assertThat(different).failed();
      assertThat(different)
          .hadErrorContaining(
              "Every 'Lower<X>' is a 'Node<Pair<X, ? super Object>>', and no"
                  + " 'Node<Pair<X, ? super Object>>' is a 'Node<Pair<String, ?>>': the test would"
                  + " never match.");
      assertThat(different).hadErrorCount(1);
    }

    @Test
    @DisplayName("weighs a wildcard the target writes around its class against the source's")
    void weighsAWildcardTheTargetWritesAroundItsClass() {
      // Wild is a Base of some Outer: an Outer<?>.Base can be one, an Outer<String>.Base cannot.
      var any =
          compile(
              """
              public interface AnyOpticsSpec extends OpticsSpec<Outer<?>.Base> {
                  @InstanceOf(Wild.class)
                  Prism<Outer<?>.Base, Wild> wild();
              }""");
      var strings =
          compile(
              """
              public interface StringsOpticsSpec extends OpticsSpec<Outer<String>.Base> {
                  @InstanceOf(Wild.class)
                  Prism<Outer<String>.Base, Outer<String>.Base> wild();
              }""");

      assertThat(any).succeeded();
      assertGeneratedCodeContains(any, "com.myapp.AnyOptics", "source instanceof Wild");
      assertThat(strings).failed();
      assertThat(strings)
          .hadErrorContaining(
              "'StringsOpticsSpec.wild' tests 'Wild', which no 'Outer<String>.Base'");
      assertThat(strings).hadErrorCount(1);
    }

    @Test
    @DisplayName(
        "leaves a wildcard that names the source's type variable to the checks that follow")
    void leavesAWildcardThatNamesTheSourcesVariable() {
      // javac reads ? extends U as nothing it could rule out, and accepts the test.
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U>
                  extends OpticsSpec<Node<Pair<String, ? extends U>>> {
                  @InstanceOf(Lower.class)
                  Prism<Node<Pair<String, ? extends U>>, Node<Pair<String, ? extends U>>> lower();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("narrows to 'Lower<String>', which is not a");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("pins nothing, rather than refusing, against a wildcard javac reads as one type")
    void pinsNothingAgainstAWildcardReadAsOneType() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec
                  extends OpticsSpec<Node<Pair<? super Object, Object>>> {
                  @InstanceOf(Twin.class)
                  Prism<Node<Pair<? super Object, Object>>, Node<Pair<? super Object, Object>>>
                      twin();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("'Node<Pair<? super Object, Object>>' pins nothing to X");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("names the primitive only where nothing else rules the source out")
    void namesThePrimitiveOnlyWhereNothingElseRulesTheSourceOut() {
      // X would have to be int, but String is not Integer whatever X is.
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Node<Pair<int[], Integer>>> {
                  @InstanceOf(Strict.class)
                  Prism<Node<Pair<int[], Integer>>, Node<Pair<int[], Integer>>> strict();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Every 'Strict<X>' is a 'Node<Pair<X[], String>>', and no 'Node<Pair<X[], String>>'"
                  + " is a 'Node<Pair<int[], Integer>>': the test would never match.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("rules nothing out where the source's enclosing class's argument is a wildcard")
    void rulesNothingOutAgainstAnEnclosingWildcard() {
      // Outer<?>.Base admits a Base of any Outer, so its wildcard stands for an argument rather
      // than inside one.
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Outer<?>.Base> {
                  @InstanceOf(Outer.Sub.class)
                  Prism<Outer<?>.Base, Outer<?>.Sub> sub();
              }""");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Outer<?>.Sub t");
    }

    @Test
    @DisplayName("rules nothing out where the source's own argument is a wildcard")
    void rulesNothingOutAgainstTheSourcesOwnWildcard() {
      // A Node<?> holds a Counts as surely as any other Node.
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Node<?>> {
                  @InstanceOf(Counts.class)
                  Prism<Node<?>, Counts> counts();
              }""",
              """
              public interface LeafOpticsSpec extends OpticsSpec<Node<?>> {
                  @InstanceOf(Leaf.class)
                  Prism<Node<?>, Leaf<?>> leaf();
              }""");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Counts");
      assertGeneratedCodeContains(compilation, "com.myapp.LeafOptics", "source instanceof Leaf<?>");
    }
  }

  @Nested
  @DisplayName("the target is an inner class of another class")
  class Members {

    @Test
    @DisplayName("names an inner class of a generic class under its enclosing arguments")
    void namesAnInnerClassUnderItsEnclosingArguments() {
      // The class constant names a raw Outer.Plain. Node<String> pins nothing of Outer's, so a
      // Plain of some Outer is what the test earns, and the pattern names it so.
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Node<String>> {
                  @InstanceOf(Outer.Plain.class)
                  Prism<Node<String>, Outer<?>.Plain> plain();
              }""");

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Outer<?>.Plain t");
    }

    @Test
    @DisplayName("pins an inner class's own argument and its enclosing class's alike")
    void pinsOwnAndEnclosingArgumentsAlike() {
      // Inner's own Y is pinned through Node<Y>, and Pin's clause pins Outer's X the same way.
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Outer.Inner.class)
                  Prism<Node<U>, Outer<?>.Inner<U>> inner();

                  @InstanceOf(Outer.Pin.class)
                  Prism<Node<U>, Outer<U>.Pin> pin();
              }""");

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Outer<?>.Inner<U> t");
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Outer<U>.Pin t");
    }

    @Test
    @DisplayName("narrows an array of inner classes through its component")
    void narrowsAnArrayOfInnerClasses() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Object> {
                  @InstanceOf(Outer.Inner[].class)
                  Prism<Object, Outer<?>.Inner<?>[]> inners();
              }""");

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Outer<?>.Inner<?>[] t");
    }

    @Test
    @DisplayName("rejects a focus asking for an enclosing argument the source does not pin")
    void rejectsAnEnclosingArgumentTheSourceDoesNotPin() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Node<String>> {
                  @InstanceOf(Outer.Plain.class)
                  Prism<Node<String>, Outer<String>.Plain> plain();
              }""");

      assertThat(compilation).failed();
      // X is named with its class, since the source's own parameter may share the name.
      assertThat(compilation).hadErrorContaining("'Node<String>' pins nothing to Outer's X");
      assertThat(compilation).hadErrorContaining("declare the focus as 'Outer<?>.Plain'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("pins an enclosing argument through a source that is an inner class too")
    void pinsAnEnclosingArgumentThroughAnInnerSource() {
      // Outer<X>.Sub reaches the source as Outer<X>.Base, and matching that against
      // Outer<String>.Base pins X through the enclosing type alone.
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Outer<String>.Base> {
                  @InstanceOf(Outer.Sub.class)
                  Prism<Outer<String>.Base, Outer<String>.Sub> sub();
              }""");

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Outer<String>.Sub t");
    }

    @Test
    @DisplayName("names a pinned parameter whose bound only names a free one")
    void namesAPinnedParameterWhoseBoundNamesAFreeOne() {
      // Z extends List<X> becomes List<?> once X is a wildcard, and U is within that.
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U extends List<String>>
                  extends OpticsSpec<Node<U>> {
                  @InstanceOf(Dependent.InZ.class)
                  Prism<Node<U>, Node<U>> inZ();
              }""");

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Dependent<?, U, ?>.InZ t");
    }

    @Test
    @DisplayName("names a pinned parameter bounded by another it pins")
    void namesAPinnedParameterBoundedByAnotherItPins() {
      // W extends X, and the source pins both, so the bound is written as the argument X took.
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Node<Pair<Number, Integer>>> {
                  @InstanceOf(Dependent.Both.class)
                  Prism<Node<Pair<Number, Integer>>, Node<Pair<Number, Integer>>> both();
              }""");

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.SubjectOptics",
          "source instanceof Dependent<Number, ?, Integer>.Both t");
    }

    @Test
    @DisplayName("rejects a pinned parameter bounded by one the source leaves free")
    void rejectsAPinnedParameterBoundedByAFreeOne() {
      // Each test would write a wildcard for X, and a bound that is that wildcard leaves nothing an
      // argument could be within: javac refuses Outer<?>.Bounded<U>, whatever the focus declares.
      var compilation =
          compile(
              """
              public interface BoundedOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Outer.Bounded.class)
                  Prism<Node<U>, Node<U>> bounded();
              }""",
              """
              public interface InWOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Dependent.InW.class)
                  Prism<Node<U>, Node<U>> inW();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'BoundedOpticsSpec.bounded' tests 'Outer.Bounded', where Y extends X and 'Node<U>'"
                  + " pins nothing to Outer's X.");
      assertThat(compilation).hadErrorContaining("with @MatchWhen, or write the prism by hand.");
      // W is Dependent's, and named so, since the target may declare a W of its own.
      assertThat(compilation)
          .hadErrorContaining(
              "'InWOpticsSpec.inW' tests 'Dependent.InW', where Dependent's W extends X and"
                  + " 'Node<U>' pins nothing to Dependent's X.");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("names a member of a non-generic class without enclosing arguments")
    void namesAMemberOfANonGenericClassWithoutEnclosingArguments() {
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

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Outer.Inner<U>");
    }
  }

  @Nested
  @DisplayName("the source pins an argument outside its parameter's bound")
  class Bounds {

    @Test
    @DisplayName("rejects the test javac would refuse, at the declaration")
    void rejectsAnArgumentOutsideItsBound() {
      // Numeric's Y extends Number, and nothing says the U it is pinned to is one: javac refuses
      // Limits.Numeric<U>, so the generated file would not compile.
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.Numeric.class)
                  Prism<Node<U>, Node<U>> numeric();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'SubjectOpticsSpec.numeric' tests 'Limits.Numeric<U>', where Y extends Number and U"
                  + " is not a 'Number'.");
      assertThat(compilation)
          .hadErrorContaining(
              "javac refuses a type argument outside its parameter's bound, so the generated test"
                  + " would not compile.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare U as 'U extends Number' in 'SubjectOpticsSpec', or narrow through a"
                  + " predicate and getter of 'Node<U>' with @MatchWhen.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("writes the test once the spec bounds the argument, as the fix line says")
    void writesTheTestOnceTheSpecBoundsTheArgument() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U extends Number> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.Numeric.class)
                  Prism<Node<U>, Limits.Numeric<U>> numeric();
              }""",
              """
              public interface MeasuredOpticsSpec<U extends Number & Comparable<U>>
                  extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.Measured.class)
                  Prism<Node<U>, Limits.Measured<U>> measured();
              }""",
              """
              public interface SameOpticsSpec<T, U extends T> extends OpticsSpec<Node<Pair<T, U>>> {
                  @InstanceOf(Limits.Same.class)
                  Prism<Node<Pair<T, U>>, Limits.Same<T, U>> same();
              }""",
              """
              public interface MixedOpticsSpec<U extends Number & CharSequence>
                  extends OpticsSpec<Node<Pair<U, U>>> {
                  @InstanceOf(Limits.Mixed.class)
                  Prism<Node<Pair<U, U>>, Limits.Mixed<U, U>> mixed();
              }""",
              """
              public interface NarrowedOpticsSpec<U extends Integer>
                  extends OpticsSpec<Node<Pair<U, U>>> {
                  @InstanceOf(Limits.Narrowed.class)
                  Prism<Node<Pair<U, U>>, Limits.Narrowed<U, U>> narrowed();
              }""",
              """
              public interface MaximalOpticsSpec<U extends Comparable<U>> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.Maximal.class)
                  Prism<Node<U>, Limits.Maximal<U>> maximal();
              }""");

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "source instanceof Limits.Numeric<U> t");
      assertGeneratedCodeContains(
          compilation, "com.myapp.MeasuredOptics", "source instanceof Limits.Measured<U> t");
      assertGeneratedCodeContains(
          compilation, "com.myapp.SameOptics", "source instanceof Limits.Same<T, U> t");
      assertGeneratedCodeContains(
          compilation, "com.myapp.MixedOptics", "source instanceof Limits.Mixed<U, U> t");
      assertGeneratedCodeContains(
          compilation, "com.myapp.NarrowedOptics", "source instanceof Limits.Narrowed<U, U> t");
      assertGeneratedCodeContains(
          compilation, "com.myapp.MaximalOptics", "source instanceof Limits.Maximal<U> t");
    }

    @Test
    @DisplayName("offers another class where no bound the spec writes would bring it within")
    void rejectsAnArgumentNoBoundOnTheSpecCouldHelp() {
      // A is pinned to Number, so B's bound reads List<? extends Number>, and List<String> is no
      // such list whatever the spec declares.
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec
                  extends OpticsSpec<Node<Pair<Number, List<String>>>> {
                  @InstanceOf(Limits.PairedUpper.class)
                  Prism<Node<Pair<Number, List<String>>>, Node<Pair<Number, List<String>>>> paired();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'SubjectOpticsSpec.paired' tests 'Limits.PairedUpper<Number, List<String>>', where B"
                  + " extends List<? extends A> and List<String> is not a 'List<? extends"
                  + " Number>'.");
      assertThat(compilation)
          .hadErrorContaining(
              "Name a class in @InstanceOf that a 'Node<Pair<Number, List<String>>>' can be, or"
                  + " narrow through a predicate and getter of 'Node<Pair<Number,"
                  + " List<String>>>' with @MatchWhen.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("reads a bound that names a parameter the test leaves free as javac does")
    void readsABoundNamingAFreeParameterAsJavacDoes() {
      // A is free, so the test writes ? for it: B extends List<A> reads List<?>. As a wildcard's
      // bound javac reads a free A its own way: List<? super A> reads List<?>, and List<? extends
      // A> reads List<? extends Number>, from the bound A declares, which a List<String> is not.
      var compilation =
          compile(
              """
              public interface ListedOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.Listed.class)
                  Prism<Node<U>, Node<U>> listed();
              }""",
              """
              public interface LowerOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.LowerListed.class)
                  Prism<Node<U>, Node<U>> lower();
              }""",
              """
              public interface UpperOpticsSpec<U extends List<String>> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.UpperListed.class)
                  Prism<Node<U>, Node<U>> upper();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'ListedOpticsSpec.listed' tests 'Limits.Listed<?, U>', where B extends List<A> and U"
                  + " is not a 'List<?>'.");
      assertThat(compilation)
          .hadErrorContaining(
              "'LowerOpticsSpec.lower' tests 'Limits.LowerListed<?, U>', where B extends List<?"
                  + " super A> and U is not a 'List<?>'.");
      assertThat(compilation)
          .hadErrorContaining(
              "'UpperOpticsSpec.upper' tests 'Limits.UpperListed<?, U>', where B extends List<?"
                  + " extends A> and U is not a 'List<? extends Number>'.");
      assertThat(compilation).hadErrorCount(3);
    }

    @Test
    @DisplayName("checks the arguments it pins on an enclosing class too")
    void checksTheArgumentsItPinsOnAnEnclosingClass() {
      var compilation =
          compile(
              """
              public interface RankedOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.Ranked.Pin.class)
                  Prism<Node<U>, Node<U>> pin();
              }""",
              """
              public interface LetteredOpticsSpec extends OpticsSpec<Outer<Object>.Base> {
                  @InstanceOf(Limits.Lettered.Sub.class)
                  Prism<Outer<Object>.Base, Outer<Object>.Base> sub();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'RankedOpticsSpec.pin' tests 'Limits.Ranked<U>.Pin', where Ranked's X extends"
                  + " Comparable<X> and U is not a 'Comparable<U>'.");
      assertThat(compilation)
          .hadErrorContaining(
              "'LetteredOpticsSpec.sub' tests 'Limits.Lettered<Object>.Sub', where Lettered's Y"
                  + " extends CharSequence and Object is not a 'CharSequence'.");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("names every bound the argument has to meet, not only the one it misses")
    void namesEveryBoundTheArgumentHasToMeet() {
      // U is a Number already, so it misses only Comparable<U>, but the message names both: the
      // reader declaring U from it keeps the Number. Two bounds get no declaration of their own.
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U extends Number> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.Measured.class)
                  Prism<Node<U>, Node<U>> measured();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "where Y extends Number & Comparable<Y> and U is not a 'Number & Comparable<U>'.");
      assertThat(compilation)
          .hadErrorContaining("Name a class in @InstanceOf that a 'Node<U>' can be");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName(
        "declares a spec parameter with the one bound every argument outside its own needs")
    void declaresASpecParameterWithTheOneBoundNeeded() {
      // Same's B extends A, and A is pinned to T. Narrowed pins both parameters to U, and Integer
      // implies Number, so U needs Integer alone, which keeps a Number it declares already. Twins
      // asks for Number twice. Maximal's Object adds nothing to its Comparable<Y>.
      var compilation =
          compile(
              """
              public interface SameOpticsSpec<T, U> extends OpticsSpec<Node<Pair<T, U>>> {
                  @InstanceOf(Limits.Same.class)
                  Prism<Node<Pair<T, U>>, Node<Pair<T, U>>> same();
              }""",
              """
              public interface NarrowedOpticsSpec<U> extends OpticsSpec<Node<Pair<U, U>>> {
                  @InstanceOf(Limits.Narrowed.class)
                  Prism<Node<Pair<U, U>>, Node<Pair<U, U>>> narrowed();
              }""",
              """
              public interface KeptOpticsSpec<U extends Number> extends OpticsSpec<Node<Pair<U, U>>> {
                  @InstanceOf(Limits.Narrowed.class)
                  Prism<Node<Pair<U, U>>, Node<Pair<U, U>>> narrowed();
              }""",
              """
              public interface TwinsOpticsSpec<U> extends OpticsSpec<Node<Pair<U, U>>> {
                  @InstanceOf(Limits.Twins.class)
                  Prism<Node<Pair<U, U>>, Node<Pair<U, U>>> twins();
              }""",
              """
              public interface MaximalOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.Maximal.class)
                  Prism<Node<U>, Node<U>> maximal();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("Declare U as 'U extends T' in 'SameOpticsSpec', or narrow");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare U as 'U extends Integer' in 'NarrowedOpticsSpec', or narrow");
      assertThat(compilation)
          .hadErrorContaining("Declare U as 'U extends Integer' in 'KeptOpticsSpec', or narrow");
      assertThat(compilation)
          .hadErrorContaining("Declare U as 'U extends Number' in 'TwinsOpticsSpec', or narrow");
      assertThat(compilation)
          .hadErrorContaining(
              "where Y extends Object & Comparable<Y> and U is not a 'Comparable<U>'.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare U as 'U extends Comparable<U>' in 'MaximalOpticsSpec', or narrow");
      assertThat(compilation).hadErrorCount(5);
    }

    @Test
    @DisplayName("offers another class where no one bound on a spec parameter would do")
    void offersAnotherClassWhereNoOneBoundWouldDo() {
      // In turn: an argument that is not a spec parameter; a bound read with the target's own A in
      // it; a bound arm no type writes; an array, which bounds no type parameter; two bounds, which
      // may clash in ways only javac settles; a bound that does not imply the one U declares
      // already, Comparable<U> beside Comparable<String> or Number beside CharSequence; a bound
      // naming the X of a class around a static member; a raw bound, which -Werror refuses; a
      // bound closing a cycle; and a second argument, String, that no bound on U helps.
      var compilation =
          compile(
              """
              public interface NumberListsOpticsSpec<U> extends OpticsSpec<Node<List<U>>> {
                  @InstanceOf(Limits.NumberLists.class)
                  Prism<Node<List<U>>, Node<List<U>>> numberLists();
              }""",
              """
              public interface ComparingOpticsSpec<U extends List<Integer>>
                  extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.Comparing.class)
                  Prism<Node<U>, Node<U>> comparing();
              }""",
              """
              public interface PartlyOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.Partly.class)
                  Prism<Node<U>, Node<U>> partly();
              }""",
              """
              public interface ArrayOpticsSpec<U> extends OpticsSpec<Node<Pair<String[], U>>> {
                  @InstanceOf(Limits.Same.class)
                  Prism<Node<Pair<String[], U>>, Node<Pair<String[], U>>> same();
              }""",
              """
              public interface ClashingOpticsSpec<U> extends OpticsSpec<Node<Pair<U, U>>> {
                  @InstanceOf(Limits.Clashing.class)
                  Prism<Node<Pair<U, U>>, Node<Pair<U, U>>> clashing();
              }""",
              """
              public interface TwiceOpticsSpec<U extends Comparable<String>>
                  extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.Ordered.class)
                  Prism<Node<U>, Node<U>> ordered();
              }""",
              """
              public interface MixedOpticsSpec<U extends CharSequence>
                  extends OpticsSpec<Node<Pair<U, U>>> {
                  @InstanceOf(Limits.Mixed.class)
                  Prism<Node<Pair<U, U>>, Node<Pair<U, U>>> mixed();
              }""",
              """
              public interface ScopedOpticsSpec<U> extends OpticsSpec<Node<Pair<String, U>>> {
                  @InstanceOf(Limits.Scoped.Target.class)
                  Prism<Node<Pair<String, U>>, Node<Pair<String, U>>> scoped();
              }""",
              """
              public interface RawOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.RawBound.class)
                  Prism<Node<U>, Node<U>> raw();
              }""",
              """
              public interface CycleOpticsSpec<T extends U, U> extends OpticsSpec<Node<Pair<T, U>>> {
                  @InstanceOf(Limits.Same.class)
                  Prism<Node<Pair<T, U>>, Node<Pair<T, U>>> same();
              }""",
              """
              public interface StringOpticsSpec<U> extends OpticsSpec<Node<Pair<U, String>>> {
                  @InstanceOf(Limits.Narrowed.class)
                  Prism<Node<Pair<U, String>>, Node<Pair<U, String>>> narrowed();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'NumberListsOpticsSpec.numberLists' tests 'Limits.NumberLists<List<U>>', where Y"
                  + " extends List<? extends Number> and List<U> is not a 'List<? extends"
                  + " Number>'.");
      assertThat(compilation)
          .hadErrorContaining(
              "Name a class in @InstanceOf that a 'Node<List<U>>' can be, or narrow through a"
                  + " predicate and getter of 'Node<List<U>>' with @MatchWhen.");
      assertThat(compilation)
          .hadErrorContaining(
              "'ComparingOpticsSpec.comparing' tests 'Limits.Comparing<?, U>', where B extends"
                  + " List<? extends A> and U is not a 'List<? extends Comparable<A>>'.");
      assertThat(compilation)
          .hadErrorContaining(
              "'PartlyOpticsSpec.partly' tests 'Limits.Partly<?, U>', where Y extends Number &"
                  + " Comparable<? extends A> and U is not a 'Number'.");
      assertThat(compilation)
          .hadErrorContaining(
              "'ArrayOpticsSpec.same' tests 'Limits.Same<String[], U>', where B extends A and U"
                  + " is not a 'String[]'.");
      assertThat(compilation)
          .hadErrorContaining(
              "'ClashingOpticsSpec.clashing' tests 'Limits.Clashing<U, U>', where A extends"
                  + " Integer and U is not a 'Integer'.");
      assertThat(compilation)
          .hadErrorContaining(
              "'TwiceOpticsSpec.ordered' tests 'Limits.Ordered<U>', where Y extends Comparable<Y>"
                  + " and U is not a 'Comparable<U>'.");
      assertThat(compilation)
          .hadErrorContaining(
              "'MixedOpticsSpec.mixed' tests 'Limits.Mixed<U, U>', where A extends Number and U"
                  + " is not a 'Number'.");
      assertThat(compilation)
          .hadErrorContaining(
              "'ScopedOpticsSpec.scoped' tests 'Limits.Scoped.Target<String, U>', where B extends"
                  + " Limits.Scoped<X>.In<A> and U is not a 'Limits.Scoped<X>.In<String>'.");
      assertThat(compilation)
          .hadErrorContaining(
              "Name a class in @InstanceOf that a 'Node<Pair<String, U>>' can be, or narrow"
                  + " through a predicate and getter of 'Node<Pair<String, U>>' with @MatchWhen.");
      assertThat(compilation)
          .hadErrorContaining(
              "'RawOpticsSpec.raw' tests 'Limits.RawBound<U>', where Y extends List and U is not a"
                  + " 'List'.");
      assertThat(compilation)
          .hadErrorContaining(
              "'CycleOpticsSpec.same' tests 'Limits.Same<T, U>', where B extends A and U is not a"
                  + " 'T'.");
      assertThat(compilation)
          .hadErrorContaining(
              "'StringOpticsSpec.narrowed' tests 'Limits.Narrowed<U, String>', where A extends"
                  + " Number and U is not a 'Number'.");
      assertThat(compilation).hadErrorCount(11);
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(Locale.ROOT).contains("Declare "));
    }

    @Test
    @DisplayName(
        "leaves javac to refuse a bound naming a member of a generic class through a raw one")
    void leavesAnImproperlyFormedBoundToJavac() {
      // Raw.In<A> gives In type arguments under a raw Raw, which javac refuses as improperly
      // formed and no type builder writes: the check stands aside rather than failing on it.
      var raw =
          JavaFileObjects.forSourceString(
              "com.external.Raw",
              """
              package com.external;

              public class Raw<X> {
                  public class In<Q> {}
              }
              """);
      var malformed =
          JavaFileObjects.forSourceString(
              "com.external.Malformed",
              """
              package com.external;

              @SuppressWarnings("rawtypes")
              public class Malformed<A, B extends Raw.In<A>> extends Node<B> {}
              """);
      var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.SubjectOpticsSpec",
              """
              package com.myapp;

              import com.external.Malformed;
              import com.external.Node;
              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Malformed.class)
                  Prism<Node<U>, Node<U>> malformed();
              }""");

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(NODE, raw, malformed, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("improperly formed type");
    }

    @Test
    @DisplayName("writes every test javac accepts, leaving it a bound the check cannot read")
    void writesEveryTestJavacAccepts() {
      // Each argument is within its bound as javac reads it. MeetListed's A is bounded by an
      // intersection, and LowerArray and MemberArray name an array of a free A: no type writes
      // javac's reading of those, so the check leaves them to javac, which accepts these.
      var compilation =
          compile(
              """
              public interface ListedOpticsSpec<U extends List<Integer>>
                  extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.Listed.class)
                  Prism<Node<U>, Limits.Listed<?, U>> listed();

                  @InstanceOf(Limits.UpperListed.class)
                  Prism<Node<U>, Limits.UpperListed<?, U>> upper();

                  @InstanceOf(Limits.MeetListed.class)
                  Prism<Node<U>, Limits.MeetListed<?, U>> meet();

                  @InstanceOf(Limits.LowerListed.class)
                  Prism<Node<U>, Limits.LowerListed<?, U>> lower();
              }""",
              """
              public interface LowerArrayOpticsSpec<U extends List<Object>>
                  extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.LowerArray.class)
                  Prism<Node<U>, Limits.LowerArray<?, U>> lowerArray();
              }""",
              """
              public interface MemberArrayOpticsSpec<U extends Outer<Object>.Base>
                  extends OpticsSpec<Node<U>> {
                  @InstanceOf(Limits.MemberArray.class)
                  Prism<Node<U>, Limits.MemberArray<?, U>> memberArray();
              }""",
              """
              public interface PairedUpperOpticsSpec
                  extends OpticsSpec<Node<Pair<Integer, List<Integer>>>> {
                  @InstanceOf(Limits.PairedUpper.class)
                  Prism<Node<Pair<Integer, List<Integer>>>, Limits.PairedUpper<Integer, List<Integer>>>
                      upper();
              }""",
              """
              public interface PairedLowerOpticsSpec
                  extends OpticsSpec<Node<Pair<Integer, List<Number>>>> {
                  @InstanceOf(Limits.PairedLower.class)
                  Prism<Node<Pair<Integer, List<Number>>>, Limits.PairedLower<Integer, List<Number>>>
                      lower();
              }""",
              """
              public interface PairedArrayOpticsSpec
                  extends OpticsSpec<Node<Pair<String, List<String[]>>>> {
                  @InstanceOf(Limits.PairedArray.class)
                  Prism<Node<Pair<String, List<String[]>>>, Limits.PairedArray<String, List<String[]>>>
                      array();
              }""",
              """
              public interface PairedMemberOpticsSpec
                  extends OpticsSpec<Node<Pair<String, Outer<String>.Base>>> {
                  @InstanceOf(Limits.PairedMember.class)
                  Prism<
                          Node<Pair<String, Outer<String>.Base>>,
                          Limits.PairedMember<String, Outer<String>.Base>>
                      member();
              }""",
              """
              public interface OrderedOpticsSpec extends OpticsSpec<Node<String>> {
                  @InstanceOf(Limits.Ordered.class)
                  Prism<Node<String>, Limits.Ordered<String>> ordered();
              }""");

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.myapp.ListedOptics", "source instanceof Limits.UpperListed<?, U> t");
      assertGeneratedCodeContains(
          compilation, "com.myapp.ListedOptics", "source instanceof Limits.MeetListed<?, U> t");
      assertGeneratedCodeContains(
          compilation, "com.myapp.LowerArrayOptics", "source instanceof Limits.LowerArray<?, U> t");
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.MemberArrayOptics",
          "source instanceof Limits.MemberArray<?, U> t");
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.PairedLowerOptics",
          "source instanceof Limits.PairedLower<Integer, List<Number>> t");
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.PairedMemberOptics",
          "source instanceof Limits.PairedMember<String, Outer<String>.Base> t");
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
