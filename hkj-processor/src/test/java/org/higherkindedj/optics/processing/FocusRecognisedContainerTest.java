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
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper.CompiledResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Each collection {@code @GenerateFocus} recognises by name reaches a traversal that rebuilds it.
 *
 * <p>The three do not share one: the no-argument {@code .each()} carries a {@code List} traversal
 * and casts the focused value to a {@code List} unchecked, so a {@code Set} or a {@code Collection}
 * routed through it compiled and then threw on its first traversal (issue #725). A compile-testing
 * assertion cannot see that, which is why the modifications below run the generated path and read
 * the component back rather than only reading the emitted source.
 */
@DisplayName("Recognised container widening")
class FocusRecognisedContainerTest {

  /** One record carrying a component of every recognised collection shape. */
  private static final JavaFileObject HOLDER =
      JavaFileObjects.forSourceString(
          "com.example.Holder",
          """
          package com.example;

          import java.util.Collection;
          import java.util.List;
          import java.util.Optional;
          import java.util.Set;
          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus
          public record Holder(
              List<String> list,
              Set<String> set,
              Collection<String> collection,
              Set<Optional<String>> nested) {}
          """);

  private static String upper(String value) {
    return value.toUpperCase(Locale.ROOT);
  }

  private static Compilation compileHolder() {
    Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(HOLDER);
    assertThat(compilation).succeeded();
    return compilation;
  }

  @Nested
  @DisplayName("Emitted expression")
  class EmittedExpression {

    @Test
    @DisplayName("List widens through the no-argument each()")
    void listWidensThroughTheNoArgumentEach() {
      assertGeneratedCodeContains(compileHolder(), "com.example.HolderFocus", "\"list\").each();");
    }

    @Test
    @DisplayName("Set widens through the Each that rebuilds a set")
    void setWidensThroughTheEachThatRebuildsASet() {
      assertGeneratedCodeContains(
          compileHolder(), "com.example.HolderFocus", "\"set\").each(EachInstances.setEach());");
    }

    @Test
    @DisplayName("Collection widens through the Each that rebuilds a collection")
    void collectionWidensThroughTheEachThatRebuildsACollection() {
      assertGeneratedCodeContains(
          compileHolder(),
          "com.example.HolderFocus",
          "\"collection\").each(EachInstances.collectionEach());");
    }

    @Test
    @DisplayName("a nested container composes onto the Set's own Each")
    void aNestedContainerComposesOntoTheSetsOwnEach() {
      assertGeneratedCodeContains(
          compileHolder(),
          "com.example.HolderFocus",
          "\"nested\").each(EachInstances.setEach()).some();");
    }

    @Test
    @DisplayName("a no-argument step before a Set spells out the element it hands on")
    void aNoArgumentStepBeforeASetSpellsOutTheElementItHandsOn() {
      JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Nested",
              """
              package com.example;

              import java.util.List;
              import java.util.Set;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Nested(List<Set<String>> rows) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.NestedFocus",
          "\"rows\").<Set<String>>each().each(EachInstances.setEach());");
    }
  }

  @Nested
  @DisplayName("Modifying through the generated path")
  class ModifyingThroughTheGeneratedPath {

    private final CompiledResult result =
        RuntimeCompilationHelper.compileWith(new FocusProcessor(), HOLDER);

    /** A Holder built from the four containers, in declaration order. */
    private Object holder(
        Collection<String> list,
        Collection<String> set,
        Collection<String> collection,
        Collection<Optional<String>> nested) {
      try {
        Constructor<?> constructor =
            result.loadClass("com.example.Holder").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(list, set, collection, nested);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError("could not build com.example.Holder", e);
      }
    }

    /** The Holder every component of which holds the same two lower-case names. */
    private Object holder() {
      return holder(
          List.of("alice", "bob"),
          new HashSet<>(List.of("alice", "bob")),
          new HashSet<>(List.of("alice", "bob")),
          new HashSet<>(List.of(Optional.of("alice"), Optional.<String>empty())));
    }

    @SuppressWarnings("unchecked") // the generated method's type arguments erase to these
    private TraversalPath<Object, String> path(String component) {
      try {
        return (TraversalPath<Object, String>)
            result.invokeStatic("com.example.HolderFocus", component);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError("could not read com.example.HolderFocus." + component, e);
      }
    }

    @Test
    @DisplayName("a List component keeps its order and its duplicates")
    void aListComponentKeepsItsOrderAndItsDuplicates() {
      Object modified = path("list").modifyAll(FocusRecognisedContainerTest::upper, holder());

      assertThat(invoke(modified, "list")).isEqualTo(List.of("ALICE", "BOB"));
    }

    @Test
    @DisplayName("a Set component comes back a Set")
    void aSetComponentComesBackASet() {
      Object modified = path("set").modifyAll(FocusRecognisedContainerTest::upper, holder());

      assertThat(invoke(modified, "set")).isEqualTo(Set.of("ALICE", "BOB"));
    }

    @Test
    @DisplayName("a Collection component holding a set comes back a set")
    void aCollectionComponentHoldingASetComesBackASet() {
      Object modified = path("collection").modifyAll(FocusRecognisedContainerTest::upper, holder());

      assertThat(invoke(modified, "collection"))
          .isInstanceOf(Set.class)
          .isEqualTo(Set.of("ALICE", "BOB"));
    }

    @Test
    @DisplayName("a Collection component holding a list keeps its duplicates")
    void aCollectionComponentHoldingAListKeepsItsDuplicates() {
      Object source =
          holder(
              List.of("alice"),
              Set.of("alice"),
              new ArrayList<>(List.of("alice", "alice")),
              Set.of(Optional.of("alice")));

      Object modified = path("collection").modifyAll(FocusRecognisedContainerTest::upper, source);

      assertThat(invoke(modified, "collection")).isEqualTo(List.of("ALICE", "ALICE"));
    }

    @Test
    @DisplayName("a container nested in a Set is reached through it")
    void aContainerNestedInASetIsReachedThroughIt() {
      Object modified = path("nested").modifyAll(FocusRecognisedContainerTest::upper, holder());

      assertThat(invoke(modified, "nested"))
          .isEqualTo(Set.of(Optional.of("ALICE"), Optional.<String>empty()));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"list", "set", "collection", "nested"})
    @DisplayName("reading every element back finds the ones that were put in")
    void readingEveryElementBackFindsTheOnesThatWerePutIn(String component) {
      assertThat(path(component).getAll(holder()))
          .containsExactlyInAnyOrderElementsOf(
              component.equals("nested") ? List.of("alice") : List.of("alice", "bob"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"list", "set", "collection", "nested"})
    @DisplayName("the identity modification returns an equal record")
    void theIdentityModificationReturnsAnEqualRecord(String component) {
      Object source = holder();

      assertThat(path(component).modifyAll(Function.identity(), source)).isEqualTo(source);
    }
  }

  @Nested
  @DisplayName("A container whose Each cannot be instantiated")
  class AContainerWhoseEachCannotBeInstantiated {

    private Compilation compile(String component) {
      return javac()
          .withProcessors(new FocusProcessor())
          .compile(
              JavaFileObjects.forSourceString(
                  "com.example.Holder",
                  """
                  package com.example;

                  import java.util.Collection;
                  import java.util.Set;
                  import org.higherkindedj.optics.annotations.GenerateFocus;

                  @GenerateFocus
                  @SuppressWarnings("rawtypes")
                  public record Holder(%s f) {}
                  """
                      .formatted(component)));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Set<?>", "Set<? extends CharSequence>", "Collection<?>"})
    @DisplayName("a wildcard is reported against the declaration")
    void aWildcardIsReportedAgainstTheDeclaration(String component) {
      assertThat(compile(component))
          .hadErrorContaining(
              "record component 'Holder.f' has a wildcard type argument in " + component + ".");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Set", "Collection"})
    @DisplayName("a raw container is reported against the declaration")
    void aRawContainerIsReportedAgainstTheDeclaration(String component) {
      assertThat(compile(component))
          .hadErrorContaining("record component 'Holder.f' has a raw " + component + ".");
    }
  }
}
