// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;

/** Property-based Functor + Monad law verification for List using shared hkj-test law helpers. */
class ListMonadPropertyTest {

  private final Monad<ListKind.Witness> monad = Instances.monadZero(list());

  private final BiPredicate<Kind<ListKind.Witness, ?>, Kind<ListKind.Witness, ?>> eq =
      KindEquivalence.byEqualsAfter(LIST::narrow);

  @Provide
  Arbitrary<Kind<ListKind.Witness, Integer>> listKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .list()
        .ofMinSize(0)
        .ofMaxSize(5)
        .map(l -> LIST.widen(new ArrayList<>(l)));
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  @Provide
  Arbitrary<Function<Integer, Kind<ListKind.Witness, String>>> intToListString() {
    return Arbitraries.of(
        i -> LIST.widen(List.of("a:" + i)),
        i -> LIST.widen(List.of("a:" + i, "b:" + i)),
        i -> LIST.widen(List.<String>of()),
        i -> LIST.widen(List.of(String.valueOf(i))));
  }

  @Provide
  Arbitrary<Function<String, Kind<ListKind.Witness, String>>> stringToListString() {
    return Arbitraries.of(
        s -> LIST.widen(List.of(s.toUpperCase())),
        s -> LIST.widen(List.of(s + "!", s + "?")),
        s -> LIST.widen(List.<String>of()),
        s -> LIST.widen(List.of("len:" + s.length())));
  }

  @Property(tries = 100)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("listKinds") Kind<ListKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, eq);
  }

  @Property(tries = 100)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("listKinds") Kind<ListKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, eq);
  }

  @Property(tries = 100)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToListString") Function<Integer, Kind<ListKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, eq);
  }

  @Property(tries = 100)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("listKinds") Kind<ListKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, eq);
  }

  @Property(tries = 100)
  @Label("Monad associativity")
  void associativity(
      @ForAll("listKinds") Kind<ListKind.Witness, Integer> m,
      @ForAll("intToListString") Function<Integer, Kind<ListKind.Witness, String>> f,
      @ForAll("stringToListString") Function<String, Kind<ListKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, eq);
  }
}
