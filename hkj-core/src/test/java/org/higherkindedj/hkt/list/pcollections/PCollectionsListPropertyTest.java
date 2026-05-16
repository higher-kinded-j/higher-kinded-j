// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list.pcollections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.List;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.list.ListTraverse;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

/**
 * Property-based tests confirming that the standard {@link ListMonad} / {@link ListTraverse}
 * Functor, Monad, and Foldable laws continue to hold when the underlying {@link java.util.List} is
 * a PCollections {@link PVector}.
 *
 * <p>This is the property-test counterpart to {@link PCollectionsListIntegrationTest}: it exercises
 * the same compatibility hypothesis across many randomised inputs.
 */
class PCollectionsListPropertyTest {

  private final MonadZero<ListKind.Witness> monad = Instances.monadZero(list());
  private final ListTraverse traverse = ListTraverse.INSTANCE;

  // ---------------------------------------------------------------------
  // Arbitraries
  // ---------------------------------------------------------------------

  @Provide
  Arbitrary<PVector<Integer>> pvectorInts() {
    return Arbitraries.integers()
        .between(-50, 50)
        .list()
        .ofMinSize(0)
        .ofMaxSize(20)
        .map(TreePVector::from);
  }

  @Provide
  Arbitrary<Function<Integer, Integer>> intToInt() {
    return Arbitraries.of(
        i -> i + 1, i -> i * 2, i -> -i, i -> i % 7, Function.<Integer>identity());
  }

  @Provide
  Arbitrary<Function<Integer, Kind<ListKind.Witness, Integer>>> intToKindList() {
    return Arbitraries.of(
        (Function<Integer, Kind<ListKind.Witness, Integer>>)
            i -> LIST.widen(TreePVector.from(List.of(i, i + 1))),
        i -> LIST.widen(TreePVector.from(List.of(i * 10))),
        i -> LIST.widen(TreePVector.<Integer>empty()),
        i -> LIST.widen(TreePVector.from(List.of(i, -i, i * i))));
  }

  @Provide
  Arbitrary<Function<Integer, Kind<ListKind.Witness, Integer>>> intToKindListSecondary() {
    return Arbitraries.of(
        (Function<Integer, Kind<ListKind.Witness, Integer>>)
            i -> LIST.widen(TreePVector.from(List.of(i + 100))),
        i -> LIST.widen(TreePVector.from(List.of(i, i))),
        i -> LIST.widen(TreePVector.<Integer>empty()));
  }

  // ---------------------------------------------------------------------
  // Functor laws
  // ---------------------------------------------------------------------

  @Property
  @Label("Functor identity: map(id, pv) = pv")
  void functorIdentity(@ForAll("pvectorInts") PVector<Integer> pv) {
    Kind<ListKind.Witness, Integer> kind = LIST.widen(pv);

    Kind<ListKind.Witness, Integer> mapped = monad.map(Function.identity(), kind);

    assertThat(LIST.narrow(mapped)).containsExactlyElementsOf(pv);
  }

  @Property
  @Label("Functor composition: map(g . f, pv) = map(g, map(f, pv))")
  void functorComposition(
      @ForAll("pvectorInts") PVector<Integer> pv,
      @ForAll("intToInt") Function<Integer, Integer> f,
      @ForAll("intToInt") Function<Integer, Integer> g) {
    Kind<ListKind.Witness, Integer> kind = LIST.widen(pv);

    Kind<ListKind.Witness, Integer> lhs = monad.map(f.andThen(g), kind);
    Kind<ListKind.Witness, Integer> rhs = monad.map(g, monad.map(f, kind));

    assertThat(LIST.narrow(lhs)).containsExactlyElementsOf(LIST.narrow(rhs));
  }

  // ---------------------------------------------------------------------
  // Monad laws
  // ---------------------------------------------------------------------

  @Property
  @Label("Monad left identity: flatMap(of(a), f) = f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToKindList") Function<Integer, Kind<ListKind.Witness, Integer>> f) {

    Kind<ListKind.Witness, Integer> lhs = monad.flatMap(f, monad.of(value));
    Kind<ListKind.Witness, Integer> rhs = f.apply(value);

    assertThat(LIST.narrow(lhs)).containsExactlyElementsOf(LIST.narrow(rhs));
  }

  @Property
  @Label("Monad right identity: flatMap(pv, of) = pv")
  void rightIdentity(@ForAll("pvectorInts") PVector<Integer> pv) {
    Kind<ListKind.Witness, Integer> kind = LIST.widen(pv);

    Kind<ListKind.Witness, Integer> result = monad.flatMap(monad::of, kind);

    assertThat(LIST.narrow(result)).containsExactlyElementsOf(pv);
  }

  @Property
  @Label("Monad associativity: flatMap(flatMap(pv, f), g) = flatMap(pv, x -> flatMap(f(x), g))")
  void associativity(
      @ForAll("pvectorInts") @Size(max = 8) PVector<Integer> pv,
      @ForAll("intToKindList") Function<Integer, Kind<ListKind.Witness, Integer>> f,
      @ForAll("intToKindListSecondary") Function<Integer, Kind<ListKind.Witness, Integer>> g) {
    Kind<ListKind.Witness, Integer> kind = LIST.widen(pv);

    Kind<ListKind.Witness, Integer> lhs = monad.flatMap(g, monad.flatMap(f, kind));
    Kind<ListKind.Witness, Integer> rhs = monad.flatMap(x -> monad.flatMap(g, f.apply(x)), kind);

    assertThat(LIST.narrow(lhs)).containsExactlyElementsOf(LIST.narrow(rhs));
  }

  // ---------------------------------------------------------------------
  // Foldable laws
  // ---------------------------------------------------------------------

  @Property
  @Label("foldMap with sum monoid equals java.util.stream sum")
  void foldMapSumMatchesStream(@ForAll("pvectorInts") PVector<Integer> pv) {
    Kind<ListKind.Witness, Integer> kind = LIST.widen(pv);

    int viaFoldMap = traverse.foldMap(Monoids.integerAddition(), i -> i, kind);
    int viaStream = pv.stream().mapToInt(Integer::intValue).sum();

    assertThat(viaFoldMap).isEqualTo(viaStream);
  }

  @Property
  @Label(
      "foldMap is a monoid homomorphism: foldMap(f) of (xs ++ ys) = foldMap(f, xs) <> foldMap(f, ys)")
  void foldMapMonoidHomomorphism(
      @ForAll("pvectorInts") PVector<Integer> xs, @ForAll("pvectorInts") PVector<Integer> ys) {
    PVector<Integer> combined = xs.plusAll(ys);

    int lhs = traverse.foldMap(Monoids.integerAddition(), i -> i, LIST.widen(combined));
    int rhs =
        traverse.foldMap(Monoids.integerAddition(), i -> i, LIST.widen(xs))
            + traverse.foldMap(Monoids.integerAddition(), i -> i, LIST.widen(ys));

    assertThat(lhs).isEqualTo(rhs);
  }
}
