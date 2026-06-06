// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.instances.Witnesses.optional;

import java.util.function.Function;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;

/**
 * Property-based Functor- and Monad-law verification for Optional, sharing the laws spec with
 * OptionalFunctorTest and OptionalMonadTest.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class OptionalMonadPropertyTest {

  private final MonadError<OptionalKind.Witness, Unit> monad = Instances.monadError(optional());

  @Provide
  Arbitrary<Kind<OptionalKind.Witness, Integer>> optionalKinds() {
    return OptionalArbitraries.optionalKinds(100, 0.15);
  }

  @Provide
  Arbitrary<Kind<OptionalKind.Witness, Integer>> functorOptionalKinds() {
    return OptionalArbitraries.optionalKinds(1000, 0.1);
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return OptionalArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return OptionalArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<OptionalKind.Witness, String>>> intToOptionalString() {
    return OptionalArbitraries.intToOptionalString();
  }

  @Provide
  Arbitrary<Function<String, Kind<OptionalKind.Witness, String>>> stringToOptionalString() {
    return OptionalArbitraries.stringToOptionalString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("functorOptionalKinds") Kind<OptionalKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, OptionalLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("functorOptionalKinds") Kind<OptionalKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, OptionalLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToOptionalString") Function<Integer, Kind<OptionalKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, OptionalLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("optionalKinds") Kind<OptionalKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, OptionalLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("optionalKinds") Kind<OptionalKind.Witness, Integer> m,
      @ForAll("intToOptionalString") Function<Integer, Kind<OptionalKind.Witness, String>> f,
      @ForAll("stringToOptionalString") Function<String, Kind<OptionalKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, OptionalLawFixtures.EQ);
  }
}
