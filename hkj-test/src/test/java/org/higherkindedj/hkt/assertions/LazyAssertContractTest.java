// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.higherkindedj.hkt.lazy.Lazy;
import org.junit.jupiter.api.DisplayName;

/** Coverage contract for {@link LazyAssert}. See {@link AssertContract}. */
@DisplayName("LazyAssert contract")
class LazyAssertContractTest extends AssertContract<Supplier<Lazy<Integer>>, LazyAssert<Integer>> {

  // Each subject is a Supplier<Lazy<Integer>> so each test gets a fresh Lazy
  // (forcing one Lazy mutates its state and would taint subsequent rows).
  private static final Supplier<Lazy<Integer>> NOW = () -> Lazy.now(42);
  private static final Supplier<Lazy<Integer>> NOW_99 = () -> Lazy.now(99);
  private static final Supplier<Lazy<Integer>> DEFERRED = () -> Lazy.defer(() -> 42);
  private static final Supplier<Lazy<Integer>> DEFERRED_99 = () -> Lazy.defer(() -> 99);
  private static final Supplier<Lazy<Integer>> DEFERRED_FAILS =
      () ->
          Lazy.defer(
              () -> {
                throw new IllegalStateException("forced-failure");
              });
  // Pre-failed lazy: evaluated once, exception cached.
  private static final Supplier<Lazy<Integer>> ALREADY_FAILED =
      () -> {
        Lazy<Integer> l = DEFERRED_FAILS.get();
        try {
          l.force();
        } catch (Throwable ignored) {
        }
        return l;
      };

  @Override
  protected Function<Supplier<Lazy<Integer>>, LazyAssert<Integer>> entry() {
    return s -> LazyAssert.assertThatLazy(s.get());
  }

  @Override
  protected Stream<Row<Supplier<Lazy<Integer>>, LazyAssert<Integer>>> rows() {
    return Stream.of(
        row("isEvaluated", NOW, DEFERRED, LazyAssert::isEvaluated),
        row("isNotEvaluated", DEFERRED, NOW, LazyAssert::isNotEvaluated),
        row("hasValue match", NOW, NOW_99, a -> a.hasValue(42)),
        row("whenForcedHasValue match", DEFERRED, DEFERRED_99, a -> a.whenForcedHasValue(42)),
        passOnly(
            "whenForcedHasValueSatisfying passes",
            DEFERRED,
            a -> a.whenForcedHasValueSatisfying(v -> {})),
        failOnly(
            "whenForcedHasValueSatisfying inner fails",
            DEFERRED,
            a ->
                a.whenForcedHasValueSatisfying(
                    v -> {
                      throw new AssertionError("inner");
                    })),
        passOnly("whenForcedHasValueNonNull", DEFERRED, LazyAssert::whenForcedHasValueNonNull),
        row(
            "whenForcedThrows match",
            DEFERRED_FAILS,
            DEFERRED,
            a -> a.whenForcedThrows(IllegalStateException.class)),
        row(
            "whenForcedThrowsWithMessage match",
            DEFERRED_FAILS,
            DEFERRED,
            a -> a.whenForcedThrowsWithMessage("forced-failure")),
        row("hasFailed", ALREADY_FAILED, NOW, LazyAssert::hasFailed),
        row("hasNotFailed", NOW, ALREADY_FAILED, LazyAssert::hasNotFailed));
  }
}
