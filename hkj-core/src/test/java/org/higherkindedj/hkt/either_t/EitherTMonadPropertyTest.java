// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either_t;

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
import org.higherkindedj.hkt.either_t.EitherTMonadTest.TestError;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.optional.OptionalKind;

/**
 * Property-based Functor- and Monad-law verification for EitherT over Optional. Mirrors
 * EitherTMonadTest's law coverage but with jqwik-generated fixtures spanning the three transformer
 * states (Right, Left, empty-outer).
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class EitherTMonadPropertyTest {

  private final MonadError<OptionalKind.Witness, Unit> outerMonad =
      Instances.monadError(optional());
  private final MonadError<EitherTKind.Witness<OptionalKind.Witness, TestError>, TestError>
      eitherTMonad = Instances.eitherT(outerMonad);

  private final BiPredicate<
          Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, ?>,
          Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, ?>>
      eq = EitherTLawFixtures.EQ;

  @Provide
  Arbitrary<Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer>> eitherTKinds() {
    return EitherTArbitraries.eitherTKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return EitherTArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return EitherTArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>>>
      intToTKindString() {
    return EitherTArbitraries.intToTKindString();
  }

  @Provide
  Arbitrary<Function<String, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>>>
      stringToTKindString() {
    return EitherTArbitraries.stringToTKindString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(
      @ForAll("eitherTKinds")
          Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> fa) {
    FunctorLaws.assertIdentity(eitherTMonad, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("eitherTKinds")
          Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(eitherTMonad, fa, f, g, eq);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToTKindString")
          Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>> f) {
    MonadLaws.assertLeftIdentity(eitherTMonad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(
      @ForAll("eitherTKinds")
          Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> m) {
    MonadLaws.assertRightIdentity(eitherTMonad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("eitherTKinds") Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> m,
      @ForAll("intToTKindString")
          Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>> f,
      @ForAll("stringToTKindString")
          Function<String, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>> g) {
    MonadLaws.assertAssociativity(eitherTMonad, m, f, g, eq);
  }
}
