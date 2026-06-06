// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.future;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;

/**
 * Shared jqwik arbitraries for the CompletableFuture property tests.
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code CompletableFuture<Integer>}
 * generator and the function/kleisli pools are defined once. The futures are <em>completed</em>, so
 * law verification via {@code join()} is synchronous and fast.
 */
final class FutureArbitraries {

  private FutureArbitraries() {}

  /** Completed {@code CompletableFuture<Integer>} kinds over {@code [-100, 100]}. */
  static Arbitrary<Kind<CompletableFutureKind.Witness, Integer>> futureKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .map(i -> FUTURE.widen(CompletableFuture.completedFuture(i)));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> CompletableFuture<String>} kleisli arrows. */
  static Arbitrary<Function<Integer, Kind<CompletableFutureKind.Witness, String>>>
      intToFutureString() {
    return Arbitraries.of(
        i -> FUTURE.widen(CompletableFuture.completedFuture("a:" + i)),
        i -> FUTURE.widen(CompletableFuture.completedFuture("b:" + (i * 2))),
        i -> FUTURE.widen(CompletableFuture.completedFuture(String.valueOf(i))));
  }

  /** A small pool of {@code String -> CompletableFuture<String>} kleisli arrows. */
  static Arbitrary<Function<String, Kind<CompletableFutureKind.Witness, String>>>
      stringToFutureString() {
    return Arbitraries.of(
        s -> FUTURE.widen(CompletableFuture.completedFuture(s.toUpperCase())),
        s -> FUTURE.widen(CompletableFuture.completedFuture("len:" + s.length())),
        s -> FUTURE.widen(CompletableFuture.completedFuture("transformed:" + s)));
  }
}
