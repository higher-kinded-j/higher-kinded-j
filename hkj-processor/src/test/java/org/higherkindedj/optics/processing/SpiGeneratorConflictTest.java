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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests exercising the equal-priority SPI conflict warning and the empty-optic-expression
 * fallbacks, using the test-scope marker generators registered in {@code META-INF/services} (see
 * {@code org.higherkindedj.optics.processing.testspi.TestMarkerGenerators}).
 *
 * <p>The {@code Dup} marker is supported by two equal-priority generators plus one lower-priority
 * generator, so resolving it warns about the conflict, picks the first match, and skips the
 * lower-priority one. Neither {@code Dup} nor {@code Solo} generators provide an optic expression,
 * so navigator widening falls back to the plain {@code .each()}/{@code .nullable()} forms.
 */
@DisplayName("SPI generator conflicts and fallbacks")
class SpiGeneratorConflictTest {

  private static final JavaFileObject DUP_MARKER =
      JavaFileObjects.forSourceString(
          "com.example.hkjtest.Dup",
          """
          package com.example.hkjtest;

          public class Dup<T> {}
          """);

  private static final JavaFileObject SOLO_MARKER =
      JavaFileObjects.forSourceString(
          "com.example.hkjtest.Solo",
          """
          package com.example.hkjtest;

          public class Solo<T> {}
          """);

  private static final JavaFileObject BOX_MARKER =
      JavaFileObjects.forSourceString(
          "com.example.hkjtest.Box",
          """
          package com.example.hkjtest;

          public class Box<T> {}
          """);

  @Nested
  @DisplayName("FocusProcessor conflict warning")
  class FocusConflictWarning {

    @Test
    @DisplayName("should warn about equal-priority SPI providers and use the first match")
    void shouldWarnAboutEqualPrioritySpiProviders() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Conflicted",
              """
              package com.example;

              import com.example.hkjtest.Dup;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Conflicted(String x, Dup<String> d, Optional<Dup<String>> od) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(DUP_MARKER, source);

      assertThat(compilation).succeeded();
      // Two equal-priority Dup generators: warned for the direct field (with element context)
      // and again for the nested Optional<Dup<String>> analysis (without element context).
      assertThat(compilation)
          .hadWarningContaining("Multiple TraversableGenerator SPI providers with equal priority");
      // Dup is ZERO_OR_MORE and widenCollections is off, so the field stays a FocusPath.
      assertGeneratedCodeContains(
          compilation, "com.example.ConflictedFocus", "FocusPath<Conflicted, Dup<String>> d()");
    }
  }

  @Nested
  @DisplayName("Navigator conflict warning and widening fallbacks")
  class NavigatorConflictAndFallbacks {

    @Test
    @DisplayName("should warn and fall back to plain widening for expression-less SPI containers")
    void shouldWarnAndFallBackForExpressionLessSpiContainers() {
      final JavaFileObject targetSource =
          JavaFileObjects.forSourceString(
              "com.example.NavTarget",
              """
              package com.example;

              import com.example.hkjtest.Box;
              import com.example.hkjtest.Dup;
              import com.example.hkjtest.Solo;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              @SuppressWarnings("rawtypes")
              public record NavTarget(
                  String label, Dup<String> d, Solo<String> s, Dup rawDup, Dup<?> wildDup,
                  Box<String> boxed) {}
              """);

      final JavaFileObject rootSource =
          JavaFileObjects.forSourceString(
              "com.example.NavRoot",
              """
              package com.example;

              import com.example.hkjtest.Dup;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              @SuppressWarnings("rawtypes")
              public record NavRoot(String name, NavTarget target, Dup rawTop) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(DUP_MARKER, SOLO_MARKER, BOX_MARKER, targetSource, rootSource);

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadWarningContaining("Multiple TraversableGenerator SPI providers with equal priority");

      // Dup (ZERO_OR_MORE, no optic expression) falls back to .each() inside the navigator.
      assertGeneratedCodeContains(
          compilation, "com.example.NavRootFocus", "TraversalPath<S, String> d()");
      // Solo (ZERO_OR_ONE, no optic expression) falls back to .nullable().
      assertGeneratedCodeContains(
          compilation, "com.example.NavRootFocus", "AffinePath<S, String> s()");
    }

    @Test
    @DisplayName("should stop nested recursion at raw SPI inner containers")
    void shouldStopNestedRecursionAtRawSpiInnerContainers() {
      // Optional<Solo> (raw inner ZERO_OR_ONE SPI type): the inner focus type is unknown, so
      // recursion stops and the deep inner type falls back to Object. Optional<Dup> does the
      // same for a ZERO_OR_MORE SPI type under widenCollections.
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.RawSpiInner",
              """
              package com.example;

              import com.example.hkjtest.Dup;
              import com.example.hkjtest.Solo;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(widenCollections = true)
              @SuppressWarnings("rawtypes")
              public record RawSpiInner(Optional<Solo> c, Optional<Dup> od) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(DUP_MARKER, SOLO_MARKER, source);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.RawSpiInnerFocus", "AffinePath<RawSpiInner, Object> c()");
      assertGeneratedCodeContains(
          compilation, "com.example.RawSpiInnerFocus", "TraversalPath<RawSpiInner, Object> od()");
    }

    @Test
    @DisplayName("should widen Box fields to Object when the focus index has no type argument")
    void shouldWidenBoxFieldsToObjectWhenFocusIndexOutOfRange() {
      // BoxIndexOneGenerator focuses on type argument 1, which Box<T> never has, so the focus
      // type falls back to Object throughout.
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.BoxWiden",
              """
              package com.example;

              import com.example.hkjtest.Box;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(widenCollections = true)
              public record BoxWiden(String name, Box<String> boxed) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(BOX_MARKER, source);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.BoxWidenFocus", "TraversalPath<BoxWiden, Object> boxed()");
    }
  }
}
