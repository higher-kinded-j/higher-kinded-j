// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.higherkindedj.hkt.instances.Witnesses.list;

import java.util.function.Function;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;

/** Property-based Functor + Monad law verification for List using shared hkj-test law helpers. */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class ListMonadPropertyTest {

  private final Monad<ListKind.Witness> monad = Instances.monadZero(list());

  @Provide
  Arbitrary<Kind<ListKind.Witness, Integer>> listKinds() {
    return ListArbitraries.listKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return ListArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return ListArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<ListKind.Witness, String>>> intToListString() {
    return ListArbitraries.intToListString();
  }

  @Provide
  Arbitrary<Function<String, Kind<ListKind.Witness, String>>> stringToListString() {
    return ListArbitraries.stringToListString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("listKinds") Kind<ListKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, ListLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("listKinds") Kind<ListKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, ListLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToListString") Function<Integer, Kind<ListKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, ListLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("listKinds") Kind<ListKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, ListLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("listKinds") Kind<ListKind.Witness, Integer> m,
      @ForAll("intToListString") Function<Integer, Kind<ListKind.Witness, String>> f,
      @ForAll("stringToListString") Function<String, Kind<ListKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, ListLawFixtures.EQ);
  }
}
