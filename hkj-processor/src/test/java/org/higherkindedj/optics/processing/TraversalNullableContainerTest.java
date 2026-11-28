// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classDirectory;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classpathWith;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.tools.JavaFileObject;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper.CompiledResult;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A traversal over a {@code @Nullable} container finds no elements in a null one, as the Focus path
 * does, where the generator's own body would throw on it.
 *
 * <p>{@code @GenerateTraversals} and {@code @ImportOptics} write a null check before the body for a
 * component declared nullable ({@link TraversalBodies}). A component whose elements alone are
 * declared nullable, {@code List<@Nullable String>} or {@code @Nullable String[]}, is itself
 * non-null, and is traversed as before.
 */
@DisplayName("A traversal over a nullable container")
class TraversalNullableContainerTest {

  /** Holder's components declared nullable, one of each container a built-in generator claims. */
  private static final List<String> NULLABLE =
      List.of(
          "list",
          "set",
          "collection",
          "optional",
          "maybe",
          "map",
          "either",
          "attempt",
          "checked",
          "array");

  /** Holder's components that are not declared nullable themselves. */
  private static final List<String> NON_NULL = List.of("elements", "loose", "plain");

  private static final JavaFileObject HOLDER =
      JavaFileObjects.forSourceString(
          "com.example.Holder",
          """
          package com.example;

          import java.util.Collection;
          import java.util.List;
          import java.util.Map;
          import java.util.Optional;
          import java.util.Set;
          import org.higherkindedj.hkt.either.Either;
          import org.higherkindedj.hkt.maybe.Maybe;
          import org.higherkindedj.hkt.trymonad.Try;
          import org.higherkindedj.hkt.validated.Validated;
          import org.higherkindedj.optics.annotations.GenerateTraversals;
          import org.jspecify.annotations.Nullable;

          @GenerateTraversals
          public record Holder(
              @Nullable List<String> list,
              @Nullable Set<String> set,
              @Nullable Collection<String> collection,
              @Nullable Optional<String> optional,
              @Nullable Maybe<String> maybe,
              @Nullable Map<String, String> map,
              @Nullable Either<String, String> either,
              @Nullable Try<String> attempt,
              @Nullable Validated<String, String> checked,
              String @Nullable [] array,
              @Nullable String[] elements,
              List<@Nullable String> loose,
              List<String> plain) {}
          """);

  private static final JavaFileObject BOX =
      JavaFileObjects.forSourceString(
          "com.example.Box",
          """
          package com.example;

          import java.util.List;
          import org.higherkindedj.optics.annotations.GenerateTraversals;
          import org.jspecify.annotations.Nullable;

          @GenerateTraversals
          public record Box<T>(@Nullable List<T> items) {}
          """);

  private static final Compilation COMPILATION =
      javac()
          .withProcessors(new TraversalProcessor())
          .withOptions("-Xlint:all,-processing", "-Werror")
          .compile(HOLDER, BOX);

  private static final CompiledResult RESULT = new CompiledResult(COMPILATION);

  /** The null check written before the body of {@code component}'s traversal. */
  private static String guard(String component) {
    return "Applicative<F> applicative) { if (source.%s() == null) { return applicative.of(source); }"
        .formatted(component);
  }

  private static String upper(String value) {
    return value.toUpperCase(Locale.ROOT);
  }

  @Nested
  @DisplayName("Generated method")
  class GeneratedMethod {

    @Test
    @DisplayName("the records compile without a warning")
    void theRecordsCompileWithoutAWarning() {
      assertThat(COMPILATION).succeededWithoutWarnings();
    }

    @ParameterizedTest(name = "{0}")
    @FieldSource("org.higherkindedj.optics.processing.TraversalNullableContainerTest#NULLABLE")
    @DisplayName("hands the record back before the generator's body when the container is null")
    void checksANullableContainerFirst(String component) {
      assertGeneratedCodeContains(COMPILATION, "com.example.HolderTraversals", guard(component));
    }

    @ParameterizedTest(name = "{0}")
    @FieldSource("org.higherkindedj.optics.processing.TraversalNullableContainerTest#NON_NULL")
    @DisplayName("writes no check for a container that is not declared nullable itself")
    void writesNoCheckForANonNullContainer(String component) {
      // `@Nullable String[]` and `List<@Nullable String>` annotate the elements, not the container.
      assertGeneratedCodeDoesNotContain(
          COMPILATION, "com.example.HolderTraversals", "if (source." + component + "() == null)");
    }

    @Test
    @DisplayName("hands a generic record back at its own type arguments")
    void handsAGenericRecordBack() {
      assertGeneratedCodeContains(
          COMPILATION,
          "com.example.BoxTraversals",
          "Kind<F, Box<T>> modifyF(Function<T, Kind<F, T>> f, Box<T> source, " + guard("items"));
    }
  }

  @Nested
  @DisplayName("Running the generated traversal")
  class Running {
    // NullableContainerTraversalTest, in hkj-processor-plugins, runs a null container of every
    // generator family against the Focus path. These run the shapes only this module holds.

    private Object construct(String name, Object... components) {
      try {
        Constructor<?> constructor = RESULT.loadClass(name).getDeclaredConstructors()[0];
        return constructor.newInstance(components);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError("could not build " + name, e);
      }
    }

    @SuppressWarnings("unchecked") // the generated method's type arguments erase to these
    private Traversal<Object, String> traversal(String traversals, String component) {
      try {
        return (Traversal<Object, String>) RESULT.invokeStatic(traversals, component);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError("could not read " + traversals + "." + component, e);
      }
    }

    @Test
    @DisplayName(
        "finds no element in a generic record's null container, and hands it back as it is")
    void findsNoElementInAGenericRecordsNullContainer() {
      Object box = construct("com.example.Box", (Object) null);
      Traversal<Object, String> items = traversal("com.example.BoxTraversals", "items");

      assertThat(Traversals.getAll(items, box)).isEmpty();
      assertThat(Traversals.modify(items, TraversalNullableContainerTest::upper, box))
          .isSameAs(box);
    }

    @ParameterizedTest(name = "{0}")
    @FieldSource("org.higherkindedj.optics.processing.TraversalNullableContainerTest#NON_NULL")
    @DisplayName("still reaches and rewrites a container whose elements alone are nullable")
    void stillTraversesANonNullContainer(String component) {
      Object holder =
          construct(
              "com.example.Holder",
              List.of("alice"),
              Set.of("alice"),
              List.of("alice"),
              Optional.of("alice"),
              Maybe.just("alice"),
              Map.of("key", "alice"),
              Either.right("alice"),
              Try.success("alice"),
              Validated.valid("alice"),
              new String[] {"alice"},
              new String[] {"alice"},
              List.of("alice"),
              List.of("alice"));
      Traversal<Object, String> traversal = traversal("com.example.HolderTraversals", component);

      Object modified = Traversals.modify(traversal, TraversalNullableContainerTest::upper, holder);

      assertThat(Traversals.getAll(traversal, modified)).containsExactly("ALICE");
      assertThat(Traversals.getAll(traversal, holder)).containsExactly("alice");
    }
  }

  @Nested
  @DisplayName("@ImportOptics")
  class ImportOptics {

    private static final JavaFileObject PACKAGE_INFO =
        JavaFileObjects.forSourceString(
            "com.myapp.optics.package-info",
            """
            @ImportOptics({com.external.Basket.class})
            package com.myapp.optics;

            import org.higherkindedj.optics.annotations.ImportOptics;
            """);

    /** The imported record, its tags declared nullable with {@code annotation}. */
    private static JavaFileObject basket(String annotation) {
      return JavaFileObjects.forSourceString(
          "com.external.Basket",
          """
          package com.external;

          import java.util.List;

          public record Basket(%s List<String> tags, List<String> plain) {}
          """
              .formatted(annotation));
    }

    private static final String JSPECIFY = "@org.jspecify.annotations.Nullable";

    private static final String JAVAX = "@javax.annotation.Nullable";

    private static void assertChecksTagsAlone(Compilation compilation) {
      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(compilation, "com.myapp.optics.BasketLenses", guard("tags"));
      assertGeneratedCodeDoesNotContain(
          compilation, "com.myapp.optics.BasketLenses", "if (source.plain() == null)");
    }

    @Test
    @DisplayName("finds no element in a null container of an imported record")
    void findsNoElementInANullContainer() throws ReflectiveOperationException {
      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:all,-processing", "-Werror")
              .compile(basket(JSPECIFY), PACKAGE_INFO);
      assertChecksTagsAlone(compilation);
      CompiledResult result = new CompiledResult(compilation);

      Object basket =
          result
              .loadClass("com.external.Basket")
              .getDeclaredConstructors()[0]
              .newInstance(null, List.of("plain"));
      @SuppressWarnings("unchecked") // the generated method's type arguments erase to these
      Traversal<Object, String> tags =
          (Traversal<Object, String>)
              result.invokeStatic("com.myapp.optics.BasketLenses", "tagsTraversal");

      assertThat(Traversals.getAll(tags, basket)).isEmpty();
      assertThat(Traversals.modify(tags, TraversalNullableContainerTest::upper, basket))
          .isSameAs(basket);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {JSPECIFY, JAVAX})
    @DisplayName("reads the nullable container of a record read from a jar")
    void readsANullableContainerFromAJar(String annotation, @TempDir Path tmp) throws IOException {
      Compilation library = javac().compile(basket(annotation), Jsr305NullableStub.SOURCE);
      assertThat(library).succeeded();

      assertChecksTagsAlone(
          javac()
              .withClasspath(classpathWith(classDirectory(library, tmp)))
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:all,-processing", "-Werror")
              .compile(PACKAGE_INFO));
    }
  }
}
