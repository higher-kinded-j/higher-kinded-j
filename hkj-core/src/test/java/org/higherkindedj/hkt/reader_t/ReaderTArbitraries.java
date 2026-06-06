// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;

import java.util.Optional;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;

/**
 * Shared jqwik arbitraries for the ReaderT property tests (inner monad = {@code Optional},
 * environment = {@code String}).
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code ReaderT} generator and the
 * function pools are defined once rather than copy-pasted into {@code ReaderTMonadPropertyTest}.
 */
final class ReaderTArbitraries {

  private ReaderTArbitraries() {}

  private static final Monad<OptionalKind.Witness> OUTER = Instances.monadError(optional());

  private static <A> Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, A> readerT(A value) {
    return READER_T.widen(ReaderT.reader(OUTER, _ -> value));
  }

  private static <A> Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, A> fromEnv(
      Function<String, A> f) {
    return READER_T.widen(ReaderT.reader(OUTER, f));
  }

  private static <A> Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, A> emptyT() {
    Kind<OptionalKind.Witness, A> emptyOuter = OPTIONAL.widen(Optional.empty());
    return READER_T.widen(ReaderT.liftF(OUTER, emptyOuter));
  }

  /** Mix of constant readers, env-dependent readers, and empty-outer Optional states. */
  static Arbitrary<Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer>>
      readerTKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15)
        .flatMap(
            i -> {
              if (i == null) {
                return Arbitraries.just(emptyT());
              }
              if (i % 4 == 0) {
                return Arbitraries.just(fromEnv(env -> i + env.length()));
              }
              return Arbitraries.just(readerT(i));
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

  /** A small pool of {@code Integer -> ReaderT<Optional, String, String>} kleisli arrows. */
  static Arbitrary<
          Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>>>
      intToTKindString() {
    return Arbitraries.of(
        i -> readerT("v:" + i),
        i -> fromEnv(env -> i + ":" + env),
        i -> i == 0 ? emptyT() : readerT(String.valueOf(i)),
        i -> fromEnv(env -> "len=" + (env.length() + i)));
  }

  /** A small pool of {@code String -> ReaderT<Optional, String, String>} kleisli arrows. */
  static Arbitrary<
          Function<String, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>>>
      stringToTKindString() {
    return Arbitraries.of(
        s -> readerT(s + "!"),
        s -> fromEnv(env -> s + ":" + env),
        s -> s.isEmpty() ? emptyT() : readerT(s.toUpperCase()));
  }
}
