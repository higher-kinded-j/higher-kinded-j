// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

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
 * Solution for Tutorial 21: Optic-Driven Request Batching.
 *
 * <p>The exercise versions of these methods use {@code answerRequired()} placeholders; this
 * solution shows the working code and, per the teaching-solution format, the reasoning that picks
 * one form over the alternatives.
 */
@DisplayName("Tutorial Solution 21: Optic-Driven Request Batching")
public class Tutorial21_OpticBatching_Solution {

  record UserId(int value) {}

  record User(UserId id, String displayName) {}

  record Team(String name, List<UserId> memberIds) {}

  record EnrichedTeam(String name, List<User> members) {}

  private static final Map<UserId, User> USER_DIRECTORY =
      Map.of(
          new UserId(1), new User(new UserId(1), "Alice"),
          new UserId(2), new User(new UserId(2), "Bob"),
          new UserId(3), new User(new UserId(3), "Cara"));

  @Nested
  @DisplayName("Part 1: same-type batching, N+1 in, one call out")
  class SameTypeBatching {

    /**
     * Why this is idiomatic: the {@link Traversal} expresses the shape ("every element"); the
     * {@link FetchApplicative} expresses the strategy ("batch the lookups"). The N+1 collapses
     * because {@code FetchApplicative.ap} merges pending request sets, so the traversal's
     * independent foci end the round as one keyset.
     *
     * <p>Alternative: write a loop that maps each id to a {@code CompletableFuture} and {@code
     * thenCombine}s them. Same answer for one call site; loses the batching once a traversal sits
     * underneath another optic, because the loop is not a value the optic can recognise.
     *
     * <p>Common wrong attempt: handing {@code Future.applicative()} (or another sequential
     * applicative) to {@code modifyF}. The traversal runs, but every focus dispatches its own call:
     * the batch is gone because the applicative did not union the pending requests.
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
      var program =
          ids.modifyF(
              id -> FETCH.widen(Fetch.<Integer, Integer>fetch(id)),
              List.of(1, 2, 3, 4, 5),
              FetchApplicative.<Integer, Integer>instance());

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
     * Why this is idiomatic: the code-generated optic types are type-preserving (focus in = focus
     * out), so a same-type {@code Traversal} cannot directly express "load each {@code UserId} into
     * a {@code User}". {@link FetchOptics#fetchEach} builds the type-changing list-traversal the
     * codegen does not produce, given a list-reader and a rebuild function.
     *
     * <p>Alternative: read the list outside the optic, fetch in a loop, reassemble by hand. Same
     * answer; loses composability because the heterogeneous step is no longer an {@code Optic} you
     * can chain.
     *
     * <p>Common wrong attempt: try to make the codegen produce a type-changing list-traversal. The
     * generated types are type-preserving by design; reach for {@code FetchOptics.fetchEach} (or a
     * hand-rolled van Laarhoven optic) for the heterogeneous case.
     */
    @Test
    @DisplayName("Exercise 2: a Team with member ids becomes an EnrichedTeam with users")
    void exercise2_heterogeneousFetchRebuildsTheStructure() {
      Optic<Team, EnrichedTeam, UserId, User> memberFetch =
          FetchOptics.fetchEach(
              Team::memberIds, (team, users) -> new EnrichedTeam(team.name(), users));

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
     * Why this is idiomatic: a real round mixes keys belonging to different backends. {@link
     * SourceRouter#routed} composes per-source {@link BatchLoader}s with a classifier into one
     * loader the substrate can call. Each backend sees its own keys once per round; the round is
     * still one round.
     *
     * <p>Alternative: write a loader that switches on each key and calls the right backend one call
     * at a time. Same answer; you lose per-source batching.
     *
     * <p>Common wrong attempt: pre-split the input list by source and run two separate {@code
     * Fetch} programs. It works, but the optic now has to be invoked twice and any cross-source
     * dedup between the two is gone.
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

      BatchLoader<String, String> routed =
          SourceRouter.routed(
              key -> key.startsWith("u:") ? "users" : "products",
              Map.of("users", users, "products", products));

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
     * Why this is idiomatic: {@link SafeFetch#runCached} captures any boundary failure (resolver
     * throws, missing key) as {@code Either.left} on the value channel. The runner never throws.
     *
     * <p>Alternative: wrap the run in {@code try/catch} and translate. The {@code SafeFetch}
     * wrapper does the same thing and keeps the call site about the value, not the plumbing.
     *
     * <p>Common wrong attempt: catch the exception inside the resolver and return an empty map. The
     * substrate then sees a missing key and surfaces a {@code MissingKeyException}, which is a
     * different (and confusing) signal from "the backend failed".
     */
    @Test
    @DisplayName("Exercise 4: a resolver exception is captured as Either.left")
    void exercise4_resolverFailureIsLeft() {
      Function<Set<UserId>, Map<UserId, User>> failing =
          ids -> {
            throw new IllegalStateException("user-directory unavailable");
          };

      Fetch<UserId, User, User> program = Fetch.fetch(new UserId(1));

      Either<Throwable, Fetch.RunResult<UserId, User>> outcome =
          SafeFetch.runCached(program, failing);

      assertThat(outcome.isLeft()).isTrue();
      assertThat(outcome.getLeft())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("unavailable");
    }

    /**
     * Why this is idiomatic: when the value type is {@code Either<E, V>}, the backend can report
     * per-key failure without poisoning the whole round. {@link SafeFetch#partition} splits the
     * round's result into successes and failures without discarding either side.
     *
     * <p>Alternative: post-process the list with two filter passes. Same answer; {@code partition}
     * keeps the two halves aligned and saves the second traversal.
     *
     * <p>Common wrong attempt: throw on the first missing key inside the resolver. That loses the
     * successful results for the other keys in the same batch.
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

      SafeFetch.Partitioned<String, User> split = SafeFetch.partition(result.value());

      assertThat(split.successes())
          .containsExactly(new User(new UserId(1), "Alice"), new User(new UserId(3), "Cara"));
      assertThat(split.failures()).containsExactly("not found: 99");
      assertThat(result.rounds()).isEqualTo(1);
      assertThat(result.backendCalls()).isEqualTo(1);
    }
  }
}
