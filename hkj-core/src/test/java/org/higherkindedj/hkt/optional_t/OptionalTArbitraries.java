// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional_t;

import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.optional_t.OptionalTKindHelper.OPTIONAL_T;

import java.util.Optional;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;

/**
 * Shared jqwik arbitraries for the OptionalT property tests (inner monad = {@code Optional}).
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code OptionalT} generator and the
 * function pools are defined once rather than copy-pasted into {@code OptionalTMonadPropertyTest}.
 */
final class OptionalTArbitraries {

  private OptionalTArbitraries() {}

  private static final MonadError<OptionalKind.Witness, Unit> OUTER =
      Instances.monadError(optional());

  private static Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> someT(int value) {
    return OPTIONAL_T.widen(OptionalT.some(OUTER, value));
  }

  private static <R> Kind<OptionalTKind.Witness<OptionalKind.Witness>, R> someTValue(R value) {
    return OPTIONAL_T.widen(OptionalT.some(OUTER, value));
  }

  private static <R> Kind<OptionalTKind.Witness<OptionalKind.Witness>, R> noneT() {
    return OPTIONAL_T.widen(OptionalT.none(OUTER));
  }

  private static <R> Kind<OptionalTKind.Witness<OptionalKind.Witness>, R> outerEmptyT() {
    Kind<OptionalKind.Witness, Optional<R>> emptyOuter = OPTIONAL.widen(Optional.empty());
    return OPTIONAL_T.widen(OptionalT.fromKind(emptyOuter));
  }

  /** Mix of Some (success), inner None, and empty-outer Optional states. */
  static Arbitrary<Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer>> optionalTKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15)
        .flatMap(
            i -> {
              if (i == null) {
                return Arbitraries.just(outerEmptyT());
              }
              if (i % 5 == 0) {
                return Arbitraries.just(noneT());
              }
              return Arbitraries.just(someT(i));
            });
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> OptionalT<Optional, String>} kleisli arrows. */
  static Arbitrary<Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>>>
      intToTKindString() {
    return Arbitraries.of(
        i -> i % 2 == 0 ? someTValue("even:" + i) : noneT(),
        i -> i > 0 ? someTValue("positive:" + i) : outerEmptyT(),
        i -> someTValue("value:" + i),
        i -> i == 0 ? noneT() : someTValue(String.valueOf(i)));
  }

  /** A small pool of {@code String -> OptionalT<Optional, String>} kleisli arrows. */
  static Arbitrary<Function<String, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>>>
      stringToTKindString() {
    return Arbitraries.of(
        s -> s.isEmpty() ? noneT() : someTValue(s.toUpperCase()),
        s -> s.length() > 3 ? someTValue("long:" + s) : outerEmptyT(),
        s -> someTValue("transformed:" + s));
  }
}
