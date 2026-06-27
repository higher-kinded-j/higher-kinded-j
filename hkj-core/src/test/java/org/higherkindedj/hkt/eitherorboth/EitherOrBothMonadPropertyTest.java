// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import java.util.function.Function;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;

/**
 * Property-based Functor + Monad law verification for {@link EitherOrBoth}, reproducing the design
 * POC at scale: generators and kleisli arrows span {@code Left}/{@code Right}/{@code Both}, so the
 * accumulating {@code Both} combinations are exercised. Associativity holds because the left
 * semigroup (string concatenation) is associative.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class EitherOrBothMonadPropertyTest {

  private final Monad<EitherOrBothKind.Witness<String>> monad =
      Instances.eitherOrBoth(Semigroups.string());

  @Provide
  Arbitrary<Kind<EitherOrBothKind.Witness<String>, Integer>> eobKinds() {
    return EitherOrBothArbitraries.eobKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return EitherOrBothArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return EitherOrBothArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<EitherOrBothKind.Witness<String>, String>>> intToEobString() {
    return EitherOrBothArbitraries.intToEobString();
  }

  @Provide
  Arbitrary<Function<String, Kind<EitherOrBothKind.Witness<String>, String>>> stringToEobString() {
    return EitherOrBothArbitraries.stringToEobString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("eobKinds") Kind<EitherOrBothKind.Witness<String>, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, EitherOrBothLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("eobKinds") Kind<EitherOrBothKind.Witness<String>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, EitherOrBothLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToEobString")
          Function<Integer, Kind<EitherOrBothKind.Witness<String>, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, EitherOrBothLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("eobKinds") Kind<EitherOrBothKind.Witness<String>, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, EitherOrBothLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("eobKinds") Kind<EitherOrBothKind.Witness<String>, Integer> m,
      @ForAll("intToEobString") Function<Integer, Kind<EitherOrBothKind.Witness<String>, String>> f,
      @ForAll("stringToEobString")
          Function<String, Kind<EitherOrBothKind.Witness<String>, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, EitherOrBothLawFixtures.EQ);
  }
}
