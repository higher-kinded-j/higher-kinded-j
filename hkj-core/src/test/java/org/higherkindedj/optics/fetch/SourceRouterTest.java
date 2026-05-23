// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.fetch.FetchKindHelper.FETCH;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.focus.FocusPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Capability A, A2 specification: a routed multi-source loader resolves a traversal that spans
 * several sources in one substrate round, dispatching one batch per source.
 */
@DisplayName("SourceRouter: multi-source batching")
class SourceRouterTest {

  /** A per-source loader that counts its dispatches and records the keys it was handed. */
  static final class CountingLoader {
    final AtomicInteger dispatches = new AtomicInteger();
    final Set<String> seenKeys = ConcurrentHashMap.newKeySet();
    private final String tag;

    CountingLoader(String tag) {
      this.tag = tag;
    }

    BatchLoader<String, String> loader() {
      return keys ->
          CompletableFuture.supplyAsync(
              () -> {
                dispatches.incrementAndGet();
                seenKeys.addAll(keys);
                Map<String, String> out = new HashMap<>();
                keys.forEach(k -> out.put(k, tag + ":" + k));
                return out;
              });
    }
  }

  /** Classifies a key by prefix: "u:" -> users, "p:" -> products, anything else -> "other". */
  private static final Function<String, String> CLASSIFIER =
      key -> {
        if (key.startsWith("u:")) {
          return "users";
        }
        if (key.startsWith("p:")) {
          return "products";
        }
        return "other";
      };

  private static org.higherkindedj.hkt.Kind<FetchKind.Witness<String, String>, List<String>>
      traverse(List<String> keys) {
    Traversal<List<String>, String> ids = FocusPaths.listElements();
    return ids.modifyF(id -> FETCH.widen(Fetch.fetch(id)), keys, FetchApplicative.instance());
  }

  @Test
  @DisplayName("a traversal spanning two sources resolves in one dispatch per source")
  void oneDispatchPerSource() throws Exception {
    CountingLoader users = new CountingLoader("U");
    CountingLoader products = new CountingLoader("P");
    BatchLoader<String, String> routed =
        SourceRouter.routed(
            CLASSIFIER, Map.of("users", users.loader(), "products", products.loader()));

    var program = traverse(List.of("u:1", "p:9", "u:2"));
    Fetch.RunResult<String, List<String>> result =
        Fetch.runAsync(FETCH.narrow(program), routed, new ConcurrentHashMap<>()).get();

    assertThat(result.value()).containsExactly("U:u:1", "P:p:9", "U:u:2");
    assertThat(result.rounds()).isEqualTo(1);
    assertThat(users.dispatches.get()).isEqualTo(1);
    assertThat(products.dispatches.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("keys of the same source are batched into that source's single dispatch")
  void sameSourceKeysBatchedTogether() throws Exception {
    CountingLoader users = new CountingLoader("U");
    BatchLoader<String, String> routed =
        SourceRouter.routed(CLASSIFIER, Map.of("users", users.loader()));

    var program = traverse(List.of("u:1", "u:2", "u:3"));
    Fetch.runAsync(FETCH.narrow(program), routed, new ConcurrentHashMap<>()).get();

    assertThat(users.dispatches.get()).isEqualTo(1);
    assertThat(users.seenKeys).containsExactlyInAnyOrder("u:1", "u:2", "u:3");
  }

  @Test
  @DisplayName("a key whose source has no registered loader fails the run")
  void unregisteredSourceFails() throws Exception {
    CountingLoader users = new CountingLoader("U");
    BatchLoader<String, String> routed =
        SourceRouter.routed(CLASSIFIER, Map.of("users", users.loader()));

    var program = traverse(List.of("u:1", "x:9"));
    Either<Throwable, Fetch.RunResult<String, List<String>>> outcome =
        SafeFetch.runAsync(FETCH.narrow(program), routed, new ConcurrentHashMap<>()).get();

    assertThat(outcome.isLeft()).isTrue();
    assertThat(outcome.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("other");
  }
}
