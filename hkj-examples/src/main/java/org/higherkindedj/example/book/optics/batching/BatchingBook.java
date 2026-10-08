// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.batching;

import static org.higherkindedj.optics.fetch.FetchKindHelper.FETCH;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Optic;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.fetch.BatchLoader;
import org.higherkindedj.optics.fetch.Fetch;
import org.higherkindedj.optics.fetch.FetchApplicative;
import org.higherkindedj.optics.fetch.FetchOptics;
import org.higherkindedj.optics.fetch.SafeFetch;
import org.higherkindedj.optics.fetch.SourceRouter;
import org.higherkindedj.optics.focus.FocusPaths;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/optic_batching.html">Optic-Driven
 * Batching</a> page. The page {@code {{#include}}}s the anchored regions, and {@code
 * BatchingBookTest} holds the claims the page makes about this code.
 */
public final class BatchingBook {

  /** A backend that answers each key with ten times itself, and counts the calls it receives. */
  static final class Backend {
    private int calls;

    Map<Integer, Integer> loadAll(Set<Integer> keys) {
      calls++;
      return keys.stream().collect(Collectors.toMap(key -> key, key -> key * 10));
    }

    int calls() {
      return calls;
    }
  }

  private BatchingBook() {}

  static Fetch.RunResult<Integer, List<Integer>> pipeline(Backend backend) {
    // ANCHOR: pipeline
    // 1. The optic describes the shape (a list-traversal here).
    Traversal<List<Integer>, Integer> ids = FocusPaths.listElements();

    // 2. The applicative is the strategy: FetchApplicative batches.
    var program =
        ids.modifyF(
            id -> FETCH.widen(Fetch.<Integer, Integer>fetch(id)),
            List.of(1, 2, 3, 4, 5),
            FetchApplicative.<Integer, Integer>instance());

    // 3. The runner hands a whole round's keyset to the resolver in one call.
    Fetch.RunResult<Integer, List<Integer>> result =
        Fetch.runCached(FETCH.narrow(program), backend::loadAll);

    // result.rounds() and result.backendCalls() are both 1: one round, one batched call
    // ANCHOR_END: pipeline
    return result;
  }

  static Fetch.RunResult<String, List<String>> routed()
      throws InterruptedException, ExecutionException {
    // User ids and product skus, fetched in one traversal
    Traversal<List<String>, String> keys = FocusPaths.listElements();
    var program =
        keys.modifyF(
            key -> FETCH.widen(Fetch.<String, String>fetch(key)),
            List.of("u:1", "p:7", "u:2"),
            FetchApplicative.<String, String>instance());

    // ANCHOR: routed
    // Each backend answers only the keys it serves
    BatchLoader<String, String> users =
        ids ->
            CompletableFuture.completedFuture(
                ids.stream().collect(Collectors.toMap(id -> id, id -> "user " + id.substring(2))));
    BatchLoader<String, String> products =
        skus ->
            CompletableFuture.completedFuture(
                skus.stream()
                    .collect(Collectors.toMap(sku -> sku, sku -> "product " + sku.substring(2))));

    BatchLoader<String, String> routed =
        SourceRouter.routed(
            key -> key.startsWith("u:") ? "users" : "products",
            Map.of("users", users, "products", products));

    Fetch.RunResult<String, List<String>> result =
        Fetch.runAsync(FETCH.narrow(program), routed, new ConcurrentHashMap<>()).get();
    // ANCHOR_END: routed
    return result;
  }

  static SafeFetch.Partitioned<String, User> partition() {
    // Two members the directory knows, and one it does not, each fetched as an Either
    Optic<List<UserId>, List<Either<String, User>>, UserId, Either<String, User>> members =
        FetchOptics.fetchEach(ids -> ids, (ids, answers) -> answers);
    var program =
        members.modifyF(
            id -> FETCH.widen(Fetch.<UserId, Either<String, User>>fetch(id)),
            List.of(new UserId("ada"), new UserId("nobody"), new UserId("grace")),
            FetchApplicative.<UserId, Either<String, User>>instance());

    // ANCHOR: partition
    // The backend reports each key on its own: a User, or the reason there is none
    Function<Set<UserId>, Map<UserId, Either<String, User>>> partial =
        ids -> ids.stream().collect(Collectors.toMap(id -> id, BatchingBook::lookUp));

    Fetch.RunResult<UserId, List<Either<String, User>>> result =
        Fetch.runCached(FETCH.narrow(program), partial);

    SafeFetch.Partitioned<String, User> split = SafeFetch.partition(result.value());
    List<User> found = split.successes();
    List<String> reasons = split.failures();
    // ANCHOR_END: partition
    return split;
  }

  private static Either<String, User> lookUp(UserId id) {
    return switch (id.value()) {
      case "ada" -> Either.right(new User(id, "Ada Lovelace"));
      case "grace" -> Either.right(new User(id, "Grace Hopper"));
      default -> Either.left("no user " + id.value());
    };
  }
}

record UserId(String value) {}

record User(UserId id, String name) {}
