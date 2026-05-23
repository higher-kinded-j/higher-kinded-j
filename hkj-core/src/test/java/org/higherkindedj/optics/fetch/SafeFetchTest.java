// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.fetch.FetchKindHelper.FETCH;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Capability A, slice 1 specification: the railway-safe runners never throw, and report the run's
 * outcome on the value channel as an {@link Either}.
 */
@DisplayName("SafeFetch: railway-safe runners")
class SafeFetchTest {

  @Nested
  @DisplayName("runCached")
  class RunCached {

    @Test
    @DisplayName("a successful run is Either.right(result)")
    void successIsRight() {
      Fetch<String, String, String> program = Fetch.fetch("k");

      Either<Throwable, Fetch.RunResult<String, String>> outcome =
          SafeFetch.runCached(program, keys -> Map.of("k", "V"));

      assertThat(outcome.isRight()).isTrue();
      assertThat(outcome.getRight().value()).isEqualTo("V");
    }

    @Test
    @DisplayName("a resolver exception is captured as Either.left, not thrown")
    void resolverExceptionIsLeft() {
      Fetch<String, String, String> program = Fetch.fetch("k");

      Either<Throwable, Fetch.RunResult<String, String>> outcome =
          SafeFetch.runCached(
              program,
              keys -> {
                throw new IllegalStateException("backend down");
              });

      assertThat(outcome.isLeft()).isTrue();
      assertThat(outcome.getLeft())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("backend down");
    }

    @Test
    @DisplayName("a missing key is captured as Either.left(MissingKeyException)")
    void missingKeyIsLeft() {
      Fetch<String, String, String> program = Fetch.fetch("k");

      Either<Throwable, Fetch.RunResult<String, String>> outcome =
          SafeFetch.runCached(program, keys -> Map.of());

      assertThat(outcome.isLeft()).isTrue();
      assertThat(outcome.getLeft()).isInstanceOf(MissingKeyException.class);
    }
  }

  @Nested
  @DisplayName("runAsync")
  class RunAsync {

    @Test
    @DisplayName("a successful run completes with Either.right(result)")
    void successIsRight() throws Exception {
      Fetch<String, String, String> program = Fetch.fetch("k");
      BatchLoader<String, String> loader =
          keys -> CompletableFuture.completedFuture(Map.of("k", "V"));

      Either<Throwable, Fetch.RunResult<String, String>> outcome =
          SafeFetch.runAsync(program, loader, new ConcurrentHashMap<>()).get();

      assertThat(outcome.isRight()).isTrue();
      assertThat(outcome.getRight().value()).isEqualTo("V");
    }

    @Test
    @DisplayName("a failed loader future yields a completed future of Either.left")
    void loaderFailureIsLeft() throws Exception {
      Fetch<String, String, String> program = Fetch.fetch("k");
      BatchLoader<String, String> failing =
          keys -> CompletableFuture.failedFuture(new IllegalStateException("loader down"));

      Either<Throwable, Fetch.RunResult<String, String>> outcome =
          SafeFetch.runAsync(program, failing, new ConcurrentHashMap<>()).get();

      assertThat(outcome.isLeft()).isTrue();
      assertThat(outcome.getLeft())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("loader down");
    }

    @Test
    @DisplayName("a loader that throws synchronously yields Either.left, not a thrown exception")
    void loaderSynchronousThrowIsLeft() throws Exception {
      Fetch<String, String, String> program = Fetch.fetch("k");
      BatchLoader<String, String> throwing =
          keys -> {
            throw new IllegalStateException("loader exploded");
          };

      Either<Throwable, Fetch.RunResult<String, String>> outcome =
          SafeFetch.runAsync(program, throwing, new ConcurrentHashMap<>()).get();

      assertThat(outcome.isLeft()).isTrue();
      assertThat(outcome.getLeft()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a CompletionException with no cause is surfaced unchanged")
    void completionExceptionWithoutCauseSurfacedAsIs() throws Exception {
      final class CauselessCompletion extends java.util.concurrent.CompletionException {
        CauselessCompletion() {
          super("opaque failure", null);
        }
      }
      CauselessCompletion failure = new CauselessCompletion();
      Fetch<String, String, String> program = Fetch.fetch("k");
      BatchLoader<String, String> failing = keys -> CompletableFuture.failedFuture(failure);

      Either<Throwable, Fetch.RunResult<String, String>> outcome =
          SafeFetch.runAsync(program, failing, new ConcurrentHashMap<>()).get();

      assertThat(outcome.isLeft()).isTrue();
      assertThat(outcome.getLeft()).isSameAs(failure);
    }
  }

  @Nested
  @DisplayName("per-key railway values")
  class PerKey {

    /**
     * Folds a list of keys into one Fetch program collecting a per-key {@code Either} for each.
     * This is what a same-type optic traversal does internally; building it directly avoids the
     * traversal's type-preservation constraint (a traversal over {@code List<K>} can only fetch
     * {@code K -> K}, so heterogeneous {@code K -> Either<E, V>} fetches are constructed here).
     */
    private static Kind<
            FetchKind.Witness<String, Either<String, String>>, List<Either<String, String>>>
        fetchAll(List<String> keys) {
      FetchApplicative<String, Either<String, String>> app = FetchApplicative.instance();
      Kind<FetchKind.Witness<String, Either<String, String>>, List<Either<String, String>>> acc =
          app.of(List.of());
      for (String key : keys) {
        Kind<FetchKind.Witness<String, Either<String, String>>, Either<String, String>> one =
            FETCH.widen(Fetch.fetch(key));
        acc =
            app.map2(
                acc,
                one,
                (list, result) -> {
                  List<Either<String, String>> next = new ArrayList<>(list);
                  next.add(result);
                  return next;
                });
      }
      return acc;
    }

    @Test
    @DisplayName("a failed key is a Left while the others succeed, in one batched call")
    void partialFailurePreservesSuccesses() {
      var program = fetchAll(List.of("a", "b", "c"));
      Function<Set<String>, Map<String, Either<String, String>>> resolver =
          keys -> {
            Map<String, Either<String, String>> out = new HashMap<>();
            for (String k : keys) {
              out.put(
                  k,
                  k.equals("b")
                      ? Either.<String, String>left("not found: b")
                      : Either.<String, String>right("V:" + k));
            }
            return out;
          };

      Either<Throwable, Fetch.RunResult<String, List<Either<String, String>>>> outcome =
          SafeFetch.runCached(FETCH.narrow(program), resolver);

      assertThat(outcome.isRight()).isTrue();
      Fetch.RunResult<String, List<Either<String, String>>> result = outcome.getRight();
      assertThat(result.value()).hasSize(3);
      assertThat(result.value().get(0).getRight()).isEqualTo("V:a");
      assertThat(result.value().get(1).isLeft()).isTrue();
      assertThat(result.value().get(1).getLeft()).isEqualTo("not found: b");
      assertThat(result.value().get(2).getRight()).isEqualTo("V:c");
      assertThat(result.backendCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("sequence is fail-fast: all successes, or the first Left")
    void sequenceFailFast() {
      Either<String, List<String>> allOk =
          SafeFetch.sequence(
              List.of(Either.<String, String>right("x"), Either.<String, String>right("y")));
      assertThat(allOk.isRight()).isTrue();
      assertThat(allOk.getRight()).containsExactly("x", "y");

      Either<String, List<String>> failed =
          SafeFetch.sequence(
              List.of(
                  Either.<String, String>right("x"),
                  Either.<String, String>left("e1"),
                  Either.<String, String>left("e2")));
      assertThat(failed.isLeft()).isTrue();
      assertThat(failed.getLeft()).isEqualTo("e1");
    }

    @Test
    @DisplayName("partition keeps every success and every failure")
    void partitionKeepsBoth() {
      SafeFetch.Partitioned<String, String> p =
          SafeFetch.partition(
              List.of(
                  Either.<String, String>right("x"),
                  Either.<String, String>left("e1"),
                  Either.<String, String>right("y"),
                  Either.<String, String>left("e2")));

      assertThat(p.successes()).containsExactly("x", "y");
      assertThat(p.failures()).containsExactly("e1", "e2");
    }
  }

  @Nested
  @DisplayName("timeout")
  class Timeouts {

    @Test
    @DisplayName("a run that exceeds the deadline yields Either.left(TimeoutException)")
    void slowRunTimesOut() throws Exception {
      Fetch<String, String, String> program = Fetch.fetch("k");
      BatchLoader<String, String> slow =
          keys ->
              CompletableFuture.supplyAsync(
                  () -> {
                    try {
                      Thread.sleep(300);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                    return Map.of("k", "V");
                  });

      Either<Throwable, Fetch.RunResult<String, String>> outcome =
          SafeFetch.runAsync(program, slow, new ConcurrentHashMap<>(), Duration.ofMillis(50)).get();

      assertThat(outcome.isLeft()).isTrue();
      assertThat(outcome.getLeft()).isInstanceOf(TimeoutException.class);
    }

    @Test
    @DisplayName("a run within the deadline succeeds normally")
    void fastRunSucceeds() throws Exception {
      Fetch<String, String, String> program = Fetch.fetch("k");
      BatchLoader<String, String> fast =
          keys -> CompletableFuture.completedFuture(Map.of("k", "V"));

      Either<Throwable, Fetch.RunResult<String, String>> outcome =
          SafeFetch.runAsync(program, fast, new ConcurrentHashMap<>(), Duration.ofSeconds(5)).get();

      assertThat(outcome.isRight()).isTrue();
      assertThat(outcome.getRight().value()).isEqualTo("V");
    }

    @Test
    @DisplayName("a loader that throws synchronously under a deadline yields Either.left")
    void loaderSynchronousThrowUnderDeadlineIsLeft() throws Exception {
      Fetch<String, String, String> program = Fetch.fetch("k");
      BatchLoader<String, String> throwing =
          keys -> {
            throw new IllegalStateException("loader exploded");
          };

      Either<Throwable, Fetch.RunResult<String, String>> outcome =
          SafeFetch.runAsync(program, throwing, new ConcurrentHashMap<>(), Duration.ofSeconds(5))
              .get();

      assertThat(outcome.isLeft()).isTrue();
      assertThat(outcome.getLeft()).isInstanceOf(IllegalStateException.class);
    }
  }
}
