// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

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
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.laws.FunctorLaws;

/** Property-based Functor-law verification for Validated. */
// jqwik invokes the @Provide methods reflectively via @ForAll(name); IntelliJ cannot see those
// uses.
@SuppressWarnings("unused")
class ValidatedFunctorPropertyTest {

  private final Functor<ValidatedKind.Witness<List<String>>> functor =
      ValidatedMonad.instance(ValidatedArbitraries.LIST_SEMIGROUP);

  private final BiPredicate<
          Kind<ValidatedKind.Witness<List<String>>, ?>,
          Kind<ValidatedKind.Witness<List<String>>, ?>>
      eq = KindEquivalence.byEqualsAfter(VALIDATED::narrow);

  @Provide
  Arbitrary<Kind<ValidatedKind.Witness<List<String>>, Integer>> validatedKinds() {
    return ValidatedArbitraries.validatedKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return ValidatedArbitraries.intToString();
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
