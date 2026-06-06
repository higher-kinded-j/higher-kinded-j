// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import java.util.function.Function;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;

/**
 * Property-based Functor- and Monad-law verification for Free over Identity. Fixtures mix completed
 * {@code pure}, deferred {@code suspend} and {@code flatMapped} programs; equality interprets both
 * sides through the Identity natural transformation and compares results.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class FreeMonadPropertyTest {

  private final Monad<FreeKind.Witness<IdentityKind.Witness>> monad = new FreeMonad<>();

  @Provide
  Arbitrary<Kind<FreeKind.Witness<IdentityKind.Witness>, Integer>> freeKinds() {
    return FreeArbitraries.freeKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return FreeArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return FreeArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<FreeKind.Witness<IdentityKind.Witness>, String>>>
      intToFreeString() {
    return FreeArbitraries.intToFreeString();
  }

  @Provide
  Arbitrary<Function<String, Kind<FreeKind.Witness<IdentityKind.Witness>, String>>>
      stringToFreeString() {
    return FreeArbitraries.stringToFreeString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(
      @ForAll("freeKinds") Kind<FreeKind.Witness<IdentityKind.Witness>, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, FreeLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("freeKinds") Kind<FreeKind.Witness<IdentityKind.Witness>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, FreeLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToFreeString")
          Function<Integer, Kind<FreeKind.Witness<IdentityKind.Witness>, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, FreeLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("freeKinds") Kind<FreeKind.Witness<IdentityKind.Witness>, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, FreeLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("freeKinds") Kind<FreeKind.Witness<IdentityKind.Witness>, Integer> m,
      @ForAll("intToFreeString")
          Function<Integer, Kind<FreeKind.Witness<IdentityKind.Witness>, String>> f,
      @ForAll("stringToFreeString")
          Function<String, Kind<FreeKind.Witness<IdentityKind.Witness>, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, FreeLawFixtures.EQ);
  }
}
