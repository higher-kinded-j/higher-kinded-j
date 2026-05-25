// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.laws.FunctorLaws;

/** Property-based Functor-law verification for Validated. */
class ValidatedFunctorPropertyTest {

  private final Semigroup<List<String>> listSemigroup =
      (a, b) -> {
        List<String> combined = new ArrayList<>(a);
        combined.addAll(b);
        return combined;
      };

  private final Functor<ValidatedKind.Witness<List<String>>> functor =
      ValidatedMonad.instance(listSemigroup);

  private final BiPredicate<
          Kind<ValidatedKind.Witness<List<String>>, ?>,
          Kind<ValidatedKind.Witness<List<String>>, ?>>
      eq = KindEquivalence.byEqualsAfter(VALIDATED::narrow);

  @Provide
  Arbitrary<Kind<ValidatedKind.Witness<List<String>>, Integer>> validatedKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15)
        .map(
            i ->
                i == null
                    ? VALIDATED.<List<String>, Integer>widen(Validated.invalid(List.of("null")))
                    : (i % 5 == 0
                        ? VALIDATED.<List<String>, Integer>widen(
                            Validated.invalid(List.of("err:" + i)))
                        : VALIDATED.<List<String>, Integer>widen(Validated.valid(i))));
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
  void identity(@ForAll("validatedKinds") Kind<ValidatedKind.Witness<List<String>>, Integer> fa) {
    FunctorLaws.assertIdentity(functor, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void composition(
      @ForAll("validatedKinds") Kind<ValidatedKind.Witness<List<String>>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(functor, fa, f, g, eq);
  }
}
