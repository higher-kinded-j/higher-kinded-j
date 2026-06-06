// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.optics.fetch.FetchKindHelper.FETCH;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.focus.FocusPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Hardening tests for the effectful-optic batching substrate: stack safety, the missing-key policy,
 * null-argument contract checks, and Kind-narrowing robustness. The applicative laws are exercised
 * separately by {@code FetchApplicativeLawsTestFactory}.
 */
@DisplayName("Fetch substrate hardening")
class FetchHardeningTest {

  /** A resolver that echoes every requested key as {@code "val:" + key}. */
  private static final Function<Set<String>, Map<String, String>> ECHO =
      keys -> {
        Map<String, String> out = new HashMap<>();
        keys.forEach(k -> out.put(k, "val:" + k));
        return out;
      };

  private static Function<String, Kind<FetchKind.Witness<String, String>, String>> fetchEach() {
    return id -> FETCH.widen(Fetch.<String, String>fetch(id));
  }

  @Nested
  @DisplayName("stack safety")
  class StackSafety {

    /**
     * Stack usage of the trampolined resolution is constant in N, so 200,000 elements — well above
     * the ~25,000 at which a non-trampolined traversal overflows — is conclusive proof of stack
     * safety. Million-element throughput and memory are benchmark concerns, not a unit test.
     */
    @Test
    @DisplayName("a 200,000-element optic traversal resolves without StackOverflowError")
    void wideTraversalIsStackSafe() {
      Traversal<List<String>, String> ids = FocusPaths.listElements();
      List<String> input = IntStream.range(0, 200_000).mapToObj(Integer::toString).toList();

      var program = ids.modifyF(fetchEach(), input, FetchApplicative.<String, String>instance());

      Fetch.RunResult<String, List<String>> result = Fetch.runCached(FETCH.narrow(program), ECHO);

      assertThat(result.value()).hasSize(200_000);
      assertThat(result.backendCalls()).isEqualTo(1);
      assertThat(result.rounds()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("missing-key policy")
  class MissingKeyPolicy {

    private static final List<String> ABC = List.of("a", "b", "c");

    @Test
    @DisplayName("runCached fails clearly when a resolver omits a key, never a silent null")
    void missingKeyFailsClearly() {
      Traversal<List<String>, String> ids = FocusPaths.listElements();
      var program = ids.modifyF(fetchEach(), ABC, FetchApplicative.<String, String>instance());

      Function<Set<String>, Map<String, String>> omitsB =
          keys -> {
            Map<String, String> out = new HashMap<>();
            keys.forEach(
                k -> {
                  if (!k.equals("b")) {
                    out.put(k, "val:" + k);
                  }
                });
            return out;
          };

      assertThatThrownBy(() -> Fetch.runCached(FETCH.narrow(program), omitsB))
          .isInstanceOf(MissingKeyException.class)
          .hasMessageContaining("b");
    }

    @Test
    @DisplayName("runAsync fails the future when a loader omits a key")
    void missingKeyFailsTheAsyncFuture() {
      Traversal<List<String>, String> ids = FocusPaths.listElements();
      var program = ids.modifyF(fetchEach(), ABC, FetchApplicative.<String, String>instance());

      BatchLoader<String, String> omitsB =
          keys -> {
            Map<String, String> out = new HashMap<>();
            keys.forEach(
                k -> {
                  if (!k.equals("b")) {
                    out.put(k, "val:" + k);
                  }
                });
            return CompletableFuture.completedFuture(out);
          };

      assertThatThrownBy(
              () -> Fetch.runAsync(FETCH.narrow(program), omitsB, new ConcurrentHashMap<>()).get())
          .hasCauseInstanceOf(MissingKeyException.class);
    }
  }

  @Nested
  @DisplayName("contract checks")
  class ContractChecks {

    @Test
    @DisplayName("public entry points reject null arguments")
    void nullArgumentsRejected() {
      Fetch<String, String, String> someFetch = Fetch.fetch("k");
      FetchApplicative<String, String> app = FetchApplicative.instance();
      Kind<FetchKind.Witness<String, String>, String> someKind = FETCH.widen(someFetch);
      BatchLoader<String, String> loader = keys -> CompletableFuture.completedFuture(Map.of());

      assertThatThrownBy(() -> Fetch.fetch(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Fetch.runCached(null, ECHO))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Fetch.runCached(someFetch, null))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(
              () -> Fetch.runAsync(null, loader, new ConcurrentHashMap<String, String>()))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> someFetch.map(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> someFetch.flatMap(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> app.ap(null, someKind)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> app.map(null, someKind)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("null results from a resolver or loader are rejected with a clear message")
    void nullResultsFromUserCallbacksRejected() {
      Fetch<String, String, String> program = Fetch.fetch("k");

      // A resolver that returns a null map.
      assertThatThrownBy(() -> Fetch.runCached(program, keys -> null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("batchResolver");

      // A loader that returns a null future.
      assertThatThrownBy(() -> Fetch.runAsync(program, keys -> null, new ConcurrentHashMap<>()))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("loader");

      // A loader whose future resolves to null.
      assertThatThrownBy(
              () ->
                  Fetch.runAsync(
                          program,
                          keys -> CompletableFuture.completedFuture(null),
                          new ConcurrentHashMap<>())
                      .get())
          .hasCauseInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Kind narrowing")
  class KindNarrowing {

    @Test
    @DisplayName("narrow rejects a null Kind")
    void narrowRejectsNull() {
      assertThatThrownBy(() -> FETCH.narrow(null)).isInstanceOf(KindUnwrapException.class);
    }

    @Test
    @DisplayName("narrow rejects a Kind that is not a Fetch")
    void narrowRejectsForeignKind() {
      Kind<FetchKind.Witness<String, String>, String> foreign = new Kind<>() {};
      assertThatThrownBy(() -> FETCH.narrow(foreign)).isInstanceOf(KindUnwrapException.class);
    }
  }
}
