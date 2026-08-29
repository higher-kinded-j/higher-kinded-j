// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static java.util.stream.Collectors.joining;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins {@code @Nullable} widening against the {@code @Target} each vendor actually declares.
 *
 * <p>Where the annotation lands is decided by that {@code @Target}, not by the record component:
 * javac copies a declaration annotation to every declaration it is applicable to, and a {@code
 * TYPE_USE} annotation lands on the component's type instead. A stand-in annotation with a wider
 * target than the real one proves nothing about the real one: a stand-in that also targets {@code
 * RECORD_COMPONENT} makes every shape look detectable. Each case here therefore carries the target
 * of the artefact it stands for.
 *
 * <p>JSpecify is on the test classpath, so the JSpecify case uses the published annotation itself.
 * The other five are declared here with the {@code @Target} their published artefact carries.
 */
@DisplayName("@Nullable Detection Across Vendor @Target Shapes")
class NullableAnnotationDetectionTest {

  @Nested
  @DisplayName("Real JSpecify Annotation")
  class RealJSpecify {

    @Test
    @DisplayName("TYPE_USE @Nullable from the published artefact widens to AffinePath")
    void typeUseNullableWidensToAffinePath() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.User",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus
              public record User(String name, @Nullable String nickname) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.UserFocus", "AffinePath<User, String> nickname()");
      assertGeneratedCodeContains(compilation, "com.example.UserFocus", ".nullable()");
    }

    @Test
    @DisplayName("TYPE_USE @Nullable on a type argument does not widen the field itself")
    void nullableElementDoesNotWidenTheField() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Tags",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus
              public record Tags(String owner, java.util.List<@Nullable String> labels) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();
      // The element annotation says nothing about the field, so the list widens as any other
      // list does: by its elements, not by its nullability. It does describe the element the
      // traversal arrives at, which is why the focus keeps it.
      assertGeneratedCodeContains(
          compilation, "com.example.TagsFocus", "TraversalPath<Tags, @Nullable String> labels()");
      assertGeneratedCodeDoesNotContain(compilation, "com.example.TagsFocus", ".nullable()");
    }

    @Test
    @DisplayName("a nullable array widens, an array of nullable elements does not")
    void arrayNullabilityFollowsAnnotationPosition() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Readings",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus
              public record Readings(String @Nullable [] samples, @Nullable String[] labels) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();
      // `String @Nullable []` annotates the array; `@Nullable String[]` annotates its elements.
      assertGeneratedCodeContains(
          compilation, "com.example.ReadingsFocus", "AffinePath<Readings, String[]> samples()");
      assertGeneratedCodeContains(
          compilation,
          "com.example.ReadingsFocus",
          "FocusPath<Readings, @Nullable String[]> labels()");
    }

    @Test
    @DisplayName("the widening consumes the nullness and nothing else at that position")
    void theWideningConsumesTheNullnessAndNothingElse() {
      final JavaFileObject tag =
          JavaFileObjects.forSourceString(
              "com.example.Tag",
              """
              package com.example;
              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;
              @Target(ElementType.TYPE_USE)
              public @interface Tag {}
              """);
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Marked",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus
              public record Marked(@Tag @Nullable String note) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(tag, source);

      assertThat(compilation).succeeded();
      // .nullable() rules the null out, so @Nullable goes. It says nothing about @Tag, which
      // describes the value the affine does yield and so stays on the focus.
      assertGeneratedCodeContains(
          compilation, "com.example.MarkedFocus", "AffinePath<Marked, @Tag String> note()");
    }

    @Test
    @DisplayName("an unannotated field stays a FocusPath")
    void unannotatedFieldStaysFocusPath() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Plain",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Plain(String name) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.PlainFocus", "FocusPath<Plain, String> name()");
      assertGeneratedCodeDoesNotContain(compilation, "com.example.PlainFocus", ".nullable()");
    }
  }

  @Nested
  @DisplayName("Container Precedence")
  class ContainerPrecedence {

    /**
     * A container decides the path kind on its own, so a {@code @Nullable} container widens by its
     * contents and not by its nullability. Navigators have to agree with the static Focus methods
     * here: {@code .nullable()} on a container field would type the path by the element while the
     * path actually holds the container.
     */
    @Test
    @DisplayName("navigator methods widen a @Nullable container the way the Focus methods do")
    void navigatorAgreesWithFocusMethodsOnNullableContainers() {
      final JavaFileObject item =
          JavaFileObjects.forSourceString(
              "com.example.Item",
              """
              package com.example;
              import java.util.List;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus(generateNavigators = true)
              public record Item(String sku, @Nullable List<String> tags, @Nullable Optional<String> note) {}
              """);
      final JavaFileObject basket =
          JavaFileObjects.forSourceString(
              "com.example.Basket",
              """
              package com.example;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus(generateNavigators = true)
              public record Basket(String id, @Nullable List<Item> items, @Nullable Item single) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(item, basket);

      assertThat(compilation).succeeded();
      // The static methods: the list traverses, the plain record field widens on nullability.
      assertGeneratedCodeContains(
          compilation, "com.example.BasketFocus", "TraversalPath<Basket, Item> items()");
      assertGeneratedCodeContains(
          compilation, "com.example.BasketFocus", "SingleNavigator<Basket> single()");
      // The navigator into that nullable record reports the same kinds for the same fields.
      assertGeneratedCodeContains(
          compilation, "com.example.BasketFocus", "TraversalPath<S, String> tags()");
      assertGeneratedCodeContains(
          compilation, "com.example.BasketFocus", "AffinePath<S, String> note()");
      assertGeneratedCodeContains(
          compilation, "com.example.BasketFocus", "AffinePath<S, String> sku()");
    }
  }

  @Nested
  @DisplayName("Vendor @Target Shapes")
  class VendorTargets {

    /** A recognised annotation and the {@code ElementType}s its published artefact targets. */
    record Vendor(String annotation, List<String> targets) {
      @Override
      public String toString() {
        return annotation + (targets.isEmpty() ? " (no @Target)" : " " + targets);
      }
    }

    static Stream<Vendor> vendors() {
      return Stream.of(
          // JSR-305 and Jakarta declare no @Target at all, which makes them applicable to every
          // declaration context, the record component included.
          new Vendor("javax.annotation.Nullable", List.of()),
          new Vendor("jakarta.annotation.Nullable", List.of()),
          // The rest are declaration annotations that reach the accessor.
          new Vendor(
              "org.jetbrains.annotations.Nullable",
              List.of("METHOD", "FIELD", "PARAMETER", "LOCAL_VARIABLE", "TYPE_USE")),
          new Vendor(
              "androidx.annotation.Nullable",
              List.of(
                  "METHOD", "PARAMETER", "FIELD", "LOCAL_VARIABLE", "ANNOTATION_TYPE", "PACKAGE")),
          new Vendor(
              "edu.umd.cs.findbugs.annotations.Nullable",
              List.of("FIELD", "METHOD", "PARAMETER", "LOCAL_VARIABLE")));
    }

    @ParameterizedTest
    @MethodSource("vendors")
    @DisplayName("widens the annotated field to an AffinePath")
    void vendorNullableWidensToAffinePath(Vendor vendor) {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Profile",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Profile(String id, @%s String bio) {}
              """
                  .formatted(vendor.annotation()));

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(standIn(vendor), source);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.ProfileFocus", "AffinePath<Profile, String> bio()");
      assertGeneratedCodeContains(compilation, "com.example.ProfileFocus", ".nullable()");
    }

    /** Declares the vendor annotation with the {@code @Target} its published artefact carries. */
    private static JavaFileObject standIn(Vendor vendor) {
      final int lastDot = vendor.annotation().lastIndexOf('.');
      final String target =
          vendor.targets().isEmpty()
              ? ""
              : vendor.targets().stream()
                  .map("ElementType."::concat)
                  .collect(joining(", ", "@Target({", "})"));
      return JavaFileObjects.forSourceString(
          vendor.annotation(),
          """
          package %s;
          import java.lang.annotation.*;
          %s
          @Retention(RetentionPolicy.RUNTIME)
          public @interface %s {}
          """
              .formatted(
                  vendor.annotation().substring(0, lastDot),
                  target,
                  vendor.annotation().substring(lastDot + 1)));
    }
  }
}
