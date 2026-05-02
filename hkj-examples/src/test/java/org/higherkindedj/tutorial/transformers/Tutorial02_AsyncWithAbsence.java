// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.transformers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.optional_t.OptionalTKindHelper.OPTIONAL_T;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe_t.MaybeT;
import org.higherkindedj.hkt.optional_t.OptionalT;
import org.higherkindedj.hkt.optional_t.OptionalTKind;
import org.higherkindedj.hkt.optional_t.OptionalTMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 02: Async with Absence - Composing Future&lt;Optional&gt; chains
 *
 * <p>Many libraries (caching layers, repositories, configuration loaders) return values shaped as
 * {@code CompletableFuture<Optional<T>>}: an asynchronous lookup that may not find anything. The
 * Effect Path API gives you {@code OptionalPath} for synchronous absence and {@code
 * CompletableFuturePath} for async, but neither covers the intersection. {@code OptionalT} bridges
 * the gap so you can chain async lookups without nested {@code
 * orElse(CompletableFuture.completedFuture(Optional.empty()))} boilerplate.
 *
 * <p>Higher-Kinded-J also offers a sister type, {@code Maybe}, with its own transformer {@code
 * MaybeT}. Use whichever matches the absence representation in your codebase.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>Bridge an existing {@code Future<Optional>} into {@code OptionalT} with {@code fromKind}
 *   <li>Use {@code For} comprehensions to chain async lookups
 *   <li>Recover from absence with {@code handleErrorWith} (the error type is {@code Unit})
 *   <li>Apply the same shape to {@code MaybeT} when you prefer {@code Maybe} over {@code Optional}
 * </ul>
 *
 * <p>Prerequisites: complete Tutorial 01 (When Path Isn't Enough).
 *
 * <p>Estimated time: 20-30 minutes
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 02: Async with Absence")
public class Tutorial02_AsyncWithAbsence {

  // --- Domain types ---

  record User(String id, String name) {}

  record Profile(String userId, String avatarUrl) {}

  record Preferences(String userId, String theme) {}

  // --- Fixtures ---

  private CompletableFutureMonad futureMonad;
  private OptionalTMonad<CompletableFutureKind.Witness> optionalTMonad;

  @BeforeEach
  void setUp() {
    futureMonad = CompletableFutureMonad.INSTANCE;
    optionalTMonad = new OptionalTMonad<>(futureMonad);
  }

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // Simulated async repository: returns CompletableFuture<Optional<T>>.

  private Kind<CompletableFutureKind.Witness, Optional<User>> fetchUser(String userId) {
    if (userId.equals("missing")) {
      return FUTURE.widen(CompletableFuture.completedFuture(Optional.empty()));
    }
    return FUTURE.widen(CompletableFuture.completedFuture(Optional.of(new User(userId, "Alice"))));
  }

  private Kind<CompletableFutureKind.Witness, Optional<Profile>> fetchProfile(String userId) {
    return FUTURE.widen(
        CompletableFuture.completedFuture(
            Optional.of(new Profile(userId, "https://example.com/" + userId + ".png"))));
  }

  // ===========================================================================
  // Part 1: OptionalT
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: OptionalT")
  class OptionalTExercises {

    /**
     * Exercise 1: Bridge a Future&lt;Optional&gt; into OptionalT.
     *
     * <p>Just like {@code EitherT.fromKind} in Tutorial 01, {@code OptionalT.fromKind} accepts an
     * existing {@code Kind<F, Optional<A>>} and wraps it.
     *
     * <p>Task: Lift the result of {@code fetchUser("alice")} into an OptionalT.
     */
    @Test
    @DisplayName("Exercise 1: fromKind lifts a Future<Optional> into OptionalT")
    void exercise1_fromKindLiftsFutureOptional() {
      var alice = fetchUser("alice");

      // TODO: Replace answerRequired() with OptionalT.fromKind(alice)
      OptionalT<CompletableFutureKind.Witness, User> wrapped = answerRequired();

      var result = FUTURE.join(wrapped.value());
      assertThat(result).isPresent();
      assertThat(result.get().name()).isEqualTo("Alice");
    }

    /**
     * Exercise 2: Chain two async lookups with For.
     *
     * <p>If the first lookup returns {@code Optional.empty()}, the second is skipped and the final
     * result is empty. No null checks, no nested {@code orElse} on futures.
     *
     * <p>Task: First fetch the user, then fetch their profile.
     */
    @Test
    @DisplayName("Exercise 2: For chains two async lookups")
    void exercise2_forChainsAsyncLookups() {
      // TODO: Replace answerRequired() with:
      // For.from(optionalTMonad, OptionalT.fromKind(fetchUser("alice")))
      //     .from(user -> OptionalT.fromKind(fetchProfile(user.id())))
      //     .yield((user, profile) -> profile)
      Kind<OptionalTKind.Witness<CompletableFutureKind.Witness>, Profile> workflow =
          answerRequired();

      var result = FUTURE.join(OPTIONAL_T.narrow(workflow).value());
      assertThat(result).isPresent();
      assertThat(result.get().userId()).isEqualTo("alice");
    }

    /**
     * Exercise 3: An empty result short-circuits the chain.
     *
     * <p>Use the same shape as exercise 2 but with {@code "missing"} as the user id. The chain
     * should yield {@code Optional.empty()} without ever calling {@code fetchProfile}.
     *
     * <p>Task: Fill in the comprehension as before, but for the missing user.
     */
    @Test
    @DisplayName("Exercise 3: Empty short-circuits the chain")
    void exercise3_emptyShortCircuits() {
      // TODO: Replace answerRequired() with the same For shape as exercise 2 but for "missing":
      // For.from(optionalTMonad, OptionalT.fromKind(fetchUser("missing")))
      //     .from(user -> OptionalT.fromKind(fetchProfile(user.id())))
      //     .yield((user, profile) -> profile)
      Kind<OptionalTKind.Witness<CompletableFutureKind.Witness>, Profile> workflow =
          answerRequired();

      var result = FUTURE.join(OPTIONAL_T.narrow(workflow).value());
      assertThat(result).isEmpty();
    }

    /**
     * Exercise 4: Recover from absence with handleErrorWith.
     *
     * <p>{@code OptionalT}'s error type is {@code Unit}: absence carries no information beyond
     * itself. The recovery handler receives a {@code Unit} value and returns a fresh OptionalT.
     *
     * <p>Note: {@code OptionalT} already implements {@code Kind}, so you can pass it directly to
     * {@code handleErrorWith} without a widen call.
     *
     * <p>Task: When the lookup is empty, substitute a default {@code Preferences} record.
     */
    @Test
    @DisplayName("Exercise 4: handleErrorWith provides a default for empty")
    void exercise4_handleErrorWithProvidesDefault() {
      Kind<CompletableFutureKind.Witness, Optional<Preferences>> emptyLookup =
          FUTURE.widen(CompletableFuture.completedFuture(Optional.empty()));
      var attempt = OptionalT.fromKind(emptyLookup);
      var defaults = new Preferences("guest", "default-light");

      // TODO: Replace answerRequired() with:
      // optionalTMonad.handleErrorWith(
      //     attempt,
      //     (Unit v) -> OptionalT.some(futureMonad, defaults))
      Kind<OptionalTKind.Witness<CompletableFutureKind.Witness>, Preferences> recovered =
          answerRequired();

      var result = FUTURE.join(OPTIONAL_T.narrow(recovered).value());
      assertThat(result).isPresent();
      assertThat(result.get().theme()).isEqualTo("default-light");
    }
  }

  // ===========================================================================
  // Part 2: MaybeT
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: MaybeT")
  class MaybeTExercises {

    /**
     * Exercise 5: MaybeT mirrors OptionalT for Higher-Kinded-J's Maybe type.
     *
     * <p>If your codebase uses {@code Maybe} rather than {@code java.util.Optional}, reach for
     * {@code MaybeT} instead. The factory methods are {@code just}, {@code nothing}, {@code
     * fromMaybe}, and {@code fromKind}, mirroring the Optional versions.
     *
     * <p>Task: Build a {@code MaybeT} containing the user "Bob" by lifting a future-of-Maybe.
     */
    @Test
    @DisplayName("Exercise 5: MaybeT.fromKind for an async Maybe")
    void exercise5_maybeTFromKind() {
      Kind<CompletableFutureKind.Witness, Maybe<User>> bob =
          FUTURE.widen(CompletableFuture.completedFuture(Maybe.just(new User("bob", "Bob"))));

      // TODO: Replace answerRequired() with MaybeT.fromKind(bob)
      MaybeT<CompletableFutureKind.Witness, User> wrapped = answerRequired();

      var result = FUTURE.join(wrapped.value());
      assertThat(result.isJust()).isTrue();
      assertThat(result.get().name()).isEqualTo("Bob");
    }
  }

  /**
   * Congratulations! You've completed Tutorial 02: Async with Absence.
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to bridge {@code CompletableFuture<Optional<T>>} into the transformer world
   *   <li>How {@code For} keeps async lookup chains readable
   *   <li>How to provide defaults when a lookup yields no result
   *   <li>That {@code MaybeT} follows the same shape if your code uses {@code Maybe}
   * </ul>
   *
   * <p>Next up: Tutorial 03 shows what stacking two transformers looks like, and why you should
   * almost never need to.
   */
}
