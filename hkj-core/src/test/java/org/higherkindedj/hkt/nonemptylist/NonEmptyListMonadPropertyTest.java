// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import static org.higherkindedj.hkt.instances.Witnesses.nonEmptyList;

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

/**
 * Property-based Functor + Monad law verification for {@link NonEmptyList}, using the shared
 * hkj-test law helpers.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class NonEmptyListMonadPropertyTest {

  private final Monad<NonEmptyListKind.Witness> monad = Instances.monad(nonEmptyList());

  @Provide
  Arbitrary<Kind<NonEmptyListKind.Witness, Integer>> nelKinds() {
    return NonEmptyListArbitraries.nonEmptyListKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return NonEmptyListArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return NonEmptyListArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<NonEmptyListKind.Witness, String>>> intToNelString() {
    return NonEmptyListArbitraries.intToNelString();
  }

  @Provide
  Arbitrary<Function<String, Kind<NonEmptyListKind.Witness, String>>> stringToNelString() {
    return NonEmptyListArbitraries.stringToNelString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("nelKinds") Kind<NonEmptyListKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, NonEmptyListLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("nelKinds") Kind<NonEmptyListKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, NonEmptyListLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToNelString") Function<Integer, Kind<NonEmptyListKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, NonEmptyListLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("nelKinds") Kind<NonEmptyListKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, NonEmptyListLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("nelKinds") Kind<NonEmptyListKind.Witness, Integer> m,
      @ForAll("intToNelString") Function<Integer, Kind<NonEmptyListKind.Witness, String>> f,
      @ForAll("stringToNelString") Function<String, Kind<NonEmptyListKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, NonEmptyListLawFixtures.EQ);
  }
}
