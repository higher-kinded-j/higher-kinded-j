// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests pinning that generator selection honours {@link
 * org.higherkindedj.optics.processing.spi.TraversableGenerator#priority()} rather than {@code
 * ServiceLoader} registration order, on every route that chooses a generator.
 *
 * <p>The test-scope {@code META-INF/services} file fixes a deliberately hostile order (see {@code
 * org.higherkindedj.optics.processing.testspi.TestMarkerGenerators}): the {@code PRIORITY_OVERRIDE}
 * generator for the {@code Pri} marker is registered <em>last</em>, and the {@code
 * PRIORITY_FALLBACK} generator for the {@code Fb} marker is registered <em>first</em>. The
 * first-match loops on the {@code @GenerateTraversals} and {@code @ImportOptics} routes got both
 * wrong. The Focus route already resolved by priority through a manual pre-sort in {@code
 * FocusProcessor.init}, so its cases here pin that the behaviour survives the sort moving into the
 * registry.
 *
 * <p>Which generator won is observable twice over: each marker generator's {@code modifyF} body
 * throws naming the generator, and the {@code Pri}/{@code Fb} winners and losers declare opposite
 * cardinalities, so the widened path tier names the winner too.
 */
@DisplayName("SPI generator priority resolution")
class SpiPriorityResolutionTest {

  private static final JavaFileObject PRI_MARKER =
      JavaFileObjects.forSourceString(
          "com.example.hkjtest.Pri",
          """
          package com.example.hkjtest;

          public class Pri<T> {}
          """);

  private static final JavaFileObject FB_MARKER =
      JavaFileObjects.forSourceString(
          "com.example.hkjtest.Fb",
          """
          package com.example.hkjtest;

          public class Fb<T> {}
          """);

  private static final JavaFileObject DUP_MARKER =
      JavaFileObjects.forSourceString(
          "com.example.hkjtest.Dup",
          """
          package com.example.hkjtest;

          public class Dup<T> {}
          """);

  @Nested
  @DisplayName("@GenerateTraversals route")
  class GenerateTraversalsRoute {

    @Test
    @DisplayName("should pick the override generator registered after the default it overrides")
    void shouldPickOverrideRegisteredLast() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.PriHolder",
              """
              package com.example;

              import com.example.hkjtest.Pri;
              import org.higherkindedj.optics.annotations.GenerateTraversals;

              @GenerateTraversals
              public record PriHolder(Pri<String> item) {}
              """);

      Compilation compilation =
          javac().withProcessors(new TraversalProcessor()).compile(PRI_MARKER, source);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.PriHolderTraversals", "PriOverrideGenerator");
      assertGeneratedCodeDoesNotContain(
          compilation, "com.example.PriHolderTraversals", "PriDefaultGenerator");
    }

    @Test
    @DisplayName("should pick the default generator over a fallback registered before it")
    void shouldPickDefaultOverEarlierFallback() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.FbHolder",
              """
              package com.example;

              import com.example.hkjtest.Fb;
              import org.higherkindedj.optics.annotations.GenerateTraversals;

              @GenerateTraversals
              public record FbHolder(Fb<String> item) {}
              """);

      Compilation compilation =
          javac().withProcessors(new TraversalProcessor()).compile(FB_MARKER, source);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.FbHolderTraversals", "FbDefaultGenerator");
      assertGeneratedCodeDoesNotContain(
          compilation, "com.example.FbHolderTraversals", "FbFallbackGenerator");
    }

    @Test
    @DisplayName("should warn about an equal-priority tie and keep the first registered")
    void shouldWarnOnEqualPriorityTie() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.DupHolder",
              """
              package com.example;

              import com.example.hkjtest.Dup;
              import org.higherkindedj.optics.annotations.GenerateTraversals;

              @GenerateTraversals
              public record DupHolder(Dup<String> item) {}
              """);

      Compilation compilation =
          javac().withProcessors(new TraversalProcessor()).compile(DUP_MARKER, source);

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadWarningContaining("Multiple TraversableGenerator SPI providers with equal priority");
      assertGeneratedCodeContains(
          compilation, "com.example.DupHolderTraversals", "DupGeneratorAlpha");
    }
  }

  @Nested
  @DisplayName("@GenerateFocus route")
  class GenerateFocusRoute {

    @Test
    @DisplayName("should widen by the override generator's cardinality, not the default's")
    void shouldWidenByOverrideCardinality() {
      // The override is ZERO_OR_ONE and the earlier-registered default is ZERO_OR_MORE, so an
      // AffinePath is itself the proof that priority resolution picked the override.
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.PriWiden",
              """
              package com.example;

              import com.example.hkjtest.Pri;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(widenCollections = true)
              public record PriWiden(String name, Pri<String> item) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(PRI_MARKER, source);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.PriWidenFocus", "AffinePath<PriWiden, String> item()");
    }

    @Test
    @DisplayName("should widen by the default generator's cardinality, not the fallback's")
    void shouldWidenByDefaultCardinalityOverEarlierFallback() {
      // The winning default is ZERO_OR_MORE and the first-registered fallback is ZERO_OR_ONE, so
      // a TraversalPath is itself the proof that the fallback lost.
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.FbWiden",
              """
              package com.example;

              import com.example.hkjtest.Fb;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(widenCollections = true)
              public record FbWiden(String name, Fb<String> item) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(FB_MARKER, source);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.FbWidenFocus", "TraversalPath<FbWiden, String> item()");
    }
  }
}
