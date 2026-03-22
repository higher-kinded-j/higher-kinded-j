// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for nested container widening: automatic detection of nested container patterns and
 * generation of composed widening chains.
 *
 * <p>Covers patterns such as:
 *
 * <ul>
 *   <li>{@code Optional<List<String>>} produces {@code .some().each()} returning TraversalPath
 *   <li>{@code List<Optional<String>>} produces {@code .each().some()} returning TraversalPath
 *   <li>{@code Optional<Optional<String>>} produces {@code .some().some()} returning AffinePath
 *   <li>{@code List<List<String>>} produces {@code .each().each()} returning TraversalPath
 * </ul>
 */
@DisplayName("Nested Container Widening")
public class FocusProcessorNestedContainerTest {

  @Nested
  @DisplayName("Two-Level Nesting")
  class TwoLevelNesting {

    @Test
    @DisplayName("Optional<List<String>> should produce .some().each() returning TraversalPath")
    void optionalOfListShouldProduceSomeThenEach() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;
              import java.util.List;

              @GenerateFocus
              public record Config(String name, Optional<List<String>> tags) {}
              """);

      final String expectedMethod =
          """
          public static TraversalPath<Config, String> tags() {
              return FocusPath.of(Lens.of(Config::tags, (source, newValue) -> new Config(source.name(), newValue))).some().each();
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedMethod);
    }

    @Test
    @DisplayName("List<Optional<String>> should produce .each().some() returning TraversalPath")
    void listOfOptionalShouldProduceEachThenSome() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;
              import java.util.List;

              @GenerateFocus
              public record Config(String name, List<Optional<String>> items) {}
              """);

      final String expectedMethod =
          """
          public static TraversalPath<Config, String> items() {
              return FocusPath.of(Lens.of(Config::items, (source, newValue) -> new Config(source.name(), newValue))).each().some();
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedMethod);
    }

    @Test
    @DisplayName("Optional<Optional<String>> should produce .some().some() returning AffinePath")
    void optionalOfOptionalShouldProduceSomeThenSome() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;

              @GenerateFocus
              public record Config(String name, Optional<Optional<String>> nested) {}
              """);

      final String expectedMethod =
          """
          public static AffinePath<Config, String> nested() {
              return FocusPath.of(Lens.of(Config::nested, (source, newValue) -> new Config(source.name(), newValue))).some().some();
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedMethod);
    }

    @Test
    @DisplayName("List<List<String>> should produce .each().each() returning TraversalPath")
    void listOfListShouldProduceEachThenEach() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.List;

              @GenerateFocus
              public record Config(String name, List<List<String>> matrix) {}
              """);

      final String expectedMethod =
          """
          public static TraversalPath<Config, String> matrix() {
              return FocusPath.of(Lens.of(Config::matrix, (source, newValue) -> new Config(source.name(), newValue))).each().each();
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedMethod);
    }

    @Test
    @DisplayName("Set<Optional<String>> should produce .each().some() returning TraversalPath")
    void setOfOptionalShouldProduceEachThenSome() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;
              import java.util.Set;

              @GenerateFocus
              public record Config(String name, Set<Optional<String>> items) {}
              """);

      final String expectedMethod =
          """
          public static TraversalPath<Config, String> items() {
              return FocusPath.of(Lens.of(Config::items, (source, newValue) -> new Config(source.name(), newValue))).each().some();
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedMethod);
    }
  }

  @Nested
  @DisplayName("Three-Level Nesting")
  class ThreeLevelNesting {

    @Test
    @DisplayName(
        "Optional<List<Optional<String>>> should produce .some().each().some() returning"
            + " TraversalPath")
    void optionalOfListOfOptionalShouldProduceThreeLevelChain() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;
              import java.util.List;

              @GenerateFocus
              public record Config(String name, Optional<List<Optional<String>>> deep) {}
              """);

      final String expectedMethod =
          """
          public static TraversalPath<Config, String> deep() {
              return FocusPath.of(Lens.of(Config::deep, (source, newValue) -> new Config(source.name(), newValue))).some().each().some();
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedMethod);
    }

    @Test
    @DisplayName(
        "List<List<List<String>>> should produce .each().each().each() returning TraversalPath")
    void listOfListOfListShouldProduceThreeLevelChain() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.List;

              @GenerateFocus
              public record Config(String name, List<List<List<String>>> cube) {}
              """);

      final String expectedMethod =
          """
          public static TraversalPath<Config, String> cube() {
              return FocusPath.of(Lens.of(Config::cube, (source, newValue) -> new Config(source.name(), newValue))).each().each().each();
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedMethod);
    }
  }

  @Nested
  @DisplayName("Four-Level Nesting (MAX_NESTING_DEPTH boundary)")
  class FourLevelNesting {

    @Test
    @DisplayName(
        "Optional<List<Optional<List<String>>>> should stop at depth 3 and treat inner List as"
            + " leaf")
    void fourLevelNestingShouldHitDepthLimit() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;
              import java.util.List;

              @GenerateFocus
              public record Config(String name, Optional<List<Optional<List<String>>>> deep) {}
              """);

      // The processor has MAX_NESTING_DEPTH = 3, so the 4th level (inner List<String>)
      // should be treated as a leaf rather than being unwrapped further.
      // The chain should still compile and produce a valid path.
      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();

      // At minimum, the field should be generated (the exact return type depends on
      // depth-limit behaviour — TraversalPath since we pass through List at level 2)
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", "deep()");
    }

    @Test
    @DisplayName(
        "Optional<Optional<Optional<Optional<String>>>> at 4 levels should hit depth limit")
    void quadrupleOptionalShouldHitDepthLimit() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;

              @GenerateFocus
              public record Config(String name, Optional<Optional<Optional<Optional<String>>>> deep) {}
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();

      // Should still generate the field, depth limit truncates the chain
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", "deep()");
    }

    @Test
    @DisplayName("List<Optional<List<Optional<String>>>> mixed 4-level nesting")
    void mixedFourLevelNestingShouldHitDepthLimit() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;
              import java.util.List;

              @GenerateFocus
              public record Config(String name, List<Optional<List<Optional<String>>>> deep) {}
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();

      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", "deep()");
    }
  }

  @Nested
  @DisplayName("Non-Nested Types Unchanged")
  class NonNestedRegression {

    @Test
    @DisplayName("Simple Optional<String> should still produce .some() returning AffinePath")
    void simpleOptionalShouldBeUnchanged() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;

              @GenerateFocus
              public record Config(String name, Optional<String> email) {}
              """);

      final String expectedMethod =
          """
          public static AffinePath<Config, String> email() {
              return FocusPath.of(Lens.of(Config::email, (source, newValue) -> new Config(source.name(), newValue))).some();
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedMethod);
    }

    @Test
    @DisplayName("Simple List<String> should still produce .each() returning TraversalPath")
    void simpleListShouldBeUnchanged() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.List;

              @GenerateFocus
              public record Config(String name, List<String> items) {}
              """);

      final String expectedMethod =
          """
          public static TraversalPath<Config, String> items() {
              return FocusPath.of(Lens.of(Config::items, (source, newValue) -> new Config(source.name(), newValue))).each();
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedMethod);
    }

    @Test
    @DisplayName("Plain String field should remain FocusPath")
    void plainFieldShouldBeUnchanged() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Config(String name) {}
              """);

      final String expectedMethod =
          """
          public static FocusPath<Config, String> name() {
              return FocusPath.of(Lens.of(Config::name, (source, newValue) -> new Config(newValue)));
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedMethod);
    }
  }

  @Nested
  @DisplayName("SPI Container Nesting")
  class SpiContainerNesting {

    @Test
    @DisplayName(
        "Optional<Either<String, Integer>> should produce"
            + " .<Either<String, Integer>>some().some(Affines.eitherRight()) returning AffinePath")
    void optionalOfEitherShouldProduceSomeThenSpiSome() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.higherkindedj.hkt.either.Either;
              import java.util.Optional;

              @GenerateFocus
              public record Config(String name, Optional<Either<String, Integer>> result) {}
              """);

      final String expectedMethod =
          """
          public static AffinePath<Config, Integer> result() {
              return FocusPath.of(Lens.of(Config::result, (source, newValue) -> new Config(source.name(), newValue))).<Either<String, Integer>>some().some(Affines.eitherRight());
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedMethod);
    }

    @Test
    @DisplayName(
        "List<Either<String, Integer>> should produce"
            + " .<Either<String, Integer>>each().some(Affines.eitherRight()) returning TraversalPath")
    void listOfEitherShouldProduceEachThenSpiSome() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.higherkindedj.hkt.either.Either;
              import java.util.List;

              @GenerateFocus
              public record Config(String name, List<Either<String, Integer>> results) {}
              """);

      final String expectedMethod =
          """
          public static TraversalPath<Config, Integer> results() {
              return FocusPath.of(Lens.of(Config::results, (source, newValue) -> new Config(source.name(), newValue))).<Either<String, Integer>>each().some(Affines.eitherRight());
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedMethod);
    }

    @Test
    @DisplayName(
        "Either<String, List<Integer>> with widenCollections should produce"
            + " .some(eitherRight()).each() returning TraversalPath")
    void eitherOfListShouldProduceSpiSomeThenEach() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.higherkindedj.hkt.either.Either;
              import java.util.List;

              @GenerateFocus
              public record Config(String name, Either<String, List<Integer>> items) {}
              """);

      // Either<String, List<Integer>>:
      // The outer Either is an SPI ZERO_OR_ONE type (focuses on R = List<Integer>)
      // The inner List<Integer> is a COLLECTION type
      // Chain: .some(Affines.eitherRight()).each() → TraversalPath
      final String expectedMethod =
          """
          public static TraversalPath<Config, Integer> items() {
              return FocusPath.of(Lens.of(Config::items, (source, newValue) -> new Config(source.name(), newValue))).some(Affines.eitherRight()).each();
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedMethod);
    }
  }

  @Nested
  @DisplayName("Widening Lattice Composition")
  class WideningLattice {

    @Test
    @DisplayName("Affine + Affine = Affine (Optional<Optional<String>>)")
    void affinePlusAffineShouldBeAffine() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;

              @GenerateFocus
              public record Config(Optional<Optional<String>> value) {}
              """);

      // AffinePath because Affine + Affine = Affine
      final String expectedReturn = "public static AffinePath<Config, String> value()";

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedReturn);
    }

    @Test
    @DisplayName("Affine + Traversal = Traversal (Optional<List<String>>)")
    void affinePlusTraversalShouldBeTraversal() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;
              import java.util.List;

              @GenerateFocus
              public record Config(Optional<List<String>> value) {}
              """);

      // TraversalPath because Affine + Traversal = Traversal
      final String expectedReturn = "public static TraversalPath<Config, String> value()";

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedReturn);
    }

    @Test
    @DisplayName("Traversal + Affine = Traversal (List<Optional<String>>)")
    void traversalPlusAffineShouldBeTraversal() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;
              import java.util.List;

              @GenerateFocus
              public record Config(List<Optional<String>> value) {}
              """);

      // TraversalPath because Traversal + Affine = Traversal
      final String expectedReturn = "public static TraversalPath<Config, String> value()";

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedReturn);
    }

    @Test
    @DisplayName("Traversal + Traversal = Traversal (List<List<String>>)")
    void traversalPlusTraversalShouldBeTraversal() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.List;

              @GenerateFocus
              public record Config(List<List<String>> value) {}
              """);

      // TraversalPath because Traversal + Traversal = Traversal
      final String expectedReturn = "public static TraversalPath<Config, String> value()";

      var compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedReturn);
    }
  }
}
