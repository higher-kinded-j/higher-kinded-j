// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe_t;

import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.maybe_t.MaybeTKindHelper.MAYBE_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.optional.OptionalKind;

/**
 * Shared jqwik arbitraries for the MaybeT property tests (inner monad = {@code Optional}).
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code MaybeT} generator and the
 * function pools are defined once rather than copy-pasted into {@code MaybeTMonadPropertyTest}.
 */
final class MaybeTArbitraries {

  private MaybeTArbitraries() {}

  private static final MonadError<OptionalKind.Witness, Unit> OUTER =
      Instances.monadError(optional());

  private static <R> Kind<MaybeTKind.Witness<OptionalKind.Witness>, R> justT(R value) {
    return MAYBE_T.widen(MaybeT.just(OUTER, value));
  }

  private static <R> Kind<MaybeTKind.Witness<OptionalKind.Witness>, R> nothingT() {
    return MAYBE_T.widen(MaybeT.nothing(OUTER));
  }

  private static <R> Kind<MaybeTKind.Witness<OptionalKind.Witness>, R> emptyT() {
    Kind<OptionalKind.Witness, Maybe<R>> emptyOuter = OPTIONAL.widen(Optional.empty());
    return MAYBE_T.widen(MaybeT.fromKind(emptyOuter));
  }

  /** Mix of Just (success), inner Nothing, and empty-outer Optional states. */
  static Arbitrary<Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>> maybeTKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15)
        .flatMap(
            i -> {
              if (i == null) {
                return Arbitraries.just(emptyT());
              }
              if (i % 5 == 0) {
                return Arbitraries.just(nothingT());
              }
              return Arbitraries.just(justT(i));
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

  /** A small pool of {@code Integer -> MaybeT<Optional, String>} kleisli arrows. */
  static Arbitrary<Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>>>
      intToTKindString() {
    return Arbitraries.of(
        i -> i % 2 == 0 ? justT("even:" + i) : nothingT(),
        i -> i > 0 ? justT("positive:" + i) : emptyT(),
        i -> justT("value:" + i),
        i -> i == 0 ? nothingT() : justT(String.valueOf(i)));
  }

  /** A small pool of {@code String -> MaybeT<Optional, String>} kleisli arrows. */
  static Arbitrary<Function<String, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>>>
      stringToTKindString() {
    return Arbitraries.of(
        s -> s.isEmpty() ? nothingT() : justT(s.toUpperCase()),
        s -> s.length() > 3 ? justT("long:" + s) : emptyT(),
        s -> justT("transformed:" + s));
  }
}
