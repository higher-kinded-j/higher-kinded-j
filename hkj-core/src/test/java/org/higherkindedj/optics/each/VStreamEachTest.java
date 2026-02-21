// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.each;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedSelective;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskApplicative;
import org.higherkindedj.hkt.vtask.VTaskKind;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VStream Each and Optics Integration Tests")
class VStreamEachTest {

  // ===== Basic VStreamTraversals Tests =====

  @Nested
  @DisplayName("VStreamTraversals.forVStream()")
  class VStreamTraversalTests {

    @Test
    @DisplayName("should traverse all elements of a VStream")
    void traversesAllElements() {
      Traversal<VStream<String>, String> traversal = VStreamTraversals.forVStream();
      VStream<String> stream = VStream.fromList(List.of("a", "b", "c"));

      List<String> elements = Traversals.getAll(traversal, stream);

      assertThat(elements).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("should modify all elements")
    void modifiesAllElements() {
      Traversal<VStream<String>, String> traversal = VStreamTraversals.forVStream();
      VStream<String> stream = VStream.fromList(List.of("hello", "world"));

      VStream<String> modified = Traversals.modify(traversal, String::toUpperCase, stream);

      assertThat(modified.toList().run()).containsExactly("HELLO", "WORLD");
    }

    @Test
    @DisplayName("should handle empty VStream")
    void handlesEmptyStream() {
      Traversal<VStream<String>, String> traversal = VStreamTraversals.forVStream();
      VStream<String> stream = VStream.empty();

      List<String> elements = Traversals.getAll(traversal, stream);

      assertThat(elements).isEmpty();
    }

    @Test
    @DisplayName("should handle single-element VStream")
    void handlesSingleElement() {
      Traversal<VStream<Integer>, Integer> traversal = VStreamTraversals.forVStream();
      VStream<Integer> stream = VStream.of(42);

      List<Integer> elements = Traversals.getAll(traversal, stream);

      assertThat(elements).containsExactly(42);
    }

    @Test
    @DisplayName("should set all elements to same value")
    void setsAllElements() {
      Traversal<VStream<String>, String> traversal = VStreamTraversals.forVStream();
      VStream<String> stream = VStream.fromList(List.of("a", "b", "c"));

      VStream<String> replaced = Traversals.modify(traversal, ignored -> "X", stream);

      assertThat(replaced.toList().run()).containsExactly("X", "X", "X");
    }

    @Test
    @DisplayName("should handle VStream with duplicate elements")
    void handlesDuplicates() {
      Traversal<VStream<Integer>, Integer> traversal = VStreamTraversals.forVStream();
      VStream<Integer> stream = VStream.fromList(List.of(1, 2, 1, 3, 2));

      List<Integer> elements = Traversals.getAll(traversal, stream);

      assertThat(elements).containsExactly(1, 2, 1, 3, 2);
    }
  }

  // ===== EachInstances.vstreamEach() Tests =====

  @Nested
  @DisplayName("EachInstances.vstreamEach()")
  class VStreamEachInstanceTests {

    private final Each<VStream<String>, String> vstreamEach = EachInstances.vstreamEach();

    @Test
    @DisplayName("each() should return non-null traversal")
    void eachReturnsTraversal() {
      Traversal<VStream<String>, String> traversal = vstreamEach.each();
      assertThat(traversal).isNotNull();
    }

    @Test
    @DisplayName("each() should traverse all elements")
    void eachTraversesAllElements() {
      VStream<String> stream = VStream.fromList(List.of("a", "b", "c"));
      Traversal<VStream<String>, String> traversal = vstreamEach.each();

      List<String> elements = Traversals.getAll(traversal, stream);

      assertThat(elements).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("each() should modify all elements")
    void eachModifiesAllElements() {
      VStream<String> stream = VStream.fromList(List.of("hello", "world"));
      Traversal<VStream<String>, String> traversal = vstreamEach.each();

      VStream<String> modified = Traversals.modify(traversal, String::toUpperCase, stream);

      assertThat(modified.toList().run()).containsExactly("HELLO", "WORLD");
    }

    @Test
    @DisplayName("each() should handle empty VStream")
    void eachHandlesEmpty() {
      VStream<String> stream = VStream.empty();
      Traversal<VStream<String>, String> traversal = vstreamEach.each();

      List<String> elements = Traversals.getAll(traversal, stream);

      assertThat(elements).isEmpty();
    }

    @Test
    @DisplayName("supportsIndexed() should return false for VStream")
    void supportsIndexedReturnsFalse() {
      assertThat(vstreamEach.supportsIndexed()).isFalse();
    }

    @Test
    @DisplayName("eachWithIndex() should return empty for VStream")
    void eachWithIndexReturnsEmpty() {
      Optional<IndexedTraversal<Object, VStream<String>, String>> indexed =
          vstreamEach.eachWithIndex();

      assertThat(indexed).isEmpty();
    }

    @Test
    @DisplayName("vstreamEach() should return new instances")
    void vstreamEachReturnsNewInstances() {
      Each<VStream<String>, String> first = EachInstances.vstreamEach();
      Each<VStream<String>, String> second = EachInstances.vstreamEach();

      assertThat(first).isNotSameAs(second);
    }
  }

  // ===== FocusDSL Integration Tests =====

  @Nested
  @DisplayName("FocusDSL Integration")
  class FocusDSLIntegrationTests {

    // Simple record for testing lens + each composition
    record Container(VStream<String> items) {}

    // Lens to access the items field
    private static final Lens<Container, VStream<String>> ITEMS_LENS =
        Lens.of(Container::items, (container, items) -> new Container(items));

    @Test
    @DisplayName("FocusPath.via(lens).each(vstreamEach) navigates into VStream elements")
    void focusPathWithVStreamEach() {
      Each<VStream<String>, String> vstreamEach = EachInstances.vstreamEach();
      TraversalPath<Container, String> allItems = FocusPath.of(ITEMS_LENS).each(vstreamEach);

      Container container = new Container(VStream.fromList(List.of("a", "b", "c")));

      List<String> items = allItems.getAll(container);

      assertThat(items).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("FocusDSL each() modifies all VStream elements")
    void focusDSLModifiesElements() {
      Each<VStream<String>, String> vstreamEach = EachInstances.vstreamEach();
      TraversalPath<Container, String> allItems = FocusPath.of(ITEMS_LENS).each(vstreamEach);

      Container original = new Container(VStream.fromList(List.of("hello", "world")));

      Container modified = allItems.modifyAll(String::toUpperCase, original);

      assertThat(modified.items().toList().run()).containsExactly("HELLO", "WORLD");
    }

    @Test
    @DisplayName("FocusDSL each() with empty VStream returns empty getAll")
    void focusDSLWithEmptyVStream() {
      Each<VStream<String>, String> vstreamEach = EachInstances.vstreamEach();
      TraversalPath<Container, String> allItems = FocusPath.of(ITEMS_LENS).each(vstreamEach);

      Container container = new Container(VStream.empty());

      List<String> items = allItems.getAll(container);

      assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("FocusDSL filter on VStream elements")
    void focusDSLFilterElements() {
      Each<VStream<Integer>, Integer> vstreamEach = EachInstances.vstreamEach();

      record IntContainer(VStream<Integer> numbers) {}

      Lens<IntContainer, VStream<Integer>> numbersLens =
          Lens.of(IntContainer::numbers, (c, n) -> new IntContainer(n));

      TraversalPath<IntContainer, Integer> allNumbers = FocusPath.of(numbersLens).each(vstreamEach);

      IntContainer container = new IntContainer(VStream.fromList(List.of(1, 2, 3, 4, 5)));

      TraversalPath<IntContainer, Integer> evens = allNumbers.filter(n -> n % 2 == 0);

      List<Integer> evenNumbers = evens.getAll(container);
      assertThat(evenNumbers).containsExactly(2, 4);
    }

    @Test
    @DisplayName("FocusDSL setAll on VStream elements")
    void focusDSLSetAll() {
      Each<VStream<String>, String> vstreamEach = EachInstances.vstreamEach();
      TraversalPath<Container, String> allItems = FocusPath.of(ITEMS_LENS).each(vstreamEach);

      Container container = new Container(VStream.fromList(List.of("a", "b", "c")));

      Container updated = allItems.setAll("X", container);

      assertThat(updated.items().toList().run()).containsExactly("X", "X", "X");
    }
  }

  // ===== TraversalPath.toVStreamPath() Tests =====

  @Nested
  @DisplayName("TraversalPath.toVStreamPath()")
  class ToVStreamPathTests {

    @Test
    @DisplayName("toVStreamPath() extracts focused values into VStreamPath")
    void toVStreamPathExtractsValues() {
      Traversal<List<String>, String> listTraversal = Traversals.forList();
      TraversalPath<List<String>, String> path = TraversalPath.of(listTraversal);

      List<String> source = List.of("a", "b", "c");

      VStreamPath<String> vstreamPath = path.toVStreamPath(source);

      assertThat(vstreamPath.run().toList().run()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("toVStreamPath() returns empty VStreamPath for empty source")
    void toVStreamPathEmptySource() {
      Traversal<List<String>, String> listTraversal = Traversals.forList();
      TraversalPath<List<String>, String> path = TraversalPath.of(listTraversal);

      List<String> source = List.of();

      VStreamPath<String> vstreamPath = path.toVStreamPath(source);

      assertThat(vstreamPath.run().toList().run()).isEmpty();
    }

    @Test
    @DisplayName("toVStreamPath() supports lazy stream operations")
    void toVStreamPathSupportsLazyOps() {
      Traversal<List<Integer>, Integer> listTraversal = Traversals.forList();
      TraversalPath<List<Integer>, Integer> path = TraversalPath.of(listTraversal);

      List<Integer> source = List.of(1, 2, 3, 4, 5);

      VStreamPath<Integer> vstreamPath = path.toVStreamPath(source);

      // Apply lazy operations
      List<Integer> result = vstreamPath.filter(n -> n > 2).map(n -> n * 10).toList().unsafeRun();

      assertThat(result).containsExactly(30, 40, 50);
    }

    @Test
    @DisplayName("toVStreamPath() on single-element traversal")
    void toVStreamPathSingleElement() {
      Traversal<List<String>, String> listTraversal = Traversals.forList();
      TraversalPath<List<String>, String> path = TraversalPath.of(listTraversal);

      List<String> source = List.of("only");

      VStreamPath<String> vstreamPath = path.toVStreamPath(source);

      assertThat(vstreamPath.run().toList().run()).containsExactly("only");
    }
  }

  // ===== VStreamPath.fromEach() Tests =====

  @Nested
  @DisplayName("VStreamPath.fromEach()")
  class FromEachTests {

    @Test
    @DisplayName("fromEach() creates VStreamPath from List and listEach")
    void fromEachWithList() {
      Each<List<String>, String> listEach = EachInstances.listEach();
      List<String> source = List.of("a", "b", "c");

      VStreamPath<String> path = VStreamPath.fromEach(source, listEach);

      assertThat(path.run().toList().run()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("fromEach() creates VStreamPath from VStream and vstreamEach")
    void fromEachWithVStream() {
      Each<VStream<Integer>, Integer> vstreamEach = EachInstances.vstreamEach();
      VStream<Integer> source = VStream.fromList(List.of(1, 2, 3));

      VStreamPath<Integer> path = VStreamPath.fromEach(source, vstreamEach);

      assertThat(path.run().toList().run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("fromEach() supports chaining operations on result")
    void fromEachSupportsChainingOps() {
      Each<List<Integer>, Integer> listEach = EachInstances.listEach();
      List<Integer> source = List.of(1, 2, 3, 4, 5);

      VStreamPath<Integer> path = VStreamPath.fromEach(source, listEach);

      List<Integer> result = path.filter(n -> n % 2 == 0).map(n -> n * 10).toList().unsafeRun();

      assertThat(result).containsExactly(20, 40);
    }

    @Test
    @DisplayName("fromEach() with empty source creates empty VStreamPath")
    void fromEachWithEmptySource() {
      Each<List<String>, String> listEach = EachInstances.listEach();
      List<String> source = List.of();

      VStreamPath<String> path = VStreamPath.fromEach(source, listEach);

      assertThat(path.run().toList().run()).isEmpty();
    }

    @Test
    @DisplayName("fromEach() throws NullPointerException for null source")
    void fromEachNullSourceThrows() {
      Each<List<String>, String> listEach = EachInstances.listEach();

      assertThatNullPointerException()
          .isThrownBy(() -> VStreamPath.fromEach(null, listEach))
          .withMessage("source must not be null");
    }

    @Test
    @DisplayName("fromEach() throws NullPointerException for null each")
    void fromEachNullEachThrows() {
      List<String> source = List.of("a");

      assertThatNullPointerException()
          .isThrownBy(() -> VStreamPath.fromEach(source, null))
          .withMessage("each must not be null");
    }

    @Test
    @DisplayName("fromEach() with Optional Each extracts present value")
    void fromEachWithOptionalEach() {
      Each<Optional<String>, String> optEach = EachInstances.optionalEach();
      Optional<String> source = Optional.of("hello");

      VStreamPath<String> path = VStreamPath.fromEach(source, optEach);

      assertThat(path.run().toList().run()).containsExactly("hello");
    }

    @Test
    @DisplayName("fromEach() with empty Optional creates empty VStreamPath")
    void fromEachWithEmptyOptional() {
      Each<Optional<String>, String> optEach = EachInstances.optionalEach();
      Optional<String> source = Optional.empty();

      VStreamPath<String> path = VStreamPath.fromEach(source, optEach);

      assertThat(path.run().toList().run()).isEmpty();
    }
  }

  // ===== modifyF with Different Applicatives =====

  @Nested
  @DisplayName("modifyF with Different Applicatives")
  class WithApplicatives {

    private final Traversal<VStream<Integer>, Integer> traversal = VStreamTraversals.forVStream();

    @Test
    @DisplayName("modifyF with IdApplicative performs pure transformation")
    void modifyFWithId() {
      VStream<Integer> stream = VStream.fromList(List.of(1, 2, 3));

      Kind<IdKind.Witness, VStream<Integer>> result =
          traversal.modifyF(a -> ID.widen(Id.of(a * 10)), stream, IdMonad.instance());

      VStream<Integer> modified = ID.narrow(result).value();
      assertThat(modified.toList().run()).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("modifyF with IdApplicative on empty VStream returns empty")
    void modifyFWithIdEmpty() {
      VStream<Integer> stream = VStream.empty();

      Kind<IdKind.Witness, VStream<Integer>> result =
          traversal.modifyF(a -> ID.widen(Id.of(a * 10)), stream, IdMonad.instance());

      VStream<Integer> modified = ID.narrow(result).value();
      assertThat(modified.toList().run()).isEmpty();
    }

    @Test
    @DisplayName("modifyF with MaybeApplicative succeeds when all elements return Just")
    void modifyFWithMaybeAllJust() {
      VStream<Integer> stream = VStream.fromList(List.of(1, 2, 3));

      Function<Integer, Kind<MaybeKind.Witness, Integer>> safeDouble =
          n -> MAYBE.widen(Maybe.just(n * 2));

      Kind<MaybeKind.Witness, VStream<Integer>> result =
          traversal.modifyF(safeDouble, stream, MaybeMonad.INSTANCE);

      Maybe<VStream<Integer>> maybeResult = MAYBE.narrow(result);
      assertThat(maybeResult.isJust()).isTrue();
      assertThat(maybeResult.orElse(null).toList().run()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("modifyF with MaybeApplicative fails fast on Nothing")
    void modifyFWithMaybeFailFast() {
      VStream<Integer> stream = VStream.fromList(List.of(1, 2, 3));

      // Return Nothing for element > 1
      Function<Integer, Kind<MaybeKind.Witness, Integer>> failOnLarge =
          n -> n > 1 ? MAYBE.widen(Maybe.nothing()) : MAYBE.widen(Maybe.just(n));

      Kind<MaybeKind.Witness, VStream<Integer>> result =
          traversal.modifyF(failOnLarge, stream, MaybeMonad.INSTANCE);

      Maybe<VStream<Integer>> maybeResult = MAYBE.narrow(result);
      assertThat(maybeResult.isNothing()).isTrue();
    }

    @Test
    @DisplayName("modifyF with OptionalApplicative succeeds when all present")
    void modifyFWithOptionalAllPresent() {
      VStream<Integer> stream = VStream.fromList(List.of(10, 20, 30));

      Function<Integer, Kind<OptionalKind.Witness, Integer>> safeHalf =
          n -> OPTIONAL.widen(Optional.of(n / 2));

      Kind<OptionalKind.Witness, VStream<Integer>> result =
          traversal.modifyF(safeHalf, stream, OptionalMonad.INSTANCE);

      Optional<VStream<Integer>> optResult = OPTIONAL.narrow(result);
      assertThat(optResult).isPresent();
      assertThat(optResult.get().toList().run()).containsExactly(5, 10, 15);
    }

    @Test
    @DisplayName("modifyF with OptionalApplicative returns empty when any element fails")
    void modifyFWithOptionalFailure() {
      VStream<Integer> stream = VStream.fromList(List.of(1, 2, 3));

      // Return empty for even numbers
      Function<Integer, Kind<OptionalKind.Witness, Integer>> failOnEven =
          n -> n % 2 == 0 ? OPTIONAL.widen(Optional.empty()) : OPTIONAL.widen(Optional.of(n));

      Kind<OptionalKind.Witness, VStream<Integer>> result =
          traversal.modifyF(failOnEven, stream, OptionalMonad.INSTANCE);

      Optional<VStream<Integer>> optResult = OPTIONAL.narrow(result);
      assertThat(optResult).isEmpty();
    }

    @Test
    @DisplayName("modifyF with ValidatedApplicative accumulates errors")
    void modifyFWithValidatedAccumulatesErrors() {
      VStream<Integer> stream = VStream.fromList(List.of(-1, 2, -3));

      ValidatedSelective<String> applicative = ValidatedSelective.instance(Semigroups.string(", "));

      // Negative numbers produce errors
      Function<Integer, Kind<ValidatedKind.Witness<String>, Integer>> validatePositive =
          n ->
              n > 0
                  ? VALIDATED.widen(Validated.valid(n))
                  : VALIDATED.widen(Validated.invalid(n + " is negative"));

      Kind<ValidatedKind.Witness<String>, VStream<Integer>> result =
          traversal.modifyF(validatePositive, stream, applicative);

      Validated<String, VStream<Integer>> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).contains("-1 is negative");
      assertThat(validated.getError()).contains("-3 is negative");
    }

    @Test
    @DisplayName("modifyF with ValidatedApplicative succeeds when all valid")
    void modifyFWithValidatedAllValid() {
      VStream<Integer> stream = VStream.fromList(List.of(1, 2, 3));

      ValidatedSelective<String> applicative = ValidatedSelective.instance(Semigroups.string(", "));

      Function<Integer, Kind<ValidatedKind.Witness<String>, Integer>> alwaysValid =
          n -> VALIDATED.widen(Validated.valid(n * 10));

      Kind<ValidatedKind.Witness<String>, VStream<Integer>> result =
          traversal.modifyF(alwaysValid, stream, applicative);

      Validated<String, VStream<Integer>> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get().toList().run()).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("modifyF with VTaskApplicative performs effectful transformation")
    void modifyFWithVTask() {
      VStream<Integer> stream = VStream.fromList(List.of(1, 2, 3));

      Function<Integer, Kind<VTaskKind.Witness, Integer>> asyncDouble =
          n -> VTASK.widen(VTask.of(() -> n * 2));

      Kind<VTaskKind.Witness, VStream<Integer>> result =
          traversal.modifyF(asyncDouble, stream, VTaskApplicative.INSTANCE);

      VTask<VStream<Integer>> vtaskResult = VTASK.narrow(result);
      VStream<Integer> modified = vtaskResult.run();
      assertThat(modified.toList().run()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("modifyF with VTaskApplicative on empty VStream returns empty result")
    void modifyFWithVTaskEmpty() {
      VStream<Integer> stream = VStream.empty();

      Function<Integer, Kind<VTaskKind.Witness, Integer>> asyncDouble =
          n -> VTASK.widen(VTask.of(() -> n * 2));

      Kind<VTaskKind.Witness, VStream<Integer>> result =
          traversal.modifyF(asyncDouble, stream, VTaskApplicative.INSTANCE);

      VTask<VStream<Integer>> vtaskResult = VTASK.narrow(result);
      VStream<Integer> modified = vtaskResult.run();
      assertThat(modified.toList().run()).isEmpty();
    }
  }

  // ===== Optics Composition =====

  @Nested
  @DisplayName("Optics Composition")
  class OpticsComposition {

    @Test
    @DisplayName("Lens then VStream Each composes correctly")
    void lensThenVStreamEach() {
      record Bag(VStream<String> tags) {}

      Lens<Bag, VStream<String>> tagsLens = Lens.of(Bag::tags, (bag, tags) -> new Bag(tags));
      Traversal<VStream<String>, String> eachTag = EachInstances.<String>vstreamEach().each();

      Traversal<Bag, String> allTags = tagsLens.andThen(eachTag);

      Bag bag = new Bag(VStream.fromList(List.of("java", "kotlin")));

      List<String> tags = Traversals.getAll(allTags, bag);
      assertThat(tags).containsExactly("java", "kotlin");

      Bag modified = Traversals.modify(allTags, String::toUpperCase, bag);
      assertThat(modified.tags().toList().run()).containsExactly("JAVA", "KOTLIN");
    }

    @Test
    @DisplayName("Nested VStream Each composition (two levels deep)")
    void nestedVStreamEachTwoLevels() {
      record Inner(VStream<Integer> values) {}

      Lens<Inner, VStream<Integer>> valuesLens =
          Lens.of(Inner::values, (inner, vals) -> new Inner(vals));

      // Outer: VStream<Inner>
      Traversal<VStream<Inner>, Inner> eachInner = VStreamTraversals.forVStream();
      Traversal<Inner, Integer> innerValues = valuesLens.andThen(VStreamTraversals.forVStream());

      // Compose: VStream<Inner> -> Inner -> Integer (two-level each)
      Traversal<VStream<Inner>, Integer> allValues = eachInner.andThen(innerValues);

      VStream<Inner> outerStream =
          VStream.fromList(
              List.of(
                  new Inner(VStream.fromList(List.of(1, 2))),
                  new Inner(VStream.fromList(List.of(3, 4, 5)))));

      List<Integer> allInts = Traversals.getAll(allValues, outerStream);
      assertThat(allInts).containsExactly(1, 2, 3, 4, 5);

      VStream<Inner> modified = Traversals.modify(allValues, n -> n * 10, outerStream);
      List<Integer> modifiedInts = Traversals.getAll(allValues, modified);
      assertThat(modifiedInts).containsExactly(10, 20, 30, 40, 50);
    }

    @Test
    @DisplayName("Nested VStream Each via FocusDSL (two levels deep)")
    void nestedVStreamEachViaFocusDSL() {
      record Cell(VStream<String> items) {}

      Lens<Cell, VStream<String>> cellItemsLens =
          Lens.of(Cell::items, (c, items) -> new Cell(items));

      record Grid(VStream<Cell> cells) {}

      Lens<Grid, VStream<Cell>> gridCellsLens = Lens.of(Grid::cells, (g, cells) -> new Grid(cells));

      Each<VStream<Cell>, Cell> cellEach = EachInstances.vstreamEach();
      Each<VStream<String>, String> stringEach = EachInstances.vstreamEach();

      TraversalPath<Grid, String> allItems =
          FocusPath.of(gridCellsLens).each(cellEach).via(cellItemsLens).each(stringEach);

      Grid grid =
          new Grid(
              VStream.fromList(
                  List.of(
                      new Cell(VStream.fromList(List.of("a", "b"))),
                      new Cell(VStream.fromList(List.of("c"))))));

      List<String> items = allItems.getAll(grid);
      assertThat(items).containsExactly("a", "b", "c");

      Grid modified = allItems.modifyAll(String::toUpperCase, grid);
      assertThat(allItems.getAll(modified)).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("VStream Each then Prism composes correctly")
    void vstreamEachThenPrism() {
      // Prism for positive integers
      Prism<Integer, Integer> positivePrism =
          Prism.of(n -> n > 0 ? Optional.of(n) : Optional.empty(), n -> n);

      Traversal<VStream<Integer>, Integer> eachElement = VStreamTraversals.forVStream();
      Traversal<VStream<Integer>, Integer> positiveElements = eachElement.andThen(positivePrism);

      VStream<Integer> stream = VStream.fromList(List.of(-1, 2, -3, 4, 5));

      // getAll only returns matched elements
      List<Integer> positives = Traversals.getAll(positiveElements, stream);
      assertThat(positives).containsExactly(2, 4, 5);

      // modify only affects matched elements; non-matched remain unchanged
      VStream<Integer> modified = Traversals.modify(positiveElements, n -> n * 100, stream);
      assertThat(modified.toList().run()).containsExactly(-1, 200, -3, 400, 500);
    }

    @Test
    @DisplayName("VStream Each then Affine composes correctly")
    void vstreamEachThenAffine() {
      record Entry(Optional<String> label) {}

      Affine<Entry, String> labelAffine =
          Affine.of(Entry::label, (entry, lbl) -> new Entry(Optional.of(lbl)));

      Traversal<VStream<Entry>, Entry> eachEntry = VStreamTraversals.forVStream();
      Traversal<VStream<Entry>, String> allLabels = eachEntry.andThen(labelAffine.asTraversal());

      VStream<Entry> stream =
          VStream.fromList(
              List.of(
                  new Entry(Optional.of("one")),
                  new Entry(Optional.empty()),
                  new Entry(Optional.of("three"))));

      // getAll only returns present labels
      List<String> labels = Traversals.getAll(allLabels, stream);
      assertThat(labels).containsExactly("one", "three");

      // modify only affects entries with labels present
      VStream<Entry> modified = Traversals.modify(allLabels, String::toUpperCase, stream);
      List<Entry> entries = modified.toList().run();
      assertThat(entries.get(0).label()).contains("ONE");
      assertThat(entries.get(1).label()).isEmpty(); // unchanged
      assertThat(entries.get(2).label()).contains("THREE");
    }

    @Test
    @DisplayName("Full pipeline: Lens -> Each -> Lens -> modify")
    void fullPipelineLensEachLensModify() {
      record Item(String name, int price) {}

      record Cart(VStream<Item> items) {}

      Lens<Cart, VStream<Item>> cartItemsLens =
          Lens.of(Cart::items, (cart, items) -> new Cart(items));
      Lens<Item, String> itemNameLens =
          Lens.of(Item::name, (item, name) -> new Item(name, item.price()));

      Each<VStream<Item>, Item> itemEach = EachInstances.vstreamEach();

      // Lens -> Each -> Lens
      TraversalPath<Cart, String> allItemNames =
          FocusPath.of(cartItemsLens).each(itemEach).via(itemNameLens);

      Cart cart = new Cart(VStream.fromList(List.of(new Item("apple", 1), new Item("banana", 2))));

      List<String> names = allItemNames.getAll(cart);
      assertThat(names).containsExactly("apple", "banana");

      Cart modified = allItemNames.modifyAll(String::toUpperCase, cart);
      assertThat(allItemNames.getAll(modified)).containsExactly("APPLE", "BANANA");
    }
  }

  // ===== Edge Cases =====

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("VStream traversal preserves element order")
    void preservesOrder() {
      Each<VStream<Integer>, Integer> vstreamEach = EachInstances.vstreamEach();
      VStream<Integer> stream = VStream.fromList(List.of(3, 1, 4, 1, 5, 9, 2, 6));

      List<Integer> result = Traversals.getAll(vstreamEach.each(), stream);

      assertThat(result).containsExactly(3, 1, 4, 1, 5, 9, 2, 6);
    }

    @Test
    @DisplayName("VStream traversal with large collection")
    void handlesLargeCollection() {
      Each<VStream<Integer>, Integer> vstreamEach = EachInstances.vstreamEach();
      List<Integer> largeList = IntStream.rangeClosed(1, 1000).boxed().toList();
      VStream<Integer> stream = VStream.fromList(largeList);

      List<Integer> result = Traversals.getAll(vstreamEach.each(), stream);

      assertThat(result).hasSize(1000);
      assertThat(result.get(0)).isEqualTo(1);
      assertThat(result.get(999)).isEqualTo(1000);
    }

    @Test
    @DisplayName("VStream modify preserves structure for identity function")
    void modifyIdentityPreservesStructure() {
      Each<VStream<String>, String> vstreamEach = EachInstances.vstreamEach();
      VStream<String> original = VStream.fromList(List.of("a", "b", "c"));

      VStream<String> result = Traversals.modify(vstreamEach.each(), Function.identity(), original);

      assertThat(result.toList().run()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("VStream traversal with null elements in stream")
    void handlesNullElements() {
      Traversal<VStream<String>, String> traversal = VStreamTraversals.forVStream();
      List<String> elements = new ArrayList<>();
      elements.add("a");
      elements.add(null);
      elements.add("c");
      VStream<String> stream = VStream.fromList(elements);

      List<String> result = Traversals.getAll(traversal, stream);

      assertThat(result).containsExactly("a", null, "c");
    }

    @Test
    @DisplayName("toVStreamPath and fromEach roundtrip")
    void toVStreamPathFromEachRoundtrip() {
      // Start with a list, go to VStreamPath, collect back
      Each<List<Integer>, Integer> listEach = EachInstances.listEach();
      List<Integer> original = List.of(10, 20, 30);

      VStreamPath<Integer> vsp = VStreamPath.fromEach(original, listEach);
      List<Integer> collected = vsp.toList().unsafeRun();

      assertThat(collected).containsExactly(10, 20, 30);
    }
  }
}
