// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.ImportOpticsProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What a generated prism's build side requires of the focus type.
 *
 * <p>A prism runs both ways. The generated one narrows through the hint and builds back with
 * identity, {@code value -> value}, which is the function {@code Prism.build} stands up — so it is
 * only a source when the focus is one. A focus that is a value rather than a variant, {@code
 * Prism<Value, String>}, has no build side the processor could write, and used to reach javac as an
 * error inside a file the author never wrote (issue #755).
 *
 * <p>The requirement belongs to the prism rather than to either hint, so both are held to it.
 */
@DisplayName("A generated prism's build side")
class PrismBuildSideTest {

  /** A check-and-extract API, with one variant getter and one value getter. */
  private static final JavaFileObject VALUE =
      JavaFileObjects.forSourceString(
          "com.external.Value",
          """
          package com.external;

          public class Value {
              public boolean isNested() { return true; }
              public Nested asNested() { return null; }
              public boolean isString() { return true; }
              public String asString() { return ""; }
          }
          """);

  private static final JavaFileObject NESTED =
      JavaFileObjects.forSourceString(
          "com.external.Nested",
          """
          package com.external;

          public class Nested extends Value {}
          """);

  /** A subtype reached through two unrelated supertypes, only one of which is the source. */
  private static final JavaFileObject BASE =
      JavaFileObjects.forSourceString(
          "com.external.Base",
          """
          package com.external;

          public interface Base {}
          """);

  private static final JavaFileObject MARKER =
      JavaFileObjects.forSourceString(
          "com.external.Marker",
          """
          package com.external;

          public interface Marker {}
          """);

  private static final JavaFileObject SUB =
      JavaFileObjects.forSourceString(
          "com.external.Sub",
          """
          package com.external;

          public class Sub implements Base, Marker {}
          """);

  /** A generic hierarchy whose subtype no instantiation of the source can be. */
  private static final JavaFileObject NODE =
      JavaFileObjects.forSourceString(
          "com.external.Node",
          """
          package com.external;

          public class Node<X> {}
          """);

  private static final JavaFileObject PAIR =
      JavaFileObjects.forSourceString(
          "com.external.Pair",
          """
          package com.external;

          public class Pair<A, B> {}
          """);

  private static final JavaFileObject TWIN =
      JavaFileObjects.forSourceString(
          "com.external.Twin",
          """
          package com.external;

          public class Twin<X> extends Node<Pair<X, X>> {}
          """);

  /** Compiles a spec interface body, holding the generated source to a consuming build's lints. */
  private Compilation compile(String specBody) {
    var specInterface =
        JavaFileObjects.forSourceString(
            "com.myapp.SubjectOpticsSpec",
            """
            package com.myapp;

            import com.external.Base;
            import com.external.Marker;
            import com.external.Nested;
            import com.external.Node;
            import com.external.Pair;
            import com.external.Sub;
            import com.external.Twin;
            import com.external.Value;
            import org.higherkindedj.optics.Prism;
            import org.higherkindedj.optics.annotations.ImportOptics;
            import org.higherkindedj.optics.annotations.InstanceOf;
            import org.higherkindedj.optics.annotations.MatchWhen;
            import org.higherkindedj.optics.annotations.OpticsSpec;

            @ImportOptics
            %s
            """
                .formatted(specBody));

    return javac()
        .withProcessors(new ImportOpticsProcessor())
        .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
        .compile(VALUE, NESTED, BASE, MARKER, SUB, NODE, PAIR, TWIN, specInterface);
  }

  @Nested
  @DisplayName("holds both hints to it")
  class HoldsBothHints {

    @Test
    @DisplayName("@MatchWhen: rejects a getter's value type, which cannot be built back")
    void rejectsAMatchWhenValueFocus() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Value> {
                  @MatchWhen(predicate = "isString", getter = "asString")
                  Prism<Value, String> string();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("focuses 'String', which is not a 'Value'");
      assertThat(compilation).hadErrorContaining("builds back with identity");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("@InstanceOf: rejects a supertype the source does not share")
    void rejectsAnInstanceOfMarkerFocus() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Base> {
                  @InstanceOf(Sub.class)
                  Prism<Base, Marker> marker();
              }""");

      // Sub is a Marker, so the narrowing itself is sound - it is the way back that is not.
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("focuses 'Marker', which is not a 'Base'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("@InstanceOf: rejects a narrowed type no instantiation of the source can be")
    void rejectsAnInstanceOfFocusTheSourceCannotBe() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @InstanceOf(Twin.class)
                  Prism<Node<U>, Twin<?>> twin();
              }""");

      // Twin<?> is what the test earns, so #733's check passes it. A Twin<?> is a
      // Node<Pair<?, ?>>, which a Node<U> is not, so the build side is where it stops.
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("focuses 'Twin<?>', which is not a 'Node<U>'");
      assertThat(compilation).hadErrorCount(1);
    }
  }

  @Nested
  @DisplayName("leaves a focus that is a source alone")
  class Accepts {

    @Test
    @DisplayName("@MatchWhen: a getter returning a variant of the source")
    void acceptsAMatchWhenVariantFocus() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Value> {
                  @MatchWhen(predicate = "isNested", getter = "asNested")
                  Prism<Value, Nested> nested();
              }""");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.myapp.SubjectOptics", "source.isNested()");
    }

    @Test
    @DisplayName("@InstanceOf: a subtype of the source")
    void acceptsAnInstanceOfSubtypeFocus() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Base> {
                  @InstanceOf(Sub.class)
                  Prism<Base, Sub> sub();
              }""");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.myapp.SubjectOptics", "source instanceof Sub");
    }

    @Test
    @DisplayName("a focus that is the source itself")
    void acceptsTheSourceAsItsOwnFocus() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Value> {
                  @MatchWhen(predicate = "isNested", getter = "asNested")
                  Prism<Value, Value> nested();
              }""");

      assertThat(compilation).succeeded();
    }
  }

  @Test
  @DisplayName("is asked after the hint, so a method carrying none is told that first")
  void reportsTheMissingHintFirst() {
    var compilation =
        compile(
            """
            public interface SubjectOpticsSpec extends OpticsSpec<Value> {
                Prism<Value, String> string();
            }""");

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("requires a prism hint annotation");
    assertThat(compilation).hadErrorCount(1);
  }
}
