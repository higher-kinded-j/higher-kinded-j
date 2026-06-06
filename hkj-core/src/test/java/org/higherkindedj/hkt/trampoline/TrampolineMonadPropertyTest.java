// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import static org.higherkindedj.hkt.instances.Witnesses.trampoline;

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
 * Property-based Functor- and Monad-law verification for Trampoline. Fixtures mix completed {@code
 * done} and deferred {@code defer} trampolines; equality runs both sides through {@link
 * Trampoline#run()}.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class TrampolineMonadPropertyTest {

  private final Monad<TrampolineKind.Witness> monad = Instances.monad(trampoline());

  @Provide
  Arbitrary<Kind<TrampolineKind.Witness, Integer>> trampolineKinds() {
    return TrampolineArbitraries.trampolineKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return TrampolineArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return TrampolineArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<TrampolineKind.Witness, String>>> intToTrampolineString() {
    return TrampolineArbitraries.intToTrampolineString();
  }

  @Provide
  Arbitrary<Function<String, Kind<TrampolineKind.Witness, String>>> stringToTrampolineString() {
    return TrampolineArbitraries.stringToTrampolineString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("trampolineKinds") Kind<TrampolineKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, TrampolineLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("trampolineKinds") Kind<TrampolineKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, TrampolineLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToTrampolineString") Function<Integer, Kind<TrampolineKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, TrampolineLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("trampolineKinds") Kind<TrampolineKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, TrampolineLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("trampolineKinds") Kind<TrampolineKind.Witness, Integer> m,
      @ForAll("intToTrampolineString") Function<Integer, Kind<TrampolineKind.Witness, String>> f,
      @ForAll("stringToTrampolineString")
          Function<String, Kind<TrampolineKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, TrampolineLawFixtures.EQ);
  }
}
