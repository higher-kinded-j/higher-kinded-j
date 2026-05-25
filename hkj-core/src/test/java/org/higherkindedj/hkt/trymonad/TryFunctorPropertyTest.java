// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

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

/** Property-based Functor-law verification for Try, sharing the laws spec with TryFunctorTest. */
class TryFunctorPropertyTest {

  private final TryFunctor functor = new TryFunctor();

  private final BiPredicate<Kind<TryKind.Witness, ?>, Kind<TryKind.Witness, ?>> eq =
      KindEquivalence.byEqualsAfter(TRY::narrow);

  // Shared exception instances so Failure equality (record-based) works under associativity.
  private static final RuntimeException FAIL_A = new IllegalStateException("a");
  private static final RuntimeException FAIL_B = new ArithmeticException("b");

  @Provide
  Arbitrary<Kind<TryKind.Witness, Integer>> tryKinds() {
    return Arbitraries.integers()
        .between(-1000, 1000)
        .injectNull(0.15)
        .map(
            i -> {
              if (i == null) return TRY.<Integer>widen(Try.failure(FAIL_A));
              if (i % 5 == 0) return TRY.<Integer>widen(Try.failure(FAIL_B));
              return TRY.widen(Try.success(i));
            });
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
  void identity(@ForAll("tryKinds") Kind<TryKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(functor, fa, eq);
  }

  @Property
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void composition(
      @ForAll("tryKinds") Kind<TryKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(functor, fa, f, g, eq);
  }
}
