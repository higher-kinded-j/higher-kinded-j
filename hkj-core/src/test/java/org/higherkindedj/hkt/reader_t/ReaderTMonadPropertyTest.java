// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

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
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.optional.OptionalKind;

/**
 * Property-based Functor- and Monad-law verification for ReaderT over Optional. Readers are
 * functions, so equality runs both sides against a fixed environment. Fixtures mix value-only,
 * env-dependent, and empty-outer states.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class ReaderTMonadPropertyTest {

  private final Monad<OptionalKind.Witness> outerMonad = Instances.monadError(optional());
  private final Monad<ReaderTKind.Witness<OptionalKind.Witness, String>> readerTMonad =
      Instances.readerT(outerMonad);

  private final BiPredicate<
          Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, ?>,
          Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, ?>>
      eq = ReaderTLawFixtures.EQ;

  @Provide
  Arbitrary<Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer>> readerTKinds() {
    return ReaderTArbitraries.readerTKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return ReaderTArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return ReaderTArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>>>
      intToTKindString() {
    return ReaderTArbitraries.intToTKindString();
  }

  @Provide
  Arbitrary<Function<String, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>>>
      stringToTKindString() {
    return ReaderTArbitraries.stringToTKindString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(
      @ForAll("readerTKinds") Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> fa) {
    FunctorLaws.assertIdentity(readerTMonad, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("readerTKinds") Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(readerTMonad, fa, f, g, eq);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToTKindString")
          Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>> f) {
    MonadLaws.assertLeftIdentity(readerTMonad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(
      @ForAll("readerTKinds") Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> m) {
    MonadLaws.assertRightIdentity(readerTMonad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("readerTKinds") Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> m,
      @ForAll("intToTKindString")
          Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>> f,
      @ForAll("stringToTKindString")
          Function<String, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>> g) {
    MonadLaws.assertAssociativity(readerTMonad, m, f, g, eq);
  }
}
