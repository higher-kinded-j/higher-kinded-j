// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

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
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Optic;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.fetch.BatchLoader;
import org.higherkindedj.optics.fetch.Fetch;
import org.higherkindedj.optics.fetch.FetchApplicative;
import org.higherkindedj.optics.fetch.FetchKind;
import org.higherkindedj.optics.fetch.FetchOptics;
import org.higherkindedj.optics.fetch.SafeFetch;
import org.higherkindedj.optics.fetch.SourceRouter;
import org.higherkindedj.optics.focus.FocusPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 21: Optic-Driven Request Batching.
 *
 * <p>Pain to Promise. Loading data for each focus of an optic traversal is the classic N+1: a list
 * of 200 ids becomes 200 backend calls. The HKJ version is one batched call:
 *
 * <pre>
 *   var program = traversal.modifyF(id -&gt; FETCH.widen(Fetch.fetch(id)),
 *                                   source,
 *                                   FetchApplicative.&lt;K, V&gt;instance());
 *   var result  = Fetch.runCached(FETCH.narrow(program), backend::loadAll);
 *   // one round, one backend call, every focus resolved
 * </pre>
 *
 * <p>Java idiom anchor. Think of {@link FetchApplicative} as a strategy you hand the optic, the
 * same way you hand {@link java.util.stream.Collector} to {@link java.util.stream.Stream#collect
 * Stream.collect}: the optic still describes the shape; the strategy decides how the work is done.
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>{@link Fetch} captures a deferred, batched data request as a value;
 *   <li>{@link FetchApplicative} plugs into {@link Optic#modifyF} so a traversal of N foci
 *       collapses to one batched backend call;
 *   <li>{@link FetchOptics#fetchEach} builds the type-changing optic the codegen does not produce,
 *       so {@code Id -> Entity} fetches read cleanly;
 *   <li>{@link SourceRouter} combines per-source loaders into one, fanning out one dispatch per
 *       source concurrently;
 *   <li>{@link SafeFetch} moves run-time failures (a resolver that throws, a missing key, a loader
 *       that fails, a deadline) onto the value channel as {@link Either}.
 * </ul>
 *
 * <p>Limits, stated up front: batching is applicative-only (a {@code flatMap} data dependency costs
 * an extra round); the cache is per-run and in-JVM; optics are post-fetch (no predicate pushdown).
 *
 * <p>Prerequisites: Complete Tutorials 05 (Traversals) and 12 (Focus DSL) before this one.
 *
 * <p>Estimated time: ~15 minutes.
 *
 * <p>Replace each {@code answerRequired()} placeholder with the correct code to make the tests
 * pass.
 */
@DisplayName("Tutorial 21: Optic-Driven Request Batching")
public class Tutorial21_OpticBatching {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Domain Records and Fixtures
  // ===========================================================================

  /** A user identifier: the "key" that backends understand. */
  record UserId(int value) {}

  /** A user record: what the user-directory backend returns. */
  record User(UserId id, String displayName) {}

  /** A team carrying member ids (the unresolved shape). */
  record Team(String name, List<UserId> memberIds) {}

  /** The same team with members resolved (the rebuilt shape). */
  record EnrichedTeam(String name, List<User> members) {}

  /** An in-memory user directory used by every exercise. */
  static final Map<UserId, User> USER_DIRECTORY =
      Map.of(
          new UserId(1), new User(new UserId(1), "Alice"),
          new UserId(2), new User(new UserId(2), "Bob"),
          new UserId(3), new User(new UserId(3), "Cara"));

  @Nested
  @DisplayName("Part 1: same-type batching, N+1 in, one call out")
  class SameTypeBatching {

    /**
     * Exercise 1: same-type batching.
     *
     * <p>A traversal of a list of ids resolves in one batched round when {@link FetchApplicative}
     * is the strategy.
     *
     * <p>Task: build a {@code Fetch} program that calls {@code Fetch.fetch} for each integer id in
     * the source list, hand it to {@link FetchApplicative}, and run it through {@code doubler}.
     *
     * <pre>
     *   // Hint 1: the optic is {@code FocusPaths.listElements()}.
     *   // Hint 2: the modifyF lambda is {@code id -> FETCH.widen(Fetch.fetch(id))}.
     *   // Hint 3: the applicative is {@code FetchApplicative.instance()} parameterised by key/value.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 1: a list of ids resolves in one batched call")
    void exercise1_aListResolvesInOneBatchedCall() {
      var backendCalls = new AtomicInteger();
      Function<Set<Integer>, Map<Integer, Integer>> doubler =
          ids -> {
            backendCalls.incrementAndGet();
            Map<Integer, Integer> out = new HashMap<>();
            ids.forEach(id -> out.put(id, id * 2));
            return out;
          };

      Traversal<List<Integer>, Integer> ids = FocusPaths.listElements();

      // TODO: build the Fetch program by running the traversal with the FetchApplicative strategy.
      Kind<FetchKind.Witness<Integer, Integer>, List<Integer>> program = answerRequired();

      Fetch.RunResult<Integer, List<Integer>> result =
          Fetch.runCached(FETCH.narrow(program), doubler);

      assertThat(result.value()).containsExactly(2, 4, 6, 8, 10);
      assertThat(result.rounds()).isEqualTo(1);
      assertThat(backendCalls.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Part 2: heterogeneous fetch, Id in, Entity out")
  class HeterogeneousFetch {

    /**
     * Exercise 2: heterogeneous fetch.
     *
     * <p>The code-generated optic types are type-preserving (focus in = focus out), so a same-type
     * traversal cannot directly express "load each {@code UserId} into a {@code User}". {@link
     * FetchOptics#fetchEach} builds the type-changing list-traversal the codegen does not produce.
     *
     * <p>Task: build the heterogeneous {@code Optic<Team, EnrichedTeam, UserId, User>} that reads
     * the member-id list, hands each id to the resolver, and rebuilds the team with users.
     *
     * <pre>
     *   // Hint 1: source reader is {@code Team::memberIds}, rebuilder is {@code (team, users) -> new EnrichedTeam(team.name(), users)}.
     *   // Hint 2: pass both to {@code FetchOptics.fetchEach(source, rebuild)}.
     *   // Hint 3: the rest of the program is the same shape as exercise 1, but with {@code UserId}/{@code User} as the key/value types.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 2: a Team with member ids becomes an EnrichedTeam with users")
    void exercise2_heterogeneousFetchRebuildsTheStructure() {
      // TODO: build the type-changing list-traversal that loads UserIds into Users.
      Optic<Team, EnrichedTeam, UserId, User> memberFetch = answerRequired();

      Function<Set<UserId>, Map<UserId, User>> userResolver =
          ids -> {
            Map<UserId, User> out = new HashMap<>();
            ids.forEach(id -> out.put(id, USER_DIRECTORY.get(id)));
            return out;
          };

      Team team = new Team("Engineering", List.of(new UserId(1), new UserId(2), new UserId(3)));

      Kind<FetchKind.Witness<UserId, User>, EnrichedTeam> program =
          memberFetch.modifyF(
              id -> FETCH.widen(Fetch.<UserId, User>fetch(id)),
              team,
              FetchApplicative.<UserId, User>instance());

      Fetch.RunResult<UserId, EnrichedTeam> result =
          Fetch.runCached(FETCH.narrow(program), userResolver);

      assertThat(result.value())
          .isEqualTo(
              new EnrichedTeam(
                  "Engineering",
                  List.of(
                      new User(new UserId(1), "Alice"),
                      new User(new UserId(2), "Bob"),
                      new User(new UserId(3), "Cara"))));
      assertThat(result.rounds()).isEqualTo(1);
      assertThat(result.backendCalls()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Part 3: multi-source, one traversal, several backends")
  class MultiSource {

    /**
     * Exercise 3: multi-source routing.
     *
     * <p>{@link SourceRouter#routed} combines per-source {@link BatchLoader}s into one, fanning out
     * a round's key set to one concurrent dispatch per source. The substrate still sees a single
     * round.
     *
     * <p>Task: build the routed loader that sends keys starting with {@code "u:"} to {@code users}
     * and everything else to {@code products}.
     *
     * <pre>
     *   // Hint 1: the classifier is {@code key -> key.startsWith("u:") ? "users" : "products"}.
     *   // Hint 2: the loader map is {@code Map.of("users", users, "products", products)}.
     *   // Hint 3: call {@code SourceRouter.routed(classifier, loaders)}.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 3: keys are partitioned by source, one dispatch per source")
    void exercise3_oneDispatchPerSource() throws Exception {
      var userDispatches = new AtomicInteger();
      var productDispatches = new AtomicInteger();

      BatchLoader<String, String> users =
          keys ->
              CompletableFuture.supplyAsync(
                  () -> {
                    userDispatches.incrementAndGet();
                    Map<String, String> out = new HashMap<>();
                    keys.forEach(k -> out.put(k, "USER:" + k));
                    return out;
                  });
      BatchLoader<String, String> products =
          keys ->
              CompletableFuture.supplyAsync(
                  () -> {
                    productDispatches.incrementAndGet();
                    Map<String, String> out = new HashMap<>();
                    keys.forEach(k -> out.put(k, "PRODUCT:" + k));
                    return out;
                  });

      // TODO: combine the two loaders into one using SourceRouter.routed.
      BatchLoader<String, String> routed = answerRequired();

      Traversal<List<String>, String> ids = FocusPaths.listElements();
      var program =
          ids.modifyF(
              key -> FETCH.widen(Fetch.<String, String>fetch(key)),
              List.of("u:1", "p:9", "u:2", "p:8"),
              FetchApplicative.<String, String>instance());

      Fetch.RunResult<String, List<String>> result =
          Fetch.runAsync(FETCH.narrow(program), routed, new ConcurrentHashMap<>()).get();

      assertThat(result.value())
          .containsExactly("USER:u:1", "PRODUCT:p:9", "USER:u:2", "PRODUCT:p:8");
      assertThat(result.rounds()).isEqualTo(1);
      assertThat(userDispatches.get()).isEqualTo(1);
      assertThat(productDispatches.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Part 4: railway errors, failures as values")
  class RailwayErrors {

    /**
     * Exercise 4: total runner.
     *
     * <p>{@link SafeFetch} captures boundary failure (resolver throws, missing key) as {@code
     * Either.left} on the value channel; the run never throws.
     *
     * <p>Task: run the {@code program} through the failing resolver using {@code SafeFetch} so the
     * outcome is {@code Either.left} rather than an exception.
     *
     * <pre>
     *   // Hint 1: use {@code SafeFetch.runCached(program, resolver)} in place of {@code Fetch.runCached}.
     *   // Hint 2: the return type is {@code Either<Throwable, Fetch.RunResult<UserId, User>>}.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 4: a resolver exception is captured as Either.left")
    void exercise4_resolverFailureIsLeft() {
      Function<Set<UserId>, Map<UserId, User>> failing =
          ids -> {
            throw new IllegalStateException("user-directory unavailable");
          };

      Fetch<UserId, User, User> program = Fetch.fetch(new UserId(1));

      // TODO: run program through the failing resolver with SafeFetch so it never throws.
      Either<Throwable, Fetch.RunResult<UserId, User>> outcome = answerRequired();

      assertThat(outcome.isLeft()).isTrue();
      assertThat(outcome.getLeft())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("unavailable");
    }

    /**
     * Exercise 5: per-key partial success.
     *
     * <p>When the value type is {@code Either<E, V>}, the backend reports per-key failure without
     * poisoning the whole round. {@link SafeFetch#partition} splits the result into successes and
     * failures.
     *
     * <p>Task: split the {@code result.value()} list of {@code Either<String, User>} into its
     * successes and failures.
     *
     * <pre>
     *   // Hint 1: call {@code SafeFetch.partition(list)}.
     *   // Hint 2: it returns a {@code SafeFetch.Partitioned<E, V>} with {@code successes()} and
     *   //         {@code failures()} lists.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 5: per-key Either preserves partial-batch success")
    void exercise5_perKeyEitherKeepsPartialSuccess() {
      Function<Set<UserId>, Map<UserId, Either<String, User>>> partial =
          ids -> {
            Map<UserId, Either<String, User>> out = new HashMap<>();
            for (UserId id : ids) {
              User user = USER_DIRECTORY.get(id);
              out.put(
                  id,
                  user != null
                      ? Either.<String, User>right(user)
                      : Either.<String, User>left("not found: " + id.value()));
            }
            return out;
          };

      FetchApplicative<UserId, Either<String, User>> app = FetchApplicative.instance();
      Kind<FetchKind.Witness<UserId, Either<String, User>>, Either<String, User>> alice =
          FETCH.widen(Fetch.fetch(new UserId(1)));
      Kind<FetchKind.Witness<UserId, Either<String, User>>, Either<String, User>> ghost =
          FETCH.widen(Fetch.fetch(new UserId(99)));
      Kind<FetchKind.Witness<UserId, Either<String, User>>, Either<String, User>> cara =
          FETCH.widen(Fetch.fetch(new UserId(3)));
      Kind<FetchKind.Witness<UserId, Either<String, User>>, List<Either<String, User>>> program =
          app.map2(
              app.map2(
                  app.map(List::<Either<String, User>>of, alice),
                  ghost,
                  (l, e) -> {
                    var n = new java.util.ArrayList<>(l);
                    n.add(e);
                    return List.copyOf(n);
                  }),
              cara,
              (l, e) -> {
                var n = new java.util.ArrayList<>(l);
                n.add(e);
                return List.copyOf(n);
              });

      Fetch.RunResult<UserId, List<Either<String, User>>> result =
          Fetch.runCached(FETCH.narrow(program), partial);

      // TODO: split the per-key Either list into successes and failures.
      SafeFetch.Partitioned<String, User> split = answerRequired();

      assertThat(split.successes())
          .containsExactly(new User(new UserId(1), "Alice"), new User(new UserId(3), "Cara"));
      assertThat(split.failures()).containsExactly("not found: 99");
      assertThat(result.rounds()).isEqualTo(1);
      assertThat(result.backendCalls()).isEqualTo(1);
    }
  }
}
