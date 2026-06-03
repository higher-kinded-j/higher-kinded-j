// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

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
 * Property-based Functor-law verification for Either, sharing the laws spec with EitherFunctorTest.
 */
// jqwik invokes the @Provide methods reflectively via @ForAll(name); IntelliJ cannot see those
// uses.
@SuppressWarnings("unused")
class EitherFunctorPropertyTest {

  private final EitherFunctor<String> functor = EitherFunctor.instance();

  private final BiPredicate<
          Kind<EitherKind.Witness<String>, ?>, Kind<EitherKind.Witness<String>, ?>>
      eq = KindEquivalence.byEqualsAfter(EITHER::narrow);

  @Provide
  Arbitrary<Kind<EitherKind.Witness<String>, Integer>> eitherKinds() {
    return EitherArbitraries.eitherKinds(1000);
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return EitherArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  @Property
  @Label("Functor identity: map(id, fa) == fa")
  void identity(@ForAll("eitherKinds") Kind<EitherKind.Witness<String>, Integer> fa) {
    FunctorLaws.assertIdentity(functor, fa, eq);
  }

  @Property
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void composition(
      @ForAll("eitherKinds") Kind<EitherKind.Witness<String>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(functor, fa, f, g, eq);
  }
}
