// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional_t;

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
 * Property-based Functor- and Monad-law verification for OptionalT over Optional. Fixtures span the
 * three transformer states: Some, inner None, and empty-outer.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class OptionalTMonadPropertyTest {

  private final MonadError<OptionalKind.Witness, Unit> outerMonad =
      Instances.monadError(optional());
  private final MonadError<OptionalTKind.Witness<OptionalKind.Witness>, Unit> optionalTMonad =
      Instances.optionalT(outerMonad);

  private final BiPredicate<
          Kind<OptionalTKind.Witness<OptionalKind.Witness>, ?>,
          Kind<OptionalTKind.Witness<OptionalKind.Witness>, ?>>
      eq = OptionalTLawFixtures.EQ;

  @Provide
  Arbitrary<Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer>> optionalTKinds() {
    return OptionalTArbitraries.optionalTKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return OptionalTArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return OptionalTArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>>>
      intToTKindString() {
    return OptionalTArbitraries.intToTKindString();
  }

  @Provide
  Arbitrary<Function<String, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>>>
      stringToTKindString() {
    return OptionalTArbitraries.stringToTKindString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(
      @ForAll("optionalTKinds") Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> fa) {
    FunctorLaws.assertIdentity(optionalTMonad, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("optionalTKinds") Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(optionalTMonad, fa, f, g, eq);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToTKindString")
          Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>> f) {
    MonadLaws.assertLeftIdentity(optionalTMonad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(
      @ForAll("optionalTKinds") Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> m) {
    MonadLaws.assertRightIdentity(optionalTMonad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("optionalTKinds") Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> m,
      @ForAll("intToTKindString")
          Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>> f,
      @ForAll("stringToTKindString")
          Function<String, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>> g) {
    MonadLaws.assertAssociativity(optionalTMonad, m, f, g, eq);
  }
}
