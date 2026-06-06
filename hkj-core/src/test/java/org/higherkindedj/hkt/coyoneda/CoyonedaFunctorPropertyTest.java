// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.coyoneda;

import java.util.function.Function;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.maybe.MaybeKind;

/**
 * Property-based Functor-law verification for Coyoneda over Maybe. Fixtures span {@code Just} and
 * {@code Nothing}; equality lowers both sides through the underlying functor.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class CoyonedaFunctorPropertyTest {

  private final CoyonedaFunctor<MaybeKind.Witness> functor = new CoyonedaFunctor<>();

  @Provide
  Arbitrary<Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer>> coyonedaKinds() {
    return CoyonedaArbitraries.coyonedaKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return CoyonedaArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return CoyonedaArbitraries.stringToInt();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(
      @ForAll("coyonedaKinds") Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer> fa) {
    FunctorLaws.assertIdentity(functor, fa, CoyonedaLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("coyonedaKinds") Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(functor, fa, f, g, CoyonedaLawFixtures.EQ);
  }
}
