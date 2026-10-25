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
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper.CompiledResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Each container {@code @GenerateFocus} recognises by name reaches an optic that reads and rebuilds
 * it.
 *
 * <p>They do not share one: a {@code List} traversal or an {@code Optional} affine handed a {@code
 * Set}, a {@code Collection} or a {@code Maybe} through an unchecked cast would compile and then
 * throw on first use. A compile-testing assertion cannot see that, which is why the reads and
 * modifications below run the generated path and read the component back rather than only reading
 * the emitted source.
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
    @DisplayName("List widens through EachInstances.listEach()")
    void listWidensThroughListEach() {
      assertGeneratedCodeContains(
          compileHolder(), "com.example.HolderFocus", "\"list\").each(EachInstances.listEach());");
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
          "\"nested\").each(EachInstances.setEach()).some(FocusPaths.optionalSome());");
    }

    @Test
    @DisplayName("a List before a Set hands on its element with no witness")
    void aListBeforeASetNeedsNoWitness() {
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
          "\"rows\").each(EachInstances.listEach()).each(EachInstances.setEach());");
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
  @DisplayName("A Maybe component")
  class AMaybeComponent {

    private static final JavaFileObject SAVED =
        JavaFileObjects.forSourceString(
            "com.example.Saved",
            """
            package com.example;

            import java.util.List;
            import org.higherkindedj.hkt.maybe.Maybe;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus
            public record Saved(Maybe<String> item, List<Maybe<String>> drafts) {}
            """);

    private final CompiledResult result =
        RuntimeCompilationHelper.compileWith(new FocusProcessor(), SAVED);

    private Object saved(Maybe<String> item, List<Maybe<String>> drafts) {
      try {
        Constructor<?> constructor =
            result.loadClass("com.example.Saved").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(item, drafts);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError("could not build com.example.Saved", e);
      }
    }

    private Object path(String component) {
      try {
        return result.invokeStatic("com.example.SavedFocus", component);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError("could not read com.example.SavedFocus." + component, e);
      }
    }

    @SuppressWarnings("unchecked") // the generated method's type arguments erase to these
    private AffinePath<Object, String> item() {
      return (AffinePath<Object, String>) path("item");
    }

    @SuppressWarnings("unchecked") // the generated method's type arguments erase to these
    private TraversalPath<Object, String> drafts() {
      return (TraversalPath<Object, String>) path("drafts");
    }

    @Test
    @DisplayName("widens through the Affine that reads a Maybe")
    void widensThroughTheAffineThatReadsAMaybe() {
      assertGeneratedCodeContains(
          result.compilation(), "com.example.SavedFocus", "\"item\").some(Affines.just());");
    }

    @Test
    @DisplayName("a List before a Maybe hands on its element with no witness")
    void aListBeforeAMaybeNeedsNoWitness() {
      assertGeneratedCodeContains(
          result.compilation(),
          "com.example.SavedFocus",
          "\"drafts\").each(EachInstances.listEach()).some(Affines.just());");
    }

    @Test
    @DisplayName("a Just reads back its value")
    void aJustReadsBackItsValue() {
      assertThat(item().getOptional(saved(Maybe.just("alice"), List.of()))).contains("alice");
    }

    @Test
    @DisplayName("Nothing reads back as empty")
    void nothingReadsBackAsEmpty() {
      assertThat(item().getOptional(saved(Maybe.nothing(), List.of()))).isEmpty();
    }

    @Test
    @DisplayName("modifying a Just rebuilds a Just")
    void modifyingAJustRebuildsAJust() {
      Object modified =
          item().modify(FocusRecognisedContainerTest::upper, saved(Maybe.just("alice"), List.of()));

      assertThat(invoke(modified, "item")).isEqualTo(Maybe.just("ALICE"));
    }

    @Test
    @DisplayName("modifying Nothing leaves the record as it was")
    void modifyingNothingLeavesTheRecordAsItWas() {
      Object source = saved(Maybe.nothing(), List.of());

      assertThat(item().modify(FocusRecognisedContainerTest::upper, source)).isEqualTo(source);
    }

    @Test
    @DisplayName("a Maybe nested in a List is reached through it")
    void aMaybeNestedInAListIsReachedThroughIt() {
      Object source = saved(Maybe.nothing(), List.of(Maybe.just("alice"), Maybe.nothing()));

      Object modified = drafts().modifyAll(FocusRecognisedContainerTest::upper, source);

      assertThat(drafts().getAll(source)).containsExactly("alice");
      assertThat(invoke(modified, "drafts"))
          .isEqualTo(List.of(Maybe.just("ALICE"), Maybe.nothing()));
    }
  }

  @Nested
  @DisplayName("A raw or wildcard container")
  class ARawOrWildcardContainer {

    private static final JavaFileObject LOOSE =
        JavaFileObjects.forSourceString(
            "com.example.Loose",
            """
            package com.example;

            import java.util.Collection;
            import java.util.List;
            import java.util.Set;
            import org.higherkindedj.hkt.maybe.Maybe;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(widenCollections = true)
            @SuppressWarnings("rawtypes")
            public record Loose(
                Set<? extends CharSequence> set,
                Collection<?> collection,
                Maybe<? extends CharSequence> item,
                Set raw,
                Maybe rawItem,
                List<Maybe<?>> drafts,
                Set<? super String> lower,
                Set<? extends CharSequence>[] shelves) {}
            """);

    private final CompiledResult result =
        RuntimeCompilationHelper.compileWith(new FocusProcessor(), LOOSE);

    /** A Loose built from its eight components, in declaration order. */
    private Object loose(Object... components) {
      try {
        Constructor<?> constructor =
            result.loadClass("com.example.Loose").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(components);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError("could not build com.example.Loose", e);
      }
    }

    /** The Loose every component of which holds lower-case names. */
    private Object loose() {
      return loose(
          new HashSet<>(List.of("alice", "bob")),
          new ArrayList<>(List.of("alice", "alice")),
          Maybe.just("alice"),
          new HashSet<>(List.of("alice")),
          Maybe.just("alice"),
          List.of(Maybe.just("alice"), Maybe.nothing()),
          new HashSet<Object>(List.of("alice")),
          new Set<?>[] {new HashSet<>(List.of("alice"))});
    }

    private Object path(String component) {
      try {
        return result.invokeStatic("com.example.LooseFocus", component);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError("could not read com.example.LooseFocus." + component, e);
      }
    }

    @SuppressWarnings("unchecked") // the generated method's type arguments erase to these
    private TraversalPath<Object, Object> many(String component) {
      return (TraversalPath<Object, Object>) path(component);
    }

    @SuppressWarnings("unchecked") // the generated method's type arguments erase to these
    private AffinePath<Object, Object> one(String component) {
      return (AffinePath<Object, Object>) path(component);
    }

    private static Object upperAny(Object value) {
      return upper(value.toString());
    }

    private void generates(String code) {
      assertGeneratedCodeContains(result.compilation(), "com.example.LooseFocus", code);
    }

    @Test
    @DisplayName("compiles, with each instance cast to the container as written")
    void castsEachInstanceToTheContainerAsWritten() {
      assertThat(result.compilation()).succeeded();
      generates(
          "\"set\").each((Each<Set<? extends CharSequence>, CharSequence>) (Each<?, ?>)"
              + " EachInstances.<CharSequence>setEach());");
      generates(
          "\"collection\").each((Each<Collection<?>, Object>) (Each<?, ?>)"
              + " EachInstances.<Object>collectionEach());");
      generates(
          "\"item\").some((Affine<Maybe<? extends CharSequence>, CharSequence>) (Affine<?, ?>)"
              + " Affines.<CharSequence>just());");
      generates("\"raw\").each((Each<Set, Object>) (Each<?, ?>) EachInstances.<Object>setEach());");
      generates(
          "\"rawItem\").some((Affine<Maybe, Object>) (Affine<?, ?>) Affines.<Object>just());");
      generates(
          "\"drafts\").each(EachInstances.listEach()).some((Affine<Maybe<?>, Object>) (Affine<?,"
              + " ?>) Affines.<Object>just());");
      generates(
          "\"lower\").each((Each<Set<? super String>, Object>) (Each<?, ?>)"
              + " EachInstances.<Object>setEach());");
    }

    @Test
    @DisplayName("compiles clean under -Xlint:all and -Werror")
    void compilesCleanUnderWerror() {
      assertThat(
              javac()
                  .withProcessors(new FocusProcessor())
                  .withOptions("-Xlint:all,-processing", "-Werror")
                  .compile(LOOSE))
          .succeededWithoutWarnings();
    }

    @Test
    @DisplayName("each path focuses on the element the type arguments stand for")
    void eachPathFocusesOnTheElementTheArgumentsStandFor() {
      generates("public static TraversalPath<Loose, CharSequence> set()");
      generates("public static TraversalPath<Loose, Object> collection()");
      generates("public static AffinePath<Loose, CharSequence> item()");
      generates("public static TraversalPath<Loose, Object> raw()");
      generates("public static AffinePath<Loose, Object> rawItem()");
      generates("public static TraversalPath<Loose, Object> drafts()");
      generates("public static TraversalPath<Loose, Object> lower()");
      generates("public static TraversalPath<Loose, CharSequence> shelves()");
    }

    @Test
    @DisplayName("a wildcard Set comes back a Set of what was written")
    void aWildcardSetComesBackASet() {
      Object modified = many("set").modifyAll(ARawOrWildcardContainer::upperAny, loose());

      assertThat(invoke(modified, "set")).isEqualTo(Set.of("ALICE", "BOB"));
    }

    @Test
    @DisplayName("a wildcard Collection holding a list keeps its duplicates")
    void aWildcardCollectionKeepsItsDuplicates() {
      Object modified = many("collection").modifyAll(ARawOrWildcardContainer::upperAny, loose());

      assertThat(invoke(modified, "collection")).isEqualTo(List.of("ALICE", "ALICE"));
    }

    @Test
    @DisplayName("a wildcard Collection holding a set comes back a set")
    void aWildcardCollectionHoldingASetComesBackASet() {
      Object source =
          loose(
              Set.of(),
              new HashSet<>(List.of("alice", "bob")),
              Maybe.nothing(),
              Set.of(),
              Maybe.nothing(),
              List.of(),
              Set.of(),
              new Set<?>[0]);

      Object modified = many("collection").modifyAll(ARawOrWildcardContainer::upperAny, source);

      assertThat(invoke(modified, "collection"))
          .isInstanceOf(Set.class)
          .isEqualTo(Set.of("ALICE", "BOB"));
    }

    @Test
    @DisplayName("a wildcard Maybe rebuilds a Just, and reads Nothing as empty")
    void aWildcardMaybeRebuildsAJust() {
      Object modified = one("item").modify(ARawOrWildcardContainer::upperAny, loose());

      assertThat(invoke(modified, "item")).isEqualTo(Maybe.just("ALICE"));
      assertThat(
              one("item")
                  .getOptional(
                      loose(
                          Set.of(),
                          List.of(),
                          Maybe.nothing(),
                          Set.of(),
                          Maybe.nothing(),
                          List.of(),
                          Set.of(),
                          new Set<?>[0])))
          .isEmpty();
    }

    @Test
    @DisplayName("a raw Set and a raw Maybe are read and rebuilt as Object")
    void aRawContainerIsReadAndRebuilt() {
      Object source = loose();

      assertThat(many("raw").getAll(source)).containsExactly("alice");
      assertThat(invoke(many("raw").modifyAll(ARawOrWildcardContainer::upperAny, source), "raw"))
          .isEqualTo(Set.of("ALICE"));
      assertThat(
              invoke(one("rawItem").modify(ARawOrWildcardContainer::upperAny, source), "rawItem"))
          .isEqualTo(Maybe.just("ALICE"));
    }

    @Test
    @DisplayName("a super-bounded Set takes back whatever is written, as Object")
    void aSuperBoundedSetTakesBackObjects() {
      Object modified = many("lower").setAll(42, loose());

      assertThat(invoke(modified, "lower")).isEqualTo(Set.of(42));
    }

    @Test
    @DisplayName("a wildcard Maybe nested in a List is reached through it")
    void aWildcardMaybeNestedInAListIsReachedThroughIt() {
      Object source = loose();

      assertThat(many("drafts").getAll(source)).containsExactly("alice");
      assertThat(
              invoke(many("drafts").modifyAll(ARawOrWildcardContainer::upperAny, source), "drafts"))
          .isEqualTo(List.of(Maybe.just("ALICE"), Maybe.nothing()));
    }

    @Test
    @DisplayName("an array of wildcard Sets is stepped into, and comes back a new array")
    void anArrayOfWildcardSetsIsSteppedInto() {
      Set<?>[] shelves = {new HashSet<>(List.of("alice"))};
      Object source =
          loose(
              Set.of(),
              List.of(),
              Maybe.nothing(),
              Set.of(),
              Maybe.nothing(),
              List.of(),
              Set.of(),
              shelves);

      Object modified = many("shelves").modifyAll(ARawOrWildcardContainer::upperAny, source);

      assertThat((Object[]) invoke(modified, "shelves")).containsExactly(Set.of("ALICE"));
      assertThat(shelves).containsExactly(Set.of("alice"));
    }

    @Test
    @DisplayName("modifying leaves the source containers as they were")
    void modifyingLeavesTheSourceAsItWas() {
      Set<String> names = new HashSet<>(List.of("alice", "bob"));
      List<String> pile = new ArrayList<>(List.of("alice", "alice"));
      Object source =
          loose(
              names,
              pile,
              Maybe.nothing(),
              Set.of(),
              Maybe.nothing(),
              List.of(),
              Set.of(),
              new Set<?>[0]);

      many("set").modifyAll(ARawOrWildcardContainer::upperAny, source);
      many("collection").modifyAll(ARawOrWildcardContainer::upperAny, source);

      assertThat(names).containsExactlyInAnyOrder("alice", "bob");
      assertThat(pile).containsExactly("alice", "alice");
    }
  }
}
