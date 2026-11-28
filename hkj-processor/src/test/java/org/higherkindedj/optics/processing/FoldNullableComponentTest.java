// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import java.util.Set;
import javax.tools.JavaFileObject;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper.CompiledResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A fold over a component declared {@code @Nullable} has nothing to fold when it is null, as the
 * Focus path reads it, where the generated loop would throw on a null container and the function
 * would be handed a null value.
 */
@DisplayName("A fold over a nullable component")
class FoldNullableComponentTest {

  private static final JavaFileObject SHELF =
      JavaFileObjects.forSourceString(
          "com.example.Shelf",
          """
          package com.example;

          import java.util.List;
          import java.util.Set;
          import org.higherkindedj.optics.annotations.GenerateFolds;
          import org.jspecify.annotations.Nullable;

          @GenerateFolds
          public record Shelf(
              @Nullable List<String> titles,
              @Nullable Set<String> tags,
              @Nullable String label,
              List<@Nullable String> notes,
              List<String> plain) {}
          """);

  private static final Compilation COMPILATION =
      javac()
          .withProcessors(new FoldProcessor())
          .withOptions("-Xlint:all,-processing", "-Werror")
          .compile(SHELF);

  private static final CompiledResult RESULT = new CompiledResult(COMPILATION);

  /** The check written before the fold's body for {@code component}. */
  private static String guard(String component) {
    return "if (source.%s() == null) { return monoid.empty(); }".formatted(component);
  }

  @Nested
  @DisplayName("Generated method")
  class GeneratedMethod {

    @Test
    @DisplayName("the record compiles without a warning")
    void theRecordCompilesWithoutAWarning() {
      assertThat(COMPILATION).succeededWithoutWarnings();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"titles", "tags", "label"})
    @DisplayName("gives the monoid's empty value before the body when the component is null")
    void checksANullableComponentFirst(String component) {
      assertGeneratedCodeContains(COMPILATION, "com.example.ShelfFolds", guard(component));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"notes", "plain"})
    @DisplayName("writes no check for a component that is not declared nullable itself")
    void writesNoCheckForANonNullComponent(String component) {
      // `List<@Nullable String>` annotates the elements, not the list.
      assertGeneratedCodeDoesNotContain(
          COMPILATION, "com.example.ShelfFolds", "if (source." + component + "() == null)");
    }
  }

  @Nested
  @DisplayName("Running the generated fold")
  class Running {

    private Object shelf(Object... components) throws ReflectiveOperationException {
      return RESULT
          .loadClass("com.example.Shelf")
          .getDeclaredConstructors()[0]
          .newInstance(components);
    }

    @SuppressWarnings("unchecked") // the generated method's type arguments erase to these
    private Fold<Object, String> fold(String component) throws ReflectiveOperationException {
      return (Fold<Object, String>) RESULT.invokeStatic("com.example.ShelfFolds", component);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"titles", "tags", "label"})
    @DisplayName("finds nothing in a null component")
    void findsNothingInANullComponent(String component) throws ReflectiveOperationException {
      Object shelf = shelf(null, null, null, List.of(), List.of());
      Fold<Object, String> fold = fold(component);

      assertThat(fold.getAll(shelf)).isEmpty();
      assertThat(fold.length(shelf)).isZero();
      assertThat(fold.foldMap(Monoids.string(), value -> value, shelf)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"titles", "tags", "label"})
    @DisplayName("still folds a component that is not null")
    void stillFoldsAPresentComponent(String component) throws ReflectiveOperationException {
      Object shelf = shelf(List.of("dune"), Set.of("dune"), "dune", List.of(), List.of());

      assertThat(fold(component).getAll(shelf)).containsExactly("dune");
    }
  }
}
