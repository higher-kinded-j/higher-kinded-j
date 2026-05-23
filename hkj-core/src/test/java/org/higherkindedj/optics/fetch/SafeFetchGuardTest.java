// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.higherkindedj.hkt.either.Either;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SafeFetch guarded runners: railway capture of GuardViolationException")
class SafeFetchGuardTest {

  private static Fetch<Integer, Integer, Integer> twoFetches() {
    Fetch<Integer, Integer, Integer> a = Fetch.fetch(1);
    Fetch<Integer, Integer, Integer> b = Fetch.fetch(2);
    return Fetch.ap(a.map(va -> vb -> va + vb), b);
  }

  private static Function<Set<Integer>, Map<Integer, Integer>> doubler() {
    return ids -> {
      Map<Integer, Integer> out = new HashMap<>();
      ids.forEach(id -> out.put(id, id * 2));
      return out;
    };
  }

  private static BatchLoader<Integer, Integer> asyncDoubler() {
    return ids ->
        CompletableFuture.supplyAsync(
            () -> {
              Map<Integer, Integer> out = new HashMap<>();
              ids.forEach(id -> out.put(id, id * 2));
              return out;
            });
  }

  @Nested
  @DisplayName("runCachedWithGuard")
  class CachedGuard {

    @Test
    @DisplayName("a passing guard yields Either.right with the run's result")
    void passingGuardIsRight() {
      Either<Throwable, Fetch.RunResult<Integer, Integer>> outcome =
          SafeFetch.runCachedWithGuard(twoFetches(), doubler(), Guards.maxKeysPerRound(2));
      assertThat(outcome.isRight()).isTrue();
      assertThat(outcome.getRight().value()).isEqualTo(6);
    }

    @Test
    @DisplayName("a refused round yields Either.left with the GuardViolationException")
    void refusedGuardIsLeft() {
      Either<Throwable, Fetch.RunResult<Integer, Integer>> outcome =
          SafeFetch.runCachedWithGuard(twoFetches(), doubler(), Guards.maxKeysPerRound(1));
      assertThat(outcome.isLeft()).isTrue();
      assertThat(outcome.getLeft())
          .isInstanceOf(GuardViolationException.class)
          .hasMessageContaining("keysPerRound=2");
    }
  }

  @Nested
  @DisplayName("runAsyncWithGuard")
  class AsyncGuard {

    @Test
    @DisplayName("a passing guard completes normally with Either.right")
    void passingGuardIsRight() throws Exception {
      Either<Throwable, Fetch.RunResult<Integer, Integer>> outcome =
          SafeFetch.runAsyncWithGuard(
                  twoFetches(),
                  asyncDoubler(),
                  new ConcurrentHashMap<>(),
                  Guards.maxKeysPerRound(2))
              .get();
      assertThat(outcome.isRight()).isTrue();
      assertThat(outcome.getRight().value()).isEqualTo(6);
    }

    @Test
    @DisplayName("a refused round completes normally with Either.left")
    void refusedGuardIsLeft() throws Exception {
      Either<Throwable, Fetch.RunResult<Integer, Integer>> outcome =
          SafeFetch.runAsyncWithGuard(
                  twoFetches(),
                  asyncDoubler(),
                  new ConcurrentHashMap<>(),
                  Guards.maxKeysPerRound(1))
              .get();
      assertThat(outcome.isLeft()).isTrue();
      assertThat(outcome.getLeft()).isInstanceOf(GuardViolationException.class);
    }

    @Test
    @DisplayName("a synchronous failure from Guards.runAsync is captured as Either.left")
    void synchronousFailureIsLeft() throws Exception {
      // A null cache makes Guards.runAsync throw synchronously; SafeFetch should catch it
      // and surface the failure on the value channel instead of letting it propagate.
      Either<Throwable, Fetch.RunResult<Integer, Integer>> outcome =
          SafeFetch.runAsyncWithGuard(twoFetches(), asyncDoubler(), null, Guards.none()).get();
      assertThat(outcome.isLeft()).isTrue();
      assertThat(outcome.getLeft()).isInstanceOf(NullPointerException.class);
    }
  }
}
