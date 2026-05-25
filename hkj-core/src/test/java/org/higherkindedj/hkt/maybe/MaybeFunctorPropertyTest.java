// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.BiPredicate;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.laws.FunctorLaws;

/**
 * Property-based Functor-law verification for {@link MaybeFunctor}, sharing the laws spec with POC
 * 1.
 */
class MaybeFunctorPropertyTest {

  private final MaybeFunctor functor = new MaybeFunctor();

  private final BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>> eq =
      KindEquivalence.byEqualsAfter(MAYBE::narrow);

  @Provide
  Arbitrary<Kind<MaybeKind.Witness, Integer>> maybeKinds() {
    return Arbitraries.integers()
        .between(-1000, 1000)
        .injectNull(0.1)
        .map(i -> i == null ? MAYBE.<Integer>widen(Maybe.nothing()) : MAYBE.widen(Maybe.just(i)));
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  @Property
  @Label("Functor identity: map(id, fa) == fa")
  void identity(@ForAll("maybeKinds") Kind<MaybeKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(functor, fa, eq);
  }

  @Property
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void composition(
      @ForAll("maybeKinds") Kind<MaybeKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(functor, fa, f, g, eq);
  }
}
