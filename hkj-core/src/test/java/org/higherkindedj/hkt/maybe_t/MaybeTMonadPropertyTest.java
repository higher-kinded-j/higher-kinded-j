// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe_t;

import static org.higherkindedj.hkt.instances.Witnesses.optional;

import java.util.function.BiPredicate;
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
import org.higherkindedj.hkt.optional.OptionalKind;

/**
 * Property-based Functor- and Monad-law verification for MaybeT over Optional. Fixtures span the
 * three transformer states: Just, Nothing, and empty-outer.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class MaybeTMonadPropertyTest {

  private final MonadError<OptionalKind.Witness, Unit> outerMonad =
      Instances.monadError(optional());
  private final MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Unit> maybeTMonad =
      Instances.maybeT(outerMonad);

  private final BiPredicate<
          Kind<MaybeTKind.Witness<OptionalKind.Witness>, ?>,
          Kind<MaybeTKind.Witness<OptionalKind.Witness>, ?>>
      eq = MaybeTLawFixtures.EQ;

  @Provide
  Arbitrary<Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>> maybeTKinds() {
    return MaybeTArbitraries.maybeTKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return MaybeTArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return MaybeTArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>>>
      intToTKindString() {
    return MaybeTArbitraries.intToTKindString();
  }

  @Provide
  Arbitrary<Function<String, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>>>
      stringToTKindString() {
    return MaybeTArbitraries.stringToTKindString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(
      @ForAll("maybeTKinds") Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> fa) {
    FunctorLaws.assertIdentity(maybeTMonad, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("maybeTKinds") Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(maybeTMonad, fa, f, g, eq);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToTKindString")
          Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> f) {
    MonadLaws.assertLeftIdentity(maybeTMonad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(
      @ForAll("maybeTKinds") Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> m) {
    MonadLaws.assertRightIdentity(maybeTMonad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("maybeTKinds") Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> m,
      @ForAll("intToTKindString")
          Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> f,
      @ForAll("stringToTKindString")
          Function<String, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> g) {
    MonadLaws.assertAssociativity(maybeTMonad, m, f, g, eq);
  }
}
