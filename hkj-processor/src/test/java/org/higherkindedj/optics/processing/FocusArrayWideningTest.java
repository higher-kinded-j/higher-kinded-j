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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper.CompiledResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * An array component widens the way any other {@code ZERO_OR_MORE} SPI container does: under {@code
 * widenCollections}, or where {@code widenFields} names it. A navigator over its elements follows
 * the widening, and never forces it.
 *
 * <p>The reads and modifications run the generated path, because an array rebuilt into the wrong
 * component type compiles and then throws on first use.
 */
@DisplayName("Array widening")
class FocusArrayWideningTest {

  private static final JavaFileObject LEAF =
      JavaFileObjects.forSourceString(
          "com.example.Leaf",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus
          public record Leaf(String name) {}
          """);

  private static final JavaFileObject BOX =
      JavaFileObjects.forSourceString(
          "com.example.Box",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus
          public record Box<T>(T value) {}
          """);

  /** One record carrying an array in every position a widening can meet one. */
  private static final JavaFileObject HOLDER =
      JavaFileObjects.forSourceString(
          "com.example.Holder",
          """
          package com.example;

          import java.util.List;
          import java.util.Optional;
          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus(widenCollections = true)
          public record Holder(
              String[] tags,
              Box<String>[] boxes,
              String[][] grid,
              List<String[]> rows,
              Optional<String[]> extra,
              List<? extends Leaf>[] groups,
              int[] counts) {}
          """);

  private static final JavaFileObject BAG =
      JavaFileObjects.forSourceString(
          "com.example.Bag",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus(widenCollections = true)
          public record Bag<T>(T[] items) {}
          """);

  private static final JavaFileObject PLAIN =
      JavaFileObjects.forSourceString(
          "com.example.Plain",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus
          public record Plain(String[] tags) {}
          """);

  private static final JavaFileObject SHELF =
      JavaFileObjects.forSourceString(
          "com.example.Shelf",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus(widenFields = "leaves")
          public record Shelf(Leaf[] leaves) {}
          """);

  private static final Compilation COMPILATION =
      javac()
          .withProcessors(new FocusProcessor())
          .withOptions("-Xlint:all,-processing", "-Werror")
          .compile(LEAF, BOX, HOLDER, BAG, PLAIN, SHELF);

  private static final CompiledResult RESULT = new CompiledResult(COMPILATION);

  private static String upper(String value) {
    return value.toUpperCase(Locale.ROOT);
  }

  private static Class<?> type(String name) {
    try {
      return RESULT.loadClass(name);
    } catch (ClassNotFoundException e) {
      throw new AssertionError("could not load " + name, e);
    }
  }

  private static Object newRecord(String name, Object... components) {
    try {
      Constructor<?> constructor = type(name).getDeclaredConstructors()[0];
      constructor.setAccessible(true);
      return constructor.newInstance(components);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("could not build " + name, e);
    }
  }

  @SuppressWarnings("unchecked") // the generated method's type arguments erase to these
  private static <A> TraversalPath<Object, A> path(String focusClass, String component) {
    try {
      return (TraversalPath<Object, A>) RESULT.invokeStatic("com.example." + focusClass, component);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("could not read " + focusClass + "." + component, e);
    }
  }

  @Nested
  @DisplayName("Generated signature")
  class GeneratedSignature {

    @Test
    @DisplayName("the records compile without a warning")
    void theRecordsCompileWithoutAWarning() {
      assertThat(COMPILATION).succeededWithoutWarnings();
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(
        delimiterString = " | ",
        value = {
          "HolderFocus | TraversalPath<Holder, String> tags()",
          "HolderFocus | BoxesNavigator<Holder, String> boxes()",
          "HolderFocus | TraversalPath<Holder, String> grid()",
          "HolderFocus | TraversalPath<Holder, String> rows()",
          "HolderFocus | TraversalPath<Holder, String> extra()",
          "HolderFocus | GroupsNavigator<Holder> groups()",
          "HolderFocus | FocusPath<Holder, int[]> counts()",
          "BagFocus | <T> TraversalPath<Bag<T>, T> items()",
          "PlainFocus | FocusPath<Plain, String[]> tags()",
          "ShelfFocus | LeavesNavigator<Shelf> leaves()",
          "ShelfFocus | TraversalPath<S, String> name()"
        })
    @DisplayName("each array widens as its settings and its element say")
    void eachArrayWidensAsItsSettingsAndItsElementSay(String focusClass, String signature) {
      // A reference array is a ZERO_OR_MORE container, stepped into under widenCollections or
      // widenFields, and its navigable element then gets a navigator, which is the traversal. A
      // primitive array has no Each to widen through, and one in a record with the default
      // settings stays in focus, as a Map does.
      assertGeneratedCodeContains(COMPILATION, "com.example." + focusClass, signature);
    }

    @Test
    @DisplayName("an array widens through arrayEach, after a List with no witness")
    void anArrayWidensThroughArrayEach() {
      assertGeneratedCodeContains(
          COMPILATION, "com.example.HolderFocus", "\"tags\").each(EachInstances.arrayEach());");
      assertGeneratedCodeContains(
          COMPILATION,
          "com.example.HolderFocus",
          "\"grid\").each(EachInstances.arrayEach()).each(EachInstances.arrayEach());");
      assertGeneratedCodeContains(
          COMPILATION,
          "com.example.HolderFocus",
          "\"rows\").each(EachInstances.listEach()).each(EachInstances.arrayEach());");
    }
  }

  @Nested
  @DisplayName("Modifying through the generated path")
  class ModifyingThroughTheGeneratedPath {

    private Object holder() {
      Object boxes = Array.newInstance(type("com.example.Box"), 1);
      Array.set(boxes, 0, newRecord("com.example.Box", "boxed"));
      return newRecord(
          "com.example.Holder",
          new String[] {"alice", "bob"},
          boxes,
          new String[][] {{"p"}, {"q", "r"}},
          List.of(new String[] {"x"}, new String[] {"y", "z"}),
          Optional.of(new String[] {"extra"}),
          new List<?>[] {List.of(newRecord("com.example.Leaf", "leaf"))},
          new int[] {1});
    }

    @Test
    @DisplayName("an array's elements are read and rebuilt in order, and the source is unchanged")
    void anArraysElementsAreReadAndRebuiltInOrder() {
      Object source = holder();
      TraversalPath<Object, String> tags = path("HolderFocus", "tags");

      Object modified = tags.modifyAll(FocusArrayWideningTest::upper, source);

      assertThat(tags.getAll(source)).containsExactly("alice", "bob");
      assertThat((String[]) invoke(modified, "tags")).containsExactly("ALICE", "BOB");
      assertThat((String[]) invoke(source, "tags")).containsExactly("alice", "bob");
    }

    @Test
    @DisplayName("an array of a generic type is rebuilt with the component type it was built with")
    void anArrayOfAGenericTypeKeepsItsComponentType() {
      Object modified =
          path("HolderFocus", "boxes")
              .modifyAll(
                  box -> newRecord("com.example.Box", upper((String) invoke(box, "value"))),
                  holder());

      Object boxes = invoke(modified, "boxes");
      assertThat(boxes.getClass().getComponentType()).isEqualTo(type("com.example.Box"));
      assertThat(invoke(Array.get(boxes, 0), "value")).isEqualTo("BOXED");
    }

    @Test
    @DisplayName("an array nested in an array, a List or an Optional is reached through it")
    void anArrayNestedInAContainerIsReachedThroughIt() {
      Object source = holder();

      Object grid =
          FocusArrayWideningTest.<String>path("HolderFocus", "grid")
              .modifyAll(FocusArrayWideningTest::upper, source);
      Object rows =
          FocusArrayWideningTest.<String>path("HolderFocus", "rows")
              .modifyAll(FocusArrayWideningTest::upper, source);
      Object extra =
          FocusArrayWideningTest.<String>path("HolderFocus", "extra")
              .modifyAll(FocusArrayWideningTest::upper, source);

      assertThat((String[][]) invoke(grid, "grid"))
          .isDeepEqualTo(new String[][] {{"P"}, {"Q", "R"}});
      assertThat((List<?>) invoke(rows, "rows"))
          .satisfiesExactly(
              row -> assertThat((String[]) row).containsExactly("X"),
              row -> assertThat((String[]) row).containsExactly("Y", "Z"));
      assertThat((Optional<?>) invoke(extra, "extra"))
          .hasValueSatisfying(value -> assertThat((String[]) value).containsExactly("EXTRA"));
    }

    @Test
    @DisplayName("a generic record's array is rebuilt for its own type argument")
    void aGenericRecordsArrayIsRebuilt() {
      Object bag = newRecord("com.example.Bag", (Object) new String[] {"one", "two"});

      Object modified =
          FocusArrayWideningTest.<String>path("BagFocus", "items")
              .modifyAll(FocusArrayWideningTest::upper, bag);

      assertThat((String[]) invoke(modified, "items")).containsExactly("ONE", "TWO");
    }

    @Test
    @DisplayName("a navigator steps into an array of navigable records")
    void aNavigatorStepsIntoAnArrayOfNavigableRecords() throws ReflectiveOperationException {
      Class<?> leafClass = type("com.example.Leaf");
      Object leaves = Array.newInstance(leafClass, 2);
      Array.set(leaves, 0, newRecord("com.example.Leaf", "oak"));
      Array.set(leaves, 1, newRecord("com.example.Leaf", "elm"));
      Object shelf = newRecord("com.example.Shelf", leaves);
      Object navigator = RESULT.invokeStatic("com.example.ShelfFocus", "leaves");
      @SuppressWarnings("unchecked") // the navigation method's type arguments erase to these
      TraversalPath<Object, String> names =
          (TraversalPath<Object, String>) invoke(navigator, "name");

      Object modified = names.modifyAll(FocusArrayWideningTest::upper, shelf);

      assertThat(names.getAll(shelf)).containsExactly("oak", "elm");
      assertThat(names.getAll(modified)).containsExactly("OAK", "ELM");
      assertThat(invoke(modified, "leaves").getClass().getComponentType()).isEqualTo(leafClass);
    }
  }
}
