// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;
import static org.higherkindedj.optics.processing.testspi.TestMarkerGenerators.opticMarker;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper.CompiledResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests exercising the equal-priority SPI conflict warning and generators that name no optic
 * expression, using the test-scope marker generators registered in {@code META-INF/services} (see
 * {@code org.higherkindedj.optics.processing.testspi.TestMarkerGenerators}).
 *
 * <p>The {@code Dup} marker is supported by two equal-priority generators plus one lower-priority
 * generator, so resolving it warns about the conflict, picks the first registered, and skips the
 * lower-priority one. Neither the {@code Dup} nor the {@code Solo} generators name an optic, so
 * their containers take no part in Focus widening: a field holding one stays in focus as a {@code
 * FocusPath}, as if no generator supported it.
 */
@DisplayName("SPI generator conflicts and expression-less containers")
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

  private static final JavaFileObject BOX_MARKER = opticMarker("Box");

  private static final JavaFileObject BLANK_MARKER =
      JavaFileObjects.forSourceString(
          "com.example.hkjtest.Blank",
          """
          package com.example.hkjtest;

          public class Blank<T> {}
          """);

  @Nested
  @DisplayName("FocusProcessor conflict warning")
  class FocusConflictWarning {

    @Test
    @DisplayName("should warn about equal-priority SPI providers and use the first registered")
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
      // Two equal-priority Dup generators: the direct field warns, and the Dup nested inside
      // the Optional warns too, anchored to the od component that reaches it.
      assertThat(compilation)
          .hadWarningContaining("Multiple TraversableGenerator SPI providers with equal priority");
      // Dup is ZERO_OR_MORE and widenCollections is off, so the field stays a FocusPath.
      assertGeneratedCodeContains(
          compilation, "com.example.ConflictedFocus", "FocusPath<Conflicted, Dup<String>> d()");
    }
  }

  @Nested
  @DisplayName("Navigator conflict warning and expression-less containers")
  class NavigatorConflictAndExpressionLessContainers {

    @Test
    @DisplayName("should warn, and leave expression-less SPI containers in focus")
    void shouldWarnAndLeaveExpressionLessSpiContainersInFocus() {
      final JavaFileObject targetSource =
          JavaFileObjects.forSourceString(
              "com.example.NavTarget",
              """
              package com.example;

              import com.example.hkjtest.Box;
              import com.example.hkjtest.Dup;
              import com.example.hkjtest.Solo;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true, widenCollections = true)
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

      // Neither Dup nor Solo names an optic, so even under widenCollections each stays in focus
      // rather than widening through the Optional-only .some() or the List-only .each().
      assertGeneratedCodeContains(
          compilation, "com.example.NavRootFocus", "FocusPath<S, Dup<String>> d()");
      assertGeneratedCodeContains(
          compilation, "com.example.NavRootFocus", "FocusPath<S, Solo<String>> s()");
    }

    @Test
    @DisplayName("should stop nested widening at an expression-less SPI container")
    void shouldStopNestedWideningAtAnExpressionLessSpiContainer() {
      // The Optional widens, and the raw Solo or Dup inside it is left in focus as it is: a
      // generator that names no optic is never asked to widen, raw or not.
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
          compilation, "com.example.RawSpiInnerFocus", "AffinePath<RawSpiInner, Solo> c()");
      assertGeneratedCodeContains(
          compilation, "com.example.RawSpiInnerFocus", "AffinePath<RawSpiInner, Dup> od()");
    }

    @Test
    @DisplayName("should leave wildcard containers whose generator names no optic in focus")
    void shouldLeaveWildcardContainersWithoutOpticExpressionInFocus() {
      // Nothing widens Solo or Dup, so their wildcards are never asked for an optic instance and
      // the declaration is not rejected.
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.WildcardSpiInner",
              """
              package com.example;

              import com.example.hkjtest.Dup;
              import com.example.hkjtest.Solo;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(widenCollections = true)
              public record WildcardSpiInner(Solo<? extends String> s, Dup<? extends String> d) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(DUP_MARKER, SOLO_MARKER, source);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.WildcardSpiInnerFocus",
          "FocusPath<WildcardSpiInner, Solo<? extends String>> s()");
      assertGeneratedCodeContains(
          compilation,
          "com.example.WildcardSpiInnerFocus",
          "FocusPath<WildcardSpiInner, Dup<? extends String>> d()");
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

  @Nested
  @DisplayName("Reading through an expression-less container")
  class ReadingThroughAnExpressionLessContainer {

    private static final JavaFileObject HELD =
        JavaFileObjects.forSourceString(
            "com.example.Held",
            """
            package com.example;

            import com.example.hkjtest.Dup;
            import com.example.hkjtest.Solo;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(widenCollections = true)
            public record Held(Solo<String> solo, Dup<String> dup) {}
            """);

    /** The generated path for a component, which must stay a FocusPath on the container. */
    @SuppressWarnings("unchecked") // the generated method's type arguments erase to these
    private static FocusPath<Object, Object> path(CompiledResult result, String component)
        throws ReflectiveOperationException {
      Object path = result.invokeStatic("com.example.HeldFocus", component);
      assertThat(path).isInstanceOf(FocusPath.class);
      return (FocusPath<Object, Object>) path;
    }

    @Test
    @DisplayName(
        "should read and write the container itself rather than cast it to Optional or List")
    void shouldReadAndWriteTheContainerItself() throws ReflectiveOperationException {
      // Widened through the Optional-only .some() or the List-only .each(), these paths would
      // compile and then throw ClassCastException on their first read or write.
      CompiledResult result =
          RuntimeCompilationHelper.compileWith(new FocusProcessor(), SOLO_MARKER, DUP_MARKER, HELD);
      Object solo = result.loadClass("com.example.hkjtest.Solo").getConstructor().newInstance();
      Object dup = result.loadClass("com.example.hkjtest.Dup").getConstructor().newInstance();
      Object held =
          result.loadClass("com.example.Held").getDeclaredConstructors()[0].newInstance(solo, dup);

      assertThat(path(result, "solo").get(held)).isSameAs(solo);
      assertThat(path(result, "dup").get(held)).isSameAs(dup);

      Object otherSolo =
          result.loadClass("com.example.hkjtest.Solo").getConstructor().newInstance();
      Object written = path(result, "solo").set(otherSolo, held);
      assertThat(path(result, "solo").get(written)).isSameAs(otherSolo);
      assertThat(path(result, "dup").get(written)).isSameAs(dup);
    }
  }

  @Nested
  @DisplayName("Expression-less containers in other positions")
  class ExpressionLessContainersInOtherPositions {

    @Test
    @DisplayName("should treat a blank optic expression as naming no optic")
    void shouldTreatABlankOpticExpressionAsNamingNoOptic() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Blanked",
              """
              package com.example;

              import com.example.hkjtest.Blank;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Blanked(Blank<String> b) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(BLANK_MARKER, source);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.BlankedFocus", "FocusPath<Blanked, Blank<String>> b()");
    }

    @Test
    @DisplayName("should generate no navigator over a navigable element it cannot widen to")
    void shouldGenerateNoNavigatorOverANavigableElementItCannotWidenTo() {
      // The element is a navigable record, but nothing widens the container that holds it, so
      // the static method and the navigator both stop at the container, and no navigator class
      // is generated over it.
      final JavaFileObject address =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Address(String street) {}
              """);
      final JavaFileObject holder =
          JavaFileObjects.forSourceString(
              "com.example.Holder",
              """
              package com.example;

              import com.example.hkjtest.Dup;
              import com.example.hkjtest.Solo;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true, widenCollections = true)
              public record Holder(Solo<Address> solo, Dup<Address> dup) {}
              """);
      final JavaFileObject outer =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Outer(Holder holder) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(SOLO_MARKER, DUP_MARKER, address, holder, outer);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.HolderFocus", "FocusPath<Holder, Solo<Address>> solo()");
      assertGeneratedCodeContains(
          compilation, "com.example.HolderFocus", "FocusPath<Holder, Dup<Address>> dup()");
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "FocusPath<S, Solo<Address>> solo()");
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "FocusPath<S, Dup<Address>> dup()");
      assertGeneratedCodeDoesNotContain(compilation, "com.example.HolderFocus", "SoloNavigator");
      assertGeneratedCodeDoesNotContain(compilation, "com.example.HolderFocus", "DupNavigator");
    }
  }
}
