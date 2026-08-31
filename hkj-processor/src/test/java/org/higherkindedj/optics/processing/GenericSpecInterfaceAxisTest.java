// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One case per copy strategy and per optic hint, each against a spec interface that declares its
 * own type parameter.
 *
 * <p>A generic spec is where a processor that reads the source type's <em>declaration</em> and one
 * that reads its <em>instantiation</em> disagree, and every optic method here is written so that
 * the spec's parameter is named under a different letter from the source type's. Where the two
 * agree by coincidence the fault is invisible, so this file holds them apart on purpose.
 *
 * <p>{@code @Wither} and {@code @ViaBuilder} rebuild through a method and read no member of the
 * source type, so they are structurally blind to that class of fault. They are here to keep the
 * axis complete rather than because they are at risk.
 *
 * <p>The source-type axis also runs here: a source naming a raw type anywhere (itself, a generic
 * enclosing type written bare, a raw type argument) is refused at the declaration, and the accepted
 * shapes are compiled under the consuming build's {@code -Xlint:unchecked,rawtypes -Werror}, so a
 * warning in the generated file fails the test the way it fails the build.
 */
@DisplayName("Every strategy and hint on a generic spec interface")
class GenericSpecInterfaceAxisTest {

  /** A generic class carrying one of every shape a strategy reaches for. */
  private static final JavaFileObject BOX =
      JavaFileObjects.forSourceString(
          "com.external.Box",
          """
          package com.external;

          import java.util.List;

          public class Box<X> extends Base<X> {
              private X content;
              private List<X> items;
              public Box() {}
              public Box(X content, String label) { this.content = content; this.label = label; }
              public Box(Base<X> other) { this.label = other.label; }
              public X content() { return content; }
              public String label() { return label; }
              public List<X> items() { return items; }
              public void setLabel(String label) { this.label = label; }
              public Box<X> withLabel(String label) {
                  Box<X> copy = new Box<>();
                  copy.content = content;
                  copy.label = label;
                  copy.items = items;
                  return copy;
              }
              public Box<X> withItems(List<X> items) {
                  Box<X> copy = new Box<>();
                  copy.content = content;
                  copy.label = label;
                  copy.items = items;
                  return copy;
              }
              public Builder<X> toBuilder() { return new Builder<>(); }
              public static final class Builder<X> {
                  private String label;
                  public Builder<X> label(String label) { this.label = label; return this; }
                  public Box<X> build() { return new Box<>(); }
              }
          }
          """);

  private static final JavaFileObject BASE =
      JavaFileObjects.forSourceString(
          "com.external.Base",
          """
          package com.external;

          public class Base<X> {
              protected String label;
          }
          """);

  private static final JavaFileObject SHAPE =
      JavaFileObjects.forSourceString(
          "com.external.Shape",
          """
          package com.external;

          public sealed interface Shape<X> permits Circle {}
          """);

  private static final JavaFileObject CIRCLE =
      JavaFileObjects.forSourceString(
          "com.external.Circle",
          """
          package com.external;

          public record Circle<X>(X tag) implements Shape<X> {}
          """);

  private static final JavaFileObject NODE =
      JavaFileObjects.forSourceString(
          "com.external.Node",
          """
          package com.external;

          public class Node<X> {
              public boolean isLeaf() { return true; }
              public Leaf<X> asLeaf() { return null; }
          }
          """);

  private static final JavaFileObject LEAF =
      JavaFileObjects.forSourceString(
          "com.external.Leaf",
          """
          package com.external;

          public class Leaf<X> extends Node<X> {}
          """);

  /**
   * A generic class whose member class declares no parameters of its own.
   *
   * <p>{@code Holder} is where the enclosing instantiation is the only instantiation: it carries no
   * type arguments, so a reader that asks only for its own answers "nothing to substitute" and
   * hands back {@code X}. This is the one fixture whose spec names a concrete argument rather than
   * its own {@code U} — the enclosing type has to supply a real container for the difference
   * between {@code X} and {@code List<String>} to be visible at all.
   */
  private static final JavaFileObject OUTER =
      JavaFileObjects.forSourceString(
          "com.external.Outer",
          """
          package com.external;

          public class Outer<X> {
              public class Holder {
                  private X items;
                  public X items() { return items; }
                  public Holder withItems(X items) { return this; }
              }
          }
          """);

  private static final JavaFileObject BOX_TRAVERSALS =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.optics.BoxTraversals",
          """
          package org.higherkindedj.optics;

          import com.external.Box;

          public final class BoxTraversals {
              private BoxTraversals() {}
              public static <U> Traversal<Box<U>, U> items() { return null; }
          }
          """);

  /** The full refusal for a raw source type, pinned verbatim once. */
  private static final String RAW_SOURCE_ERROR =
      "@ImportOptics: 'SubjectOpticsSpec' declares OpticsSpec<Box>, which names the raw type"
          + " 'Box'. Every generated optic repeats the source type verbatim, and a raw type in"
          + " generated source is a [rawtypes] warning that the suppression on your own"
          + " declaration does not cover; a constructor read under a raw type erases its"
          + " parameters besides. Name 'Box's type arguments in the OpticsSpec clause; a spec"
          + " whose optics should stay generic declares its own type parameters and passes them"
          + " on (OpticsSpec<Box<U>>).";

  /**
   * Compiles a spec interface body. The spec's parameter is always {@code U} while every source
   * type declares {@code X}, so a processor reading the wrong one cannot pass by coincidence.
   */
  private Compilation compile(String specBody) {
    var specInterface =
        JavaFileObjects.forSourceString(
            "com.myapp.SubjectOpticsSpec",
            """
            package com.myapp;

            import com.external.Base;
            import com.external.Box;
            import com.external.Circle;
            import com.external.Leaf;
            import com.external.Node;
            import com.external.Outer;
            import com.external.Shape;
            import java.util.List;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.Prism;
            import org.higherkindedj.optics.Traversal;
            import org.higherkindedj.optics.annotations.ImportOptics;
            import org.higherkindedj.optics.annotations.InstanceOf;
            import org.higherkindedj.optics.annotations.MatchWhen;
            import org.higherkindedj.optics.annotations.OpticsSpec;
            import org.higherkindedj.optics.annotations.ThroughField;
            import org.higherkindedj.optics.annotations.TraverseWith;
            import org.higherkindedj.optics.annotations.ViaBuilder;
            import org.higherkindedj.optics.annotations.ViaConstructor;
            import org.higherkindedj.optics.annotations.ViaCopyAndSet;
            import org.higherkindedj.optics.annotations.Wither;

            @ImportOptics
            %s
            """
                .formatted(specBody));

    // Every fixture, every time: the spec template imports them all, so a subset would fail on
    // the import rather than on anything the test is about.
    return javac()
        .withProcessors(new ImportOpticsProcessor())
        .compile(BASE, BOX, SHAPE, CIRCLE, NODE, LEAF, OUTER, BOX_TRAVERSALS, specInterface);
  }

  private void assertGeneratedSignature(Compilation compilation, String signature) {
    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(compilation, "com.myapp.SubjectOptics", signature);
  }

  @Test
  @DisplayName("@Wither rebuilds through a method, so it reads no member of the source type")
  void witherOnAGenericSpec() {
    var compilation =
        compile(
            """
            public interface SubjectOpticsSpec<U> extends OpticsSpec<Box<U>> {
                @Wither("withLabel")
                Lens<Box<U>, String> label();
            }""");

    assertGeneratedSignature(compilation, "public static <U> Lens<Box<U>, String> label()");
  }

  @Test
  @DisplayName("@ViaBuilder rebuilds through the builder the source type hands back")
  void viaBuilderOnAGenericSpec() {
    var compilation =
        compile(
            """
            public interface SubjectOpticsSpec<U> extends OpticsSpec<Box<U>> {
                @ViaBuilder(getter = "label", setter = "label")
                Lens<Box<U>, String> label();
            }""");

    assertGeneratedSignature(compilation, "public static <U> Lens<Box<U>, String> label()");
  }

  @Test
  @DisplayName("@ViaConstructor names the constructor arguments in the spec's own terms")
  void viaConstructorOnAGenericSpec() {
    var compilation =
        compile(
            """
            public interface SubjectOpticsSpec<U> extends OpticsSpec<Box<U>> {
                @ViaConstructor(parameterOrder = {"content", "label"})
                Lens<Box<U>, String> label();
            }""");

    assertGeneratedSignature(compilation, "public static <U> Lens<Box<U>, String> label()");
    assertGeneratedCodeContains(compilation, "com.myapp.SubjectOptics", "new Box<U>(");
  }

  @Test
  @DisplayName("@ViaCopyAndSet reads the copy constructor under the source type's instantiation")
  void viaCopyAndSetOnAGenericSpec() {
    var compilation =
        compile(
            """
            public interface SubjectOpticsSpec<U> extends OpticsSpec<Box<U>> {
                @ViaCopyAndSet(setter = "setLabel", copyConstructor = "com.external.Base")
                Lens<Box<U>, String> label();
            }""");

    assertGeneratedSignature(compilation, "public static <U> Lens<Box<U>, String> label()");
    // The parameter is declared Base<X>; under Box<U> it is Base<U>, which is what the cast names.
    assertGeneratedCodeContains(
        compilation, "com.myapp.SubjectOptics", "new Box<U>((Base<U>) source)");
  }

  @Test
  @DisplayName("@InstanceOf narrows a generic sealed hierarchy under the argument the source pins")
  void instanceOfOnAGenericSpec() {
    var compilation =
        compile(
            """
            public interface SubjectOpticsSpec<U> extends OpticsSpec<Shape<U>> {
                @InstanceOf(Circle.class)
                Prism<Shape<U>, Circle<U>> circle();
            }""");

    assertGeneratedSignature(compilation, "public static <U> Prism<Shape<U>, Circle<U>> circle()");
    // The annotation carries a class constant, which is always raw, but Circle<X> reaches Shape as
    // Shape<X>, so U is what the source pins X to and the test can name it.
    assertGeneratedCodeContains(
        compilation, "com.myapp.SubjectOptics", "source instanceof Circle<U>");
  }

  @Test
  @DisplayName("@MatchWhen narrows through the source type's own getter")
  void matchWhenOnAGenericSpec() {
    var compilation =
        compile(
            """
            public interface SubjectOpticsSpec<U> extends OpticsSpec<Node<U>> {
                @MatchWhen(predicate = "isLeaf", getter = "asLeaf")
                Prism<Node<U>, Leaf<U>> leaf();
            }""");

    assertGeneratedSignature(compilation, "public static <U> Prism<Node<U>, Leaf<U>> leaf()");
  }

  @Test
  @DisplayName("@ThroughField auto-detects the container under the source type's instantiation")
  void throughFieldOnAGenericSpec() {
    var compilation =
        compile(
            """
            public interface SubjectOpticsSpec<U> extends OpticsSpec<Box<U>> {
                @Wither("withItems")
                Lens<Box<U>, List<U>> items();

                @ThroughField(field = "items")
                Traversal<Box<U>, U> eachItem();
            }""");

    assertGeneratedSignature(compilation, "public static <U> Traversal<Box<U>, U> eachItem()");
  }

  @Test
  @DisplayName("@TraverseWith composes the traversal the spec names")
  void traverseWithOnAGenericSpec() {
    var compilation =
        compile(
            """
            public interface SubjectOpticsSpec<U> extends OpticsSpec<Box<U>> {
                @TraverseWith("org.higherkindedj.optics.BoxTraversals.items()")
                Traversal<Box<U>, U> eachItem();
            }""");

    assertGeneratedSignature(compilation, "public static <U> Traversal<Box<U>, U> eachItem()");
  }

  @Test
  @DisplayName("@ThroughField reads an accessor under the enclosing type's instantiation")
  void throughFieldOnAMemberOfAGenericOuter() {
    var compilation =
        compile(
            """
            public interface SubjectOpticsSpec extends OpticsSpec<Outer<List<String>>.Holder> {
                @Wither("withItems")
                Lens<Outer<List<String>>.Holder, List<String>> items();

                @ThroughField(field = "items")
                Traversal<Outer<List<String>>.Holder, String> eachItem();
            }""");

    // items() is declared 'X items()'. Read as declared it is a type variable and no container at
    // all, so auto-detection turns away a List the spec plainly named.
    assertGeneratedSignature(
        compilation, "public static Traversal<Outer<List<String>>.Holder, String> eachItem()");
  }

  /**
   * Compiles a spec interface body under the consuming build's flags, so a warning in the generated
   * file fails here the way it fails there. A separate, smaller template on purpose: only the
   * fixtures a row names are compiled, so the flags judge the generated file rather than the shared
   * fixture set.
   */
  private Compilation compileUnderConsumerFlags(String specBody) {
    var specInterface =
        JavaFileObjects.forSourceString(
            "com.myapp.SubjectOpticsSpec",
            """
            package com.myapp;

            import com.external.Box;
            import com.external.Registry;
            import java.util.List;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.annotations.ImportOptics;
            import org.higherkindedj.optics.annotations.OpticsSpec;
            import org.higherkindedj.optics.annotations.ViaConstructor;
            import org.higherkindedj.optics.annotations.Wither;

            @ImportOptics
            %s
            """
                .formatted(specBody));

    return javac()
        .withProcessors(new ImportOpticsProcessor())
        .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
        .compile(BASE, BOX, REGISTRY, specInterface);
  }

  /** A generic outer whose static nested type is not raw when written bare (JLS 4.8). */
  private static final JavaFileObject REGISTRY =
      JavaFileObjects.forSourceString(
          "com.external.Registry",
          """
          package com.external;

          public class Registry<X> {
              public static class Tag {
                  private String label;
                  public String label() { return label; }
                  public Tag withLabel(String label) { return this; }
              }
          }
          """);

  @Test
  @DisplayName("a raw source type is refused at the declaration, before any strategy runs")
  void rawSourceTypeRefused() {
    var compilation =
        compile(
            """
            public interface SubjectOpticsSpec extends OpticsSpec<Box> {
                @Wither("withLabel")
                Lens<Box, String> label();
            }""");

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(RAW_SOURCE_ERROR);
    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  @DisplayName("a raw enclosing type is refused: JLS 4.8 makes the member type raw too")
  void rawEnclosingSourceTypeRefused() {
    var compilation =
        compile(
            """
            public interface SubjectOpticsSpec extends OpticsSpec<Outer.Holder> {
                @Wither("withItems")
                Lens<Outer.Holder, String> items();
            }""");

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@ImportOptics: 'SubjectOpticsSpec' declares OpticsSpec<Outer.Holder>, which names"
                + " the raw type 'Outer'. Every generated optic repeats the source type verbatim,"
                + " and a raw type in generated source is a [rawtypes] warning that the"
                + " suppression on your own declaration does not cover; a constructor read under"
                + " a raw type erases its parameters besides. Name 'Outer's type arguments in the"
                + " OpticsSpec clause.");
    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  @DisplayName("a raw type argument is refused: the generated file repeats it verbatim")
  void rawTypeArgumentInSourceTypeRefused() {
    var compilation =
        compile(
            """
            @SuppressWarnings("rawtypes")
            public interface SubjectOpticsSpec extends OpticsSpec<Box<List>> {
                @Wither("withLabel")
                Lens<Box<List>, String> label();
            }""");

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("declares OpticsSpec<Box<List>>, which names the raw type 'List'.");
    assertThat(compilation)
        .hadErrorContaining("Name 'List's type arguments in the OpticsSpec clause.");
    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  @DisplayName("a raw source type is refused once, not once per @ViaConstructor member it erases")
  void rawSourceTypeRefusedWithViaConstructor() {
    var compilation =
        compile(
            """
            public interface SubjectOpticsSpec extends OpticsSpec<Box> {
                @Wither("withLabel")
                Lens<Box, String> label();

                @ViaConstructor(parameterOrder = {"content", "label"})
                Lens<Box, String> content();
            }""");

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(RAW_SOURCE_ERROR);
    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  @DisplayName("an instantiated source type generates optics that hold under -Werror")
  void instantiatedSourceTypeHoldsUnderConsumerFlags() {
    var compilation =
        compileUnderConsumerFlags(
            """
            public interface SubjectOpticsSpec extends OpticsSpec<Box<String>> {
                @Wither("withLabel")
                Lens<Box<String>, String> label();

                @ViaConstructor(parameterOrder = {"content", "label"})
                Lens<Box<String>, String> content();
            }""");

    assertGeneratedSignature(compilation, "public static Lens<Box<String>, String> label()");
  }

  @Test
  @DisplayName("a static nested type of a generic outer is not raw, and holds under -Werror")
  void staticNestedOfGenericOuterAccepted() {
    var compilation =
        compileUnderConsumerFlags(
            """
            public interface SubjectOpticsSpec extends OpticsSpec<Registry.Tag> {
                @Wither("withLabel")
                Lens<Registry.Tag, String> label();
            }""");

    assertGeneratedSignature(compilation, "public static Lens<Registry.Tag, String> label()");
  }

  @Test
  @DisplayName("a generic spec's generated optics hold under -Werror")
  void genericSpecHoldsUnderConsumerFlags() {
    var compilation =
        compileUnderConsumerFlags(
            """
            public interface SubjectOpticsSpec<U> extends OpticsSpec<Box<U>> {
                @ViaConstructor(parameterOrder = {"content", "label"})
                Lens<Box<U>, String> label();
            }""");

    assertGeneratedSignature(compilation, "public static <U> Lens<Box<U>, String> label()");
  }
}
