// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
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
 * Property-based Functor-law verification for Optional, sharing the laws spec with
 * OptionalFunctorTest.
 */
class OptionalFunctorPropertyTest {

  private final OptionalFunctor functor = new OptionalFunctor();

  private final BiPredicate<Kind<OptionalKind.Witness, ?>, Kind<OptionalKind.Witness, ?>> eq =
      KindEquivalence.byEqualsAfter(OPTIONAL::narrow);

  @Provide
  Arbitrary<Kind<OptionalKind.Witness, Integer>> optionalKinds() {
    return Arbitraries.integers()
        .between(-1000, 1000)
        .injectNull(0.15)
        .map(
            i ->
                i == null
                    ? OPTIONAL.<Integer>widen(Optional.empty())
                    : OPTIONAL.widen(Optional.of(i)));
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void identity(@ForAll("optionalKinds") Kind<OptionalKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(functor, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void composition(
      @ForAll("optionalKinds") Kind<OptionalKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(functor, fa, f, g, eq);
  }
}
