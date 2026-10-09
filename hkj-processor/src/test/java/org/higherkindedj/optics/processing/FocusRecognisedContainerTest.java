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
 * <p>They do not share one: the no-argument {@code .each()} and {@code .some()} carry a {@code
 * List} traversal and an {@code Optional} affine, and cast the focused value to that type
 * unchecked, so a {@code Set}, a {@code Collection} or a {@code Maybe} routed through either
 * compiles and then throws on first use. A compile-testing assertion cannot see that, which is why
 * the reads and modifications below run the generated path and read the component back rather than
 * only reading the emitted source.
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
    @DisplayName("a no-argument step before a Maybe spells out the element it hands on")
    void aNoArgumentStepBeforeAMaybeSpellsOutTheElementItHandsOn() {
      assertGeneratedCodeContains(
          result.compilation(),
          "com.example.SavedFocus",
          "\"drafts\").<Maybe<String>>each().some(Affines.just());");
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
  @DisplayName("A container whose optic cannot be instantiated")
  class AContainerWhoseOpticCannotBeInstantiated {

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
                  import org.higherkindedj.hkt.maybe.Maybe;
                  import org.higherkindedj.optics.annotations.GenerateFocus;

                  @GenerateFocus
                  @SuppressWarnings("rawtypes")
                  public record Holder(%s f) {}
                  """
                      .formatted(component)));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "Set<?>",
          "Set<? extends CharSequence>",
          "Collection<?>",
          "Maybe<?>",
          "Maybe<? extends CharSequence>"
        })
    @DisplayName("a wildcard is reported against the declaration")
    void aWildcardIsReportedAgainstTheDeclaration(String component) {
      assertThat(compile(component))
          .hadErrorContaining(
              "record component 'Holder.f' has a wildcard type argument in " + component + ".");
    }

    @Test
    @DisplayName("a type-use annotation stays out of the reported type name")
    void aTypeUseAnnotationStaysOutOfTheReportedTypeName() {
      // Both halves of the message must render the type the same way: naming '@Nully Set<?>' and
      // then suggesting 'Set<Object>' reads as advice to drop the annotation (#759).
      var annotation =
          JavaFileObjects.forSourceString(
              "com.example.Nully",
              """
              package com.example;

              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;

              @Target(ElementType.TYPE_USE)
              public @interface Nully {}
              """);
      var holder =
          JavaFileObjects.forSourceString(
              "com.example.Holder",
              """
              package com.example;

              import java.util.Set;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Holder(@Nully Set<?> f) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(annotation, holder);

      assertThat(compilation)
          .hadErrorContaining(
              "record component 'Holder.f' has a wildcard type argument in Set<?>.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare the component with concrete type arguments, such as Set<Object>,");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Set", "Collection", "Maybe"})
    @DisplayName("a raw container is reported against the declaration")
    void aRawContainerIsReportedAgainstTheDeclaration(String component) {
      assertThat(compile(component))
          .hadErrorContaining("record component 'Holder.f' has a raw " + component + ".");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "Set<?>",
          "Set<? extends CharSequence>",
          "Collection<?>",
          "Set",
          "Maybe<?>",
          "Maybe<? extends CharSequence>",
          "Maybe"
        })
    @DisplayName(
        "the remedy the diagnostic offers compiles: the sibling annotations take it as written")
    void theRemedyTheDiagnosticOffersCompiles(String component) {
      Compilation compilation =
          javac()
              .withProcessors(new LensProcessor(), new TraversalProcessor())
              .compile(
                  JavaFileObjects.forSourceString(
                      "com.example.Holder",
                      """
                      package com.example;

                      import java.util.Collection;
                      import java.util.Set;
                      import org.higherkindedj.hkt.maybe.Maybe;
                      import org.higherkindedj.optics.annotations.GenerateLenses;
                      import org.higherkindedj.optics.annotations.GenerateTraversals;

                      @GenerateLenses
                      @GenerateTraversals
                      @SuppressWarnings("rawtypes")
                      public record Holder(%s f) {}
                      """
                          .formatted(component)));

      assertThat(compilation).succeeded();
    }
  }
}
