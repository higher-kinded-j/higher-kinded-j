// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.higherkindedj.hkt.instances.Witnesses.lazy;

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

/** Property-based Functor- and Monad-law verification for Lazy. */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class LazyMonadPropertyTest {

  private final Monad<LazyKind.Witness> monad = Instances.monad(lazy());

  @Provide
  Arbitrary<Kind<LazyKind.Witness, Integer>> lazyKinds() {
    return LazyArbitraries.lazyKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return LazyArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return LazyArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<LazyKind.Witness, String>>> intToLazyString() {
    return LazyArbitraries.intToLazyString();
  }

  @Provide
  Arbitrary<Function<String, Kind<LazyKind.Witness, String>>> stringToLazyString() {
    return LazyArbitraries.stringToLazyString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("lazyKinds") Kind<LazyKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, LazyLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("lazyKinds") Kind<LazyKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, LazyLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToLazyString") Function<Integer, Kind<LazyKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, LazyLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("lazyKinds") Kind<LazyKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, LazyLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("lazyKinds") Kind<LazyKind.Witness, Integer> m,
      @ForAll("intToLazyString") Function<Integer, Kind<LazyKind.Witness, String>> f,
      @ForAll("stringToLazyString") Function<String, Kind<LazyKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, LazyLawFixtures.EQ);
  }
}
