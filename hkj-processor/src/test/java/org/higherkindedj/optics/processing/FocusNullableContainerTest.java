// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper.CompiledResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A {@code @Nullable} container widens through {@code .nullable()} before its own step, so a null
 * container has no focus where the container's own step would throw on it.
 *
 * <p>The path type is the one the container gives on its own: {@code .nullable()} is an {@code
 * AffinePath}, and every container widens at least that far.
 */
@DisplayName("Nullable container widening")
class FocusNullableContainerTest {

  /** One component of every container shape, each declared nullable, in this order. */
  private static final List<String> COMPONENTS =
      List.of(
          "list",
          "set",
          "collection",
          "optional",
          "maybe",
          "map",
          "either",
          "array",
          "kind",
          "nested");

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
          import org.higherkindedj.hkt.Kind;
          import org.higherkindedj.hkt.either.Either;
          import org.higherkindedj.hkt.list.ListKind;
          import org.higherkindedj.hkt.maybe.Maybe;
          import org.higherkindedj.optics.annotations.GenerateFocus;
          import org.jspecify.annotations.Nullable;

          @GenerateFocus(widenCollections = true)
          public record Holder(
              @Nullable List<String> list,
              @Nullable Set<String> set,
              @Nullable Collection<String> collection,
              @Nullable Optional<String> optional,
              @Nullable Maybe<String> maybe,
              @Nullable Map<String, String> map,
              @Nullable Either<String, String> either,
              String @Nullable [] array,
              @Nullable Kind<ListKind.Witness, String> kind,
              @Nullable List<Optional<String>> nested,
              @Nullable String[] elements) {}
          """);

  private static final JavaFileObject LEAF =
      JavaFileObjects.forSourceString(
          "com.example.Leaf",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus
          public record Leaf(String name) {}
          """);

  private static final JavaFileObject CRATE =
      JavaFileObjects.forSourceString(
          "com.example.Crate",
          """
          package com.example;

          import java.util.Map;
          import org.higherkindedj.optics.annotations.GenerateFocus;
          import org.jspecify.annotations.Nullable;

          @GenerateFocus(widenCollections = true)
          public record Crate(
              @Nullable Map<String, Leaf> byName,
              Leaf @Nullable [] row,
              Map<String, @Nullable Leaf> looseByName,
              @Nullable Leaf[] looseRow) {}
          """);

  /** Containers whose own elements are nullable containers, each reached under widenCollections. */
  private static final JavaFileObject ROWS =
      JavaFileObjects.forSourceString(
          "com.example.Rows",
          """
          package com.example;

          import java.util.List;
          import java.util.Map;
          import java.util.Set;
          import org.higherkindedj.optics.annotations.GenerateFocus;
          import org.jspecify.annotations.Nullable;

          @GenerateFocus(widenCollections = true)
          public record Rows(
              List<@Nullable List<String>> lists,
              String[] @Nullable [] grid,
              Map<String, @Nullable Set<String>> groups) {}
          """);

  private static final Compilation COMPILATION =
      javac()
          .withProcessors(new FocusProcessor())
          .withOptions("-Xlint:all,-processing", "-Werror")
          .compile(HOLDER, LEAF, CRATE, ROWS);

  private static final CompiledResult RESULT = new CompiledResult(COMPILATION);

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
    @CsvSource(
        delimiterString = " | ",
        value = {
          "list | TraversalPath<Holder, String> list() | \"list\").nullable().each(EachInstances.listEach());",
          "set | TraversalPath<Holder, String> set()"
              + " | \"set\").nullable().each(EachInstances.setEach());",
          "collection | TraversalPath<Holder, String> collection()"
              + " | \"collection\").nullable().each(EachInstances.collectionEach());",
          "optional | AffinePath<Holder, String> optional() | \"optional\").nullable().some(FocusPaths.optionalSome());",
          "maybe | AffinePath<Holder, String> maybe() | \"maybe\").nullable().some(Affines.just());",
          "map | TraversalPath<Holder, String> map()"
              + " | \"map\").nullable().each(EachInstances.mapValuesEach());",
          "either | AffinePath<Holder, String> either()"
              + " | \"either\").nullable().some(Affines.eitherRight());",
          "array | TraversalPath<Holder, String> array()"
              + " | \"array\").nullable().each(EachInstances.arrayEach());",
          "kind | TraversalPath<Holder, String> kind()"
              + " | \"kind\").nullable().each(EachInstances.<org.higherkindedj.hkt.list.ListKind.Witness,",
          "nested | TraversalPath<Holder, String> nested() | \"nested\").nullable().each(EachInstances.listEach()).some(FocusPaths.optionalSome());",
          "elements | TraversalPath<Holder, @Nullable String> elements()"
              + " | \"elements\").each(EachInstances.arrayEach());"
        })
    @DisplayName("rules the null out before the container's own step, and keeps its path type")
    void rulesTheNullOutFirst(String component, String signature, String expression) {
      // `@Nullable String[]` annotates the elements, not the array, so nothing rules it out.
      assertGeneratedCodeContains(COMPILATION, "com.example.HolderFocus", signature);
      assertGeneratedCodeContains(COMPILATION, "com.example.HolderFocus", expression);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiterString = " | ",
        value = {
          "lists | \"lists\").each(EachInstances.listEach()).some(Affines.nullable()).each(EachInstances.listEach());",
          "grid | \"grid\").each(EachInstances.arrayEach()).some(Affines.nullable())"
              + ".each(EachInstances.arrayEach());",
          "groups | \"groups\").each(EachInstances.mapValuesEach()).some(Affines.nullable())"
              + ".each(EachInstances.setEach());"
        })
    @DisplayName("rules a nullable inner container out before stepping into it")
    void rulesANullableInnerContainerOut(String component, String expression) {
      assertGeneratedCodeContains(
          COMPILATION, "com.example.RowsFocus", "TraversalPath<Rows, String> " + component + "()");
      assertGeneratedCodeContains(COMPILATION, "com.example.RowsFocus", expression);
    }

    @Test
    @DisplayName("a navigator keeps its field's path, and passes over a null element it would read")
    void aNavigatorPassesOverANullElement() {
      // The navigator is the path the field's method has without one, null elements included...
      assertGeneratedCodeContains(
          COMPILATION,
          "com.example.CrateFocus",
          "\"looseByName\").each(EachInstances.mapValuesEach()));");
      assertGeneratedCodeContains(
          COMPILATION, "com.example.CrateFocus", "\"looseRow\").each(EachInstances.arrayEach()));");
      assertGeneratedCodeContains(
          COMPILATION,
          "com.example.CrateFocus",
          "class LooseByNameNavigator<S> extends NavigatorTraversalPath<S, @Nullable Leaf>");
      // ...and each navigation method rules the null out before it reads a component
      assertGeneratedCodeContains(
          COMPILATION,
          "com.example.CrateFocus",
          "return toPath().some(Affines.nullable()).via(LeafFocus.name());");
    }

    @Test
    @DisplayName("a navigator into a nullable container composes the same widening")
    void aNavigatorIntoANullableContainerComposesTheSameWidening() {
      assertGeneratedCodeContains(
          COMPILATION,
          "com.example.CrateFocus",
          "\"byName\").nullable().each(EachInstances.mapValuesEach()))");
      assertGeneratedCodeContains(
          COMPILATION,
          "com.example.CrateFocus",
          "\"row\").nullable().each(EachInstances.arrayEach()))");
    }
  }

  @Nested
  @DisplayName("Reading and modifying through the generated path")
  class ReadingAndModifying {

    private Class<?> type(String name) {
      try {
        return RESULT.loadClass(name);
      } catch (ClassNotFoundException e) {
        throw new AssertionError("could not load " + name, e);
      }
    }

    private Object newRecord(String name, Object... components) {
      try {
        Constructor<?> constructor = type(name).getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(components);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError("could not build " + name, e);
      }
    }

    /** A Holder whose every container is null. */
    private Object nulls() {
      return newRecord(
          "com.example.Holder",
          new Object[type("com.example.Holder").getRecordComponents().length]);
    }

    /** A Holder whose every container holds the one element "alice". */
    private Object present() {
      return newRecord(
          "com.example.Holder",
          List.of("alice"),
          Set.of("alice"),
          List.of("alice"),
          Optional.of("alice"),
          Maybe.just("alice"),
          Map.of("key", "alice"),
          Either.right("alice"),
          new String[] {"alice"},
          ListKindHelper.LIST.widen(List.of("alice")),
          List.of(Optional.of("alice")),
          new String[] {"alice"});
    }

    private Object path(String focusClass, String component) {
      try {
        return RESULT.invokeStatic("com.example." + focusClass, component);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError("could not read " + focusClass + "." + component, e);
      }
    }

    @SuppressWarnings("unchecked") // the generated method's type arguments erase to these
    private List<String> read(String component, Object source) {
      return switch (path("HolderFocus", component)) {
        case AffinePath<?, ?> affine ->
            ((AffinePath<Object, String>) affine).getOptional(source).stream().toList();
        case TraversalPath<?, ?> traversal ->
            ((TraversalPath<Object, String>) traversal).getAll(source);
        default -> throw new AssertionError(component + " widened to neither");
      };
    }

    @SuppressWarnings("unchecked") // the generated method's type arguments erase to these
    private Object modify(String component, Object source) {
      return switch (path("HolderFocus", component)) {
        case AffinePath<?, ?> affine ->
            ((AffinePath<Object, String>) affine).modify(FocusNullableContainerTest::upper, source);
        case TraversalPath<?, ?> traversal ->
            ((TraversalPath<Object, String>) traversal)
                .modifyAll(FocusNullableContainerTest::upper, source);
        default -> throw new AssertionError(component + " widened to neither");
      };
    }

    @ParameterizedTest(name = "{0}")
    @FieldSource("org.higherkindedj.optics.processing.FocusNullableContainerTest#COMPONENTS")
    @DisplayName("a null container has no focus, and a modification leaves it null")
    void aNullContainerHasNoFocus(String component) {
      Object source = nulls();

      assertThat(read(component, source)).isEmpty();
      assertThat(invoke(modify(component, source), component)).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @FieldSource("org.higherkindedj.optics.processing.FocusNullableContainerTest#COMPONENTS")
    @DisplayName("a present container is read and modified through its own step")
    void aPresentContainerIsReadAndModified(String component) {
      Object source = present();

      Object modified = modify(component, source);

      assertThat(read(component, source)).containsExactly("alice");
      assertThat(read(component, modified)).containsExactly("ALICE");
    }

    @Test
    @DisplayName("the cases above name every nullable container the record declares")
    void theCasesNameEveryNullableContainer() {
      // The record's last component annotates its elements, so it has no null container to test.
      assertThat(type("com.example.Holder").getRecordComponents())
          .extracting(RecordComponent::getName)
          .containsExactlyElementsOf(
              Stream.concat(COMPONENTS.stream(), Stream.of("elements")).toList());
    }

    @SuppressWarnings("unchecked") // the navigation method's type arguments erase to these
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"byName", "row"})
    @DisplayName("a navigator into a null container reaches no element")
    void aNavigatorIntoANullContainerReachesNoElement(String component) {
      Object empty =
          newRecord(
              "com.example.Crate",
              null,
              null,
              Map.of(),
              Array.newInstance(type("com.example.Leaf"), 0));
      TraversalPath<Object, String> names =
          (TraversalPath<Object, String>) invoke(path("CrateFocus", component), "name");

      assertThat(names.getAll(empty)).isEmpty();
      assertThat(invoke(names.modifyAll(FocusNullableContainerTest::upper, empty), component))
          .isNull();
    }

    /** A Rows whose every outer container holds a null inner one and one holding "a". */
    private Object rows() {
      Map<String, Set<String>> groups = new LinkedHashMap<>();
      groups.put("none", null);
      groups.put("some", Set.of("a"));
      return newRecord(
          "com.example.Rows",
          Arrays.asList(null, List.of("a")),
          new String[][] {null, {"a"}},
          groups);
    }

    @SuppressWarnings("unchecked") // the generated method's type arguments erase to these
    private TraversalPath<Object, String> rowsPath(String component) {
      return (TraversalPath<Object, String>) path("RowsFocus", component);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"lists", "grid", "groups"})
    @DisplayName("a null inner container is passed over, and the others are modified")
    void aNullInnerContainerIsPassedOver(String component) {
      Object source = rows();

      Object modified = rowsPath(component).modifyAll(FocusNullableContainerTest::upper, source);

      assertThat(rowsPath(component).getAll(source)).containsExactly("a");
      assertThat(rowsPath(component).getAll(modified)).containsExactly("A");
    }

    @Test
    @DisplayName("a null inner container stays where it was")
    void aNullInnerContainerStaysWhereItWas() {
      Object source = rows();

      Object lists = rowsPath("lists").modifyAll(FocusNullableContainerTest::upper, source);
      Object grid = rowsPath("grid").modifyAll(FocusNullableContainerTest::upper, source);

      assertThat(invoke(lists, "lists")).isEqualTo(Arrays.asList(null, List.of("A")));
      assertThat((String[][]) invoke(grid, "grid")).isDeepEqualTo(new String[][] {null, {"A"}});
    }

    @SuppressWarnings("unchecked") // the navigation method's type arguments erase to these
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"looseByName", "looseRow"})
    @DisplayName("a navigator passes over a null element and reads the others")
    void aNavigatorPassesOverANullElement(String component) {
      Object oak = newRecord("com.example.Leaf", "oak");
      Object row = Array.newInstance(type("com.example.Leaf"), 2);
      Array.set(row, 1, oak);
      Map<String, Object> byName = new LinkedHashMap<>();
      byName.put("none", null);
      byName.put("oak", oak);
      Object crate = newRecord("com.example.Crate", null, null, byName, row);
      TraversalPath<Object, String> names =
          (TraversalPath<Object, String>) invoke(path("CrateFocus", component), "name");

      Object modified = names.modifyAll(FocusNullableContainerTest::upper, crate);

      assertThat(names.getAll(crate)).containsExactly("oak");
      assertThat(names.getAll(modified)).containsExactly("OAK");
      // The navigator itself is the field's path, so it still reaches the null element
      TraversalPath<Object, Object> elements =
          (TraversalPath<Object, Object>) path("CrateFocus", component);
      assertThat(elements.getAll(crate)).containsExactlyInAnyOrder(null, oak);
    }
  }
}
