// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import java.util.function.Function;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;

/** Property-based Functor- and Monad-law verification for Id. */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class IdMonadPropertyTest {

  private final Monad<IdKind.Witness> monad = IdMonad.instance();

  @Provide
  Arbitrary<Kind<IdKind.Witness, Integer>> idKinds() {
    return IdArbitraries.idKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return IdArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return IdArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<IdKind.Witness, String>>> intToIdString() {
    return IdArbitraries.intToIdString();
  }

  @Provide
  Arbitrary<Function<String, Kind<IdKind.Witness, String>>> stringToIdString() {
    return IdArbitraries.stringToIdString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("idKinds") Kind<IdKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, IdLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("idKinds") Kind<IdKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, IdLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToIdString") Function<Integer, Kind<IdKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, IdLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("idKinds") Kind<IdKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, IdLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("idKinds") Kind<IdKind.Witness, Integer> m,
      @ForAll("intToIdString") Function<Integer, Kind<IdKind.Witness, String>> f,
      @ForAll("stringToIdString") Function<String, Kind<IdKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, IdLawFixtures.EQ);
  }
}
