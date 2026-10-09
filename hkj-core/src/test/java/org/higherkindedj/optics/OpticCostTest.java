// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalTraverse;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.indexed.IndexedFold;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What an optic does and does not do on the way to its answer: a read rebuilds nothing, a query
 * stops at the focus that settles it, and a prism or affine that matches lifts no unused value.
 *
 * <p>Each optic here counts the work a careless implementation would do: the rebuilds a read runs,
 * the foci a query visits, or the values an applicative lifts with {@code of}. One case is a
 * control: a query that finds no answer still visits every focus.
 */
@DisplayName("An optic does only the work its answer needs")
class OpticCostTest {

  /** A list traversal that counts each time it rebuilds the list, and records each focus. */
  private static final class CountingTraversal implements Traversal<List<Integer>, Integer> {
    int rebuilds;
    final List<Integer> visited = new ArrayList<>();

    @Override
    public <F extends WitnessArity<TypeArity.Unary>> Kind<F, List<Integer>> modifyF(
        Function<Integer, Kind<F, Integer>> f, List<Integer> source, Applicative<F> app) {
      Kind<F, List<Integer>> rebuilt = app.of(List.of());
      for (Integer a : source) {
        visited.add(a);
        rebuilt = app.map2(rebuilt, f.apply(a), this::append);
      }
      return rebuilt;
    }

    private List<Integer> append(List<Integer> list, Integer a) {
      rebuilds++;
      List<Integer> next = new ArrayList<>(list);
      next.add(a);
      return next;
    }
  }

  /** The indexed counterpart of {@link CountingTraversal}, indexing each focus by position. */
  private static final class CountingIndexedTraversal
      implements IndexedTraversal<Integer, List<Integer>, Integer> {
    int rebuilds;

    @Override
    public <F extends WitnessArity<TypeArity.Unary>> Kind<F, List<Integer>> imodifyF(
        BiFunction<Integer, Integer, Kind<F, Integer>> f,
        List<Integer> source,
        Applicative<F> app) {
      Kind<F, List<Integer>> rebuilt = app.of(List.of());
      for (int i = 0; i < source.size(); i++) {
        rebuilt = app.map2(rebuilt, f.apply(i, source.get(i)), this::append);
      }
      return rebuilt;
    }

    private List<Integer> append(List<Integer> list, Integer a) {
      rebuilds++;
      List<Integer> next = new ArrayList<>(list);
      next.add(a);
      return next;
    }
  }

  /** A fold over a list that records each focus it visits. */
  private static final class RecordingFold implements Fold<List<Integer>, Integer> {
    final List<@Nullable Integer> visited = new ArrayList<>();

    @Override
    public <M> M foldMap(
        Monoid<M> monoid, Function<? super Integer, ? extends M> f, List<Integer> source) {
      M result = monoid.empty();
      for (Integer a : source) {
        visited.add(a);
        result = monoid.combine(result, f.apply(a));
      }
      return result;
    }
  }

  /** An indexed fold over a list that records each position it visits. */
  private static final class RecordingIndexedFold
      implements IndexedFold<Integer, List<Integer>, Integer> {
    final List<Integer> visited = new ArrayList<>();

    @Override
    public <M> M ifoldMap(
        Monoid<M> monoid,
        BiFunction<? super Integer, ? super Integer, ? extends M> f,
        List<Integer> source) {
      M result = monoid.empty();
      for (int i = 0; i < source.size(); i++) {
        visited.add(i);
        result = monoid.combine(result, f.apply(i, source.get(i)));
      }
      return result;
    }
  }

  /** The identity applicative, counting the values it lifts with {@code of}. */
  private static final class CountingId implements Applicative<IdKind.Witness> {
    int lifted;

    @Override
    public <A> Kind<IdKind.Witness, A> of(@Nullable A value) {
      lifted++;
      return IdMonad.instance().of(value);
    }

    @Override
    public <A, B> Kind<IdKind.Witness, B> map(
        Function<? super A, ? extends B> f, Kind<IdKind.Witness, A> fa) {
      return IdMonad.instance().map(f, fa);
    }

    @Override
    public <A, B> Kind<IdKind.Witness, B> ap(
        Kind<IdKind.Witness, ? extends Function<A, B>> ff, Kind<IdKind.Witness, A> fa) {
      return IdMonad.instance().ap(ff, fa);
    }
  }

  private static <A> A run(Kind<IdKind.Witness, A> kind) {
    return IdKindHelper.ID.narrow(kind).value();
  }

  @Nested
  @DisplayName("A read through a traversal rebuilds nothing")
  class ReadsRebuildNothing {

    @Test
    @DisplayName("Traversals.getAll()")
    void traversalsGetAll() {
      CountingTraversal traversal = new CountingTraversal();

      assertThat(Traversals.getAll(traversal, List.of(1, 2, 3))).containsExactly(1, 2, 3);
      assertThat(traversal.rebuilds).isZero();
    }

    @Test
    @DisplayName("a TraversalPath's getAll(), count() and exists()")
    void traversalPathReads() {
      CountingTraversal traversal = new CountingTraversal();
      TraversalPath<List<Integer>, Integer> path = TraversalPath.of(traversal);

      assertThat(path.getAll(List.of(1, 2, 3))).containsExactly(1, 2, 3);
      assertThat(path.count(List.of(1, 2, 3))).isEqualTo(3);
      assertThat(path.exists(a -> a == 3, List.of(1, 2, 3))).isTrue();
      assertThat(traversal.rebuilds).isZero();
    }

    @Test
    @DisplayName("IndexedTraversals.toIndexedList(), getAll() and length()")
    void indexedTraversalsReads() {
      CountingIndexedTraversal traversal = new CountingIndexedTraversal();

      assertThat(IndexedTraversals.toIndexedList(traversal, List.of(5, 6)))
          .containsExactly(new Pair<>(0, 5), new Pair<>(1, 6));
      assertThat(IndexedTraversals.getAll(traversal, List.of(5, 6))).containsExactly(5, 6);
      assertThat(IndexedTraversals.length(traversal, List.of(5, 6))).isEqualTo(2);
      assertThat(traversal.rebuilds).isZero();
    }

    @Test
    @DisplayName("asIndexedFold() folds each focus with the monoid, in order")
    void asIndexedFold() {
      CountingIndexedTraversal traversal = new CountingIndexedTraversal();

      String folded =
          traversal
              .asIndexedFold()
              .ifoldMap(Monoids.string(), (i, a) -> i + "=" + a + ";", List.of(5, 6, 7));

      assertThat(folded).isEqualTo("0=5;1=6;2=7;");
      assertThat(traversal.rebuilds).isZero();
    }
  }

  @Nested
  @DisplayName("A fold's query stops at the focus that settles it")
  class FoldQueriesStop {

    private final RecordingFold fold = new RecordingFold();

    @Test
    @DisplayName("find() visits no focus after the first match")
    void find() {
      assertThat(fold.find(a -> a > 1, List.of(1, 2, 3, 4))).contains(2);
      assertThat(fold.visited).containsExactly(1, 2);
    }

    @Test
    @DisplayName("exists() visits no focus after the first match")
    void exists() {
      assertThat(fold.exists(a -> a > 1, List.of(1, 2, 3, 4))).isTrue();
      assertThat(fold.visited).containsExactly(1, 2);
    }

    @Test
    @DisplayName("all() visits no focus after the first that fails")
    void all() {
      assertThat(fold.all(a -> a < 2, List.of(1, 2, 3, 4))).isFalse();
      assertThat(fold.visited).containsExactly(1, 2);
    }

    @Test
    @DisplayName("preview() and isEmpty() visit only the first focus")
    void previewAndIsEmpty() {
      assertThat(fold.preview(List.of(1, 2, 3))).contains(1);
      assertThat(fold.isEmpty(List.of(1, 2, 3))).isFalse();
      assertThat(fold.visited).containsExactly(1, 1);
    }

    @Test
    @DisplayName("without an answer, a query visits every focus")
    void noAnswerVisitsEveryFocus() {
      assertThat(fold.find(a -> a > 9, List.of(1, 2))).isEmpty();
      assertThat(fold.exists(a -> a > 9, List.of(1, 2))).isFalse();
      assertThat(fold.all(a -> a < 9, List.of(1, 2))).isTrue();
      assertThat(fold.isEmpty(List.of())).isTrue();
      assertThat(fold.visited).containsExactly(1, 2, 1, 2, 1, 2);
    }

    @Test
    @DisplayName("preview() stops at a null first focus, which reads as absent")
    void previewStopsAtANullFirstFocus() {
      assertThat(fold.preview(Arrays.asList(null, 2))).isEmpty();
      assertThat(fold.visited).containsExactly((Integer) null);
    }

    @Test
    @DisplayName("find() tests a null focus, passes over it when it matches, and stops at the next")
    void findPassesOverANullMatch() {
      List<@Nullable Integer> tested = new ArrayList<>();

      Optional<Integer> found =
          fold.find(
              a -> {
                tested.add(a);
                return a == null || a == 2;
              },
              Arrays.asList(1, null, 2, 3));

      assertThat(found).contains(2);
      assertThat(tested).containsExactly(1, null, 2);
    }

    @Test
    @DisplayName(
        "a fold that catches the stop and goes on still gives the answer, untested further")
    void aFoldThatCatchesTheStop() {
      List<Integer> visited = new ArrayList<>();
      List<Integer> tested = new ArrayList<>();
      Fold<List<Integer>, Integer> catching =
          new Fold<>() {
            @Override
            public <M> M foldMap(
                Monoid<M> monoid, Function<? super Integer, ? extends M> f, List<Integer> source) {
              for (Integer a : source) {
                visited.add(a);
                try {
                  f.apply(a);
                } catch (RuntimeException caught) {
                  // carries on past every exception, the search's included
                }
              }
              return monoid.empty();
            }
          };

      boolean found =
          catching.exists(
              a -> {
                tested.add(a);
                return a == 2;
              },
              List.of(1, 2, 3));

      assertThat(found).isTrue();
      assertThat(visited).containsExactly(1, 2, 3);
      assertThat(tested).containsExactly(1, 2);
    }

    @Test
    @DisplayName("a search run inside another search's fold ends only its own")
    void aNestedSearchEndsOnlyItsOwn() {
      RecordingFold inner = new RecordingFold();
      List<String> carriedOn = new ArrayList<>();
      Fold<List<Integer>, Integer> relay =
          new Fold<>() {
            @Override
            public <M> M foldMap(
                Monoid<M> monoid, Function<? super Integer, ? extends M> f, List<Integer> source) {
              // The outer search's function runs inside the inner search, so the outer search's
              // stop passes through the inner one on its way out. Were the inner search to catch
              // it, the relay would carry on past this call.
              inner.exists(
                  a -> {
                    f.apply(a);
                    return false;
                  },
                  source);
              carriedOn.add("after the inner search");
              return monoid.empty();
            }
          };

      assertThat(relay.find(a -> a == 2, List.of(1, 2, 3))).contains(2);
      assertThat(inner.visited).containsExactly(1, 2);
      assertThat(carriedOn).isEmpty();
    }
  }

  @Nested
  @DisplayName("An indexed fold's query stops at the focus that settles it")
  class IndexedFoldQueriesStop {

    private final RecordingIndexedFold fold = new RecordingIndexedFold();

    @Test
    @DisplayName("findWithIndex() and find() visit no position after the first match")
    void find() {
      assertThat(fold.findWithIndex((i, a) -> i == 1, List.of(5, 6, 7))).contains(new Pair<>(1, 6));
      assertThat(fold.find(a -> a == 6, List.of(5, 6, 7))).contains(new Pair<>(1, 6));
      assertThat(fold.visited).containsExactly(0, 1, 0, 1);
    }

    @Test
    @DisplayName("existsWithIndex() and exists() visit no position after the first match")
    void exists() {
      assertThat(fold.existsWithIndex((i, a) -> a == 6, List.of(5, 6, 7))).isTrue();
      assertThat(fold.exists(a -> a == 6, List.of(5, 6, 7))).isTrue();
      assertThat(fold.visited).containsExactly(0, 1, 0, 1);
    }

    @Test
    @DisplayName("allWithIndex() and all() visit no position after the first that fails")
    void all() {
      assertThat(fold.allWithIndex((i, a) -> i < 1, List.of(5, 6, 7))).isFalse();
      assertThat(fold.all(a -> a < 6, List.of(5, 6, 7))).isFalse();
      assertThat(fold.visited).containsExactly(0, 1, 0, 1);
    }

    @Test
    @DisplayName("isEmpty() visits only the first position")
    void isEmpty() {
      assertThat(fold.isEmpty(List.of(5, 6, 7))).isFalse();
      assertThat(fold.isEmpty(List.of())).isTrue();
      assertThat(fold.visited).containsExactly(0);
    }
  }

  @Nested
  @DisplayName("A TraversalPath's query stops at the focus that settles it")
  class TraversalPathQueriesStop {

    private final CountingTraversal traversal = new CountingTraversal();
    private final TraversalPath<List<Integer>, Integer> path = TraversalPath.of(traversal);

    @Test
    @DisplayName("find(), exists() and all() visit no element after their answer")
    void findExistsAll() {
      assertThat(path.find(a -> a == 2, List.of(1, 2, 3))).contains(2);
      assertThat(path.exists(a -> a == 2, List.of(1, 2, 3))).isTrue();
      assertThat(path.all(a -> a < 2, List.of(1, 2, 3))).isFalse();
      assertThat(traversal.visited).containsExactly(1, 2, 1, 2, 1, 2);
    }

    @Test
    @DisplayName("preview() and isEmpty() visit only the first element")
    void previewAndIsEmpty() {
      assertThat(path.preview(List.of(1, 2, 3))).contains(1);
      assertThat(path.isEmpty(List.of(1, 2, 3))).isFalse();
      assertThat(traversal.visited).containsExactly(1, 1);
    }
  }

  @Nested
  @DisplayName("A prism or affine that matches lifts no value with of")
  class MatchesLiftNothing {

    private final Prism<Object, Integer> integer =
        Prism.of(o -> o instanceof Integer i ? Optional.of(i) : Optional.empty(), i -> i);
    private final Affine<Object, Integer> integerAffine =
        Affine.of(o -> o instanceof Integer i ? Optional.of(i) : Optional.empty(), (o, i) -> i);
    private final Lens<Box, Object> content = Lens.of(Box::content, (b, c) -> new Box(c));

    private record Box(Object content) {}

    @Test
    @DisplayName("Prism.modifyF() lifts the source with of only on a miss")
    void prismModifyF() {
      CountingId app = new CountingId();

      assertThat(run(integer.modifyF(i -> Id.of(i + 1), (Object) 1, app))).isEqualTo(2);
      assertThat(app.lifted).isZero();
      assertThat(run(integer.modifyF(i -> Id.of(i + 1), (Object) "x", app))).isEqualTo("x");
      assertThat(app.lifted).isEqualTo(1);
    }

    @Test
    @DisplayName("Affine.modifyF() lifts the source with of only on a miss")
    void affineModifyF() {
      CountingId app = new CountingId();

      assertThat(run(integerAffine.modifyF(i -> Id.of(i + 1), (Object) 1, app))).isEqualTo(2);
      assertThat(app.lifted).isZero();
      assertThat(run(integerAffine.modifyF(i -> Id.of(i + 1), (Object) "x", app))).isEqualTo("x");
      assertThat(app.lifted).isEqualTo(1);
    }

    @Test
    @DisplayName("Traversal.andThen(Prism) lifts an element with of only on a miss")
    void traversalAndThenPrism() {
      CountingId app = new CountingId();
      Traversal<Box, Integer> boxedInteger = content.asTraversal().andThen(integer);

      assertThat(run(boxedInteger.modifyF(i -> Id.of(i + 1), new Box(1), app)))
          .isEqualTo(new Box(2));
      assertThat(app.lifted).isZero();
      assertThat(run(boxedInteger.modifyF(i -> Id.of(i + 1), new Box("x"), app)))
          .isEqualTo(new Box("x"));
      assertThat(app.lifted).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "Prism.andThen(Traversal) and Affine.andThen(Traversal) lift with of only on a miss")
    void prismAndAffineThenTraversal() {
      Traversal<Integer, Integer> self =
          Lens.<Integer, Integer>of(i -> i, (i, j) -> j).asTraversal();
      CountingId app = new CountingId();

      assertThat(run(integer.andThen(self).modifyF(i -> Id.of(i + 1), (Object) 1, app)))
          .isEqualTo(2);
      assertThat(run(integerAffine.andThen(self).modifyF(i -> Id.of(i + 1), (Object) 1, app)))
          .isEqualTo(2);
      assertThat(app.lifted).isZero();
      run(integer.andThen(self).modifyF(i -> Id.of(i + 1), (Object) "x", app));
      run(integerAffine.andThen(self).modifyF(i -> Id.of(i + 1), (Object) "x", app));
      assertThat(app.lifted).isEqualTo(2);
    }

    @Test
    @DisplayName("OptionalTraverse.traverse() lifts an empty Optional with of only when empty")
    void optionalTraverse() {
      CountingId app = new CountingId();

      Optional<Integer> one = Optional.of(1);
      Optional<Integer> none = Optional.empty();

      Kind<IdKind.Witness, Kind<OptionalKind.Witness, Integer>> present =
          OptionalTraverse.INSTANCE.traverse(app, i -> Id.of(i + 1), OPTIONAL.widen(one));
      assertThat(OPTIONAL.narrow(run(present))).contains(2);
      assertThat(app.lifted).isZero();
      OptionalTraverse.INSTANCE.traverse(app, i -> Id.of(i + 1), OPTIONAL.widen(none));
      assertThat(app.lifted).isEqualTo(1);
    }
  }
}
