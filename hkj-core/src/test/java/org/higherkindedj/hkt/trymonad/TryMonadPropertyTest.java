// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.instances.Witnesses.try_;

import java.util.function.Function;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;

/**
 * Property-based Functor- and Monad-law verification for Try, sharing the laws spec with
 * TryFunctorTest and TryMonadTest.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class TryMonadPropertyTest {

  private final MonadError<TryKind.Witness, Throwable> monad = Instances.monadError(try_());

  @Provide
  Arbitrary<Kind<TryKind.Witness, Integer>> tryKinds() {
    return TryArbitraries.tryKinds(100);
  }

  @Provide
  Arbitrary<Kind<TryKind.Witness, Integer>> functorTryKinds() {
    return TryArbitraries.tryKinds(1000);
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return TryArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return TryArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<TryKind.Witness, String>>> intToTryString() {
    return TryArbitraries.intToTryString();
  }

  @Provide
  Arbitrary<Function<String, Kind<TryKind.Witness, String>>> stringToTryString() {
    return TryArbitraries.stringToTryString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("functorTryKinds") Kind<TryKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, TryLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("functorTryKinds") Kind<TryKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, TryLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToTryString") Function<Integer, Kind<TryKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, TryLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("tryKinds") Kind<TryKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, TryLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("tryKinds") Kind<TryKind.Witness, Integer> m,
      @ForAll("intToTryString") Function<Integer, Kind<TryKind.Witness, String>> f,
      @ForAll("stringToTryString") Function<String, Kind<TryKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, TryLawFixtures.EQ);
  }
}
