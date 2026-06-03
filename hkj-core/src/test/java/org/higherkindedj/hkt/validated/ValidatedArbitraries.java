// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.jspecify.annotations.Nullable;

/**
 * Shared jqwik arbitraries for the Validated property tests.
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code Validated} generator, the
 * integer-to-string function pool and the error-accumulating {@link #LIST_SEMIGROUP} are defined
 * once rather than copy-pasted across {@code ValidatedFunctorPropertyTest}, {@code
 * ValidatedApplicativePropertyTest} and {@code ValidatedSelectivePropertyTest}.
 */
final class ValidatedArbitraries {

  private ValidatedArbitraries() {}

  /** List-concatenation semigroup, so combined Invalid errors accumulate into a single list. */
  static final Semigroup<List<String>> LIST_SEMIGROUP = Semigroups.list();

  /**
   * {@code Validated<List<String>, Integer>} kinds: mostly {@code Valid(i)}, with injected nulls
   * and every multiple of five collapsed to {@code Invalid}, so both inhabitants are exercised.
   */
  static Arbitrary<Kind<ValidatedKind.Witness<List<String>>, Integer>> validatedKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15)
        .map(ValidatedArbitraries::toValidatedKind);
  }

  private static Kind<ValidatedKind.Witness<List<String>>, Integer> toValidatedKind(
      @Nullable Integer i) {
    if (i == null) {
      return VALIDATED.widen(Validated.invalid(List.of("null")));
    }
    if (i % 5 == 0) {
      return VALIDATED.widen(Validated.invalid(List.of("err:" + i)));
    }
    return VALIDATED.widen(Validated.valid(i));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }
}
