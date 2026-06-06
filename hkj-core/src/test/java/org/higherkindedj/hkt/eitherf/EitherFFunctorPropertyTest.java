// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherf;

import static org.higherkindedj.hkt.instances.Witnesses.maybe;

import java.util.function.Function;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityMonad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.maybe.MaybeKind;

/**
 * Property-based Functor-law verification for {@code EitherF} over {@code Identity}/{@code Maybe}.
 * Fixtures span both {@code Left} and {@code Right}; equality narrows both sides through the {@link
 * EitherFKindHelper}.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class EitherFFunctorPropertyTest {

  private final EitherFFunctor<IdentityKind.Witness, MaybeKind.Witness> functor =
      EitherFFunctor.of(IdentityMonad.INSTANCE, Instances.monadError(maybe()));

  @Provide
  Arbitrary<Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, Integer>>
      eitherFKinds() {
    return EitherFArbitraries.eitherFKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return EitherFArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return EitherFArbitraries.stringToInt();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(
      @ForAll("eitherFKinds")
          Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, Integer> fa) {
    FunctorLaws.assertIdentity(functor, fa, EitherFLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("eitherFKinds")
          Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(functor, fa, f, g, EitherFLawFixtures.EQ);
  }
}
