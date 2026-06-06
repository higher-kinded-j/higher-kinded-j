// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.future;

import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;

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
 * Property-based Functor + Monad law verification for CompletableFuture, sharing the laws spec with
 * {@link CompletableFutureMonadTest}. The futures are completed, so equality is driven by joining
 * both sides.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class CompletableFutureMonadPropertyTest {

  private final Monad<CompletableFutureKind.Witness> monad = Instances.monad(completableFuture());

  @Provide
  Arbitrary<Kind<CompletableFutureKind.Witness, Integer>> futureKinds() {
    return FutureArbitraries.futureKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return FutureArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return FutureArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<CompletableFutureKind.Witness, String>>> intToFutureString() {
    return FutureArbitraries.intToFutureString();
  }

  @Provide
  Arbitrary<Function<String, Kind<CompletableFutureKind.Witness, String>>> stringToFutureString() {
    return FutureArbitraries.stringToFutureString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("futureKinds") Kind<CompletableFutureKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, FutureLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("futureKinds") Kind<CompletableFutureKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, FutureLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToFutureString")
          Function<Integer, Kind<CompletableFutureKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, FutureLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("futureKinds") Kind<CompletableFutureKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, FutureLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("futureKinds") Kind<CompletableFutureKind.Witness, Integer> m,
      @ForAll("intToFutureString") Function<Integer, Kind<CompletableFutureKind.Witness, String>> f,
      @ForAll("stringToFutureString")
          Function<String, Kind<CompletableFutureKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, FutureLawFixtures.EQ);
  }
}
