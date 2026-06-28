// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.transformers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.optional_t.OptionalTKindHelper.OPTIONAL_T;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
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
 * Solution for Tutorial02 AsyncWithAbsence — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
@DisplayName("Tutorial 02: Async with Absence - Solutions")
public class Tutorial02_AsyncWithAbsence_Solution {

  record User(String id, String name) {}

  record Profile(String userId, String avatarUrl) {}

  record Preferences(String userId, String theme) {}

  private MonadError<CompletableFutureKind.Witness, Throwable> futureMonad;
  private OptionalTMonad<CompletableFutureKind.Witness> optionalTMonad;

  @BeforeEach
  void setUp() {
    futureMonad = Instances.monadError(completableFuture());
    optionalTMonad = new OptionalTMonad<>(futureMonad);
  }

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

  @Nested
  @DisplayName("Part 1: OptionalT")
  class OptionalTExercises {

    /**
     * Why this is idiomatic: {@code OptionalT.fromKind} lifts {@code Kind<F, Optional<A>>} into the
     * transformer. Future + Optional becomes a single monad with a unified flatMap.
     *
     * <p>Alternative: chain {@code thenCompose} + {@code if-present} manually. Same outcome; the
     * transformer hides the plumbing.
     *
     * <p>Common wrong attempt: assume {@code OptionalT} runs the future. The transformer is a
     * description; runs at {@code .value()} extraction.
     */
    @Test
    @DisplayName("Exercise 1: fromKind lifts a Future<Optional> into OptionalT")
    void exercise1_fromKindLiftsFutureOptional() {
      var alice = fetchUser("alice");

      // SOLUTION: OptionalT.fromKind wraps an existing Kind<F, Optional<A>>.
      OptionalT<CompletableFutureKind.Witness, User> wrapped = OptionalT.fromKind(alice);

      var result = FUTURE.join(wrapped.value());
      assertThat(result).isPresent();
      assertThat(result.get().name()).isEqualTo("Alice");
    }

    /**
     * Why this is idiomatic: {@code For.from} threads OptionalT bindings through dependent steps.
     * The user lookup feeds the profile lookup; both run in the future without manual {@code
     * thenCompose}.
     *
     * <p>Alternative: nested {@code thenCompose} chains with {@code if-present}. Equivalent
     * runtime; the comprehension keeps the data flow visible.
     *
     * <p>Common wrong attempt: forget {@code OPTIONAL_T.narrow}. The {@code Kind} returned by
     * {@code yield} is widened; narrow before {@code value()}.
     */
    @Test
    @DisplayName("Exercise 2: For chains two async lookups")
    void exercise2_forChainsAsyncLookups() {
      // SOLUTION: For.yield returns a Kind; narrow before extracting .value().
      Kind<OptionalTKind.Witness<CompletableFutureKind.Witness>, Profile> workflow =
          For.from(optionalTMonad, OptionalT.fromKind(fetchUser("alice")))
              .from(user -> OptionalT.fromKind(fetchProfile(user.id())))
              .yield((user, profile) -> profile);

      var result = FUTURE.join(OPTIONAL_T.narrow(workflow).value());
      assertThat(result).isPresent();
      assertThat(result.get().userId()).isEqualTo("alice");
    }

    /**
     * Why this is idiomatic: an {@code Optional.empty()} from any step short- circuits the
     * comprehension. The future still completes; later OptionalT steps simply do not run.
     *
     * <p>Alternative: explicit {@code if-present} branches after each step. Equivalent; the
     * transformer does it automatically.
     *
     * <p>Common wrong attempt: assume the future itself can be empty. Optional-ness lives in the
     * inner type; the future always completes with a value.
     */
    @Test
    @DisplayName("Exercise 3: Empty short-circuits the chain")
    void exercise3_emptyShortCircuits() {
      // SOLUTION: same comprehension, but the first lookup returns empty so the second is skipped.
      Kind<OptionalTKind.Witness<CompletableFutureKind.Witness>, Profile> workflow =
          For.from(optionalTMonad, OptionalT.fromKind(fetchUser("missing")))
              .from(user -> OptionalT.fromKind(fetchProfile(user.id())))
              .yield((user, profile) -> profile);

      var result = FUTURE.join(OPTIONAL_T.narrow(workflow).value());
      assertThat(result).isEmpty();
    }

    /**
     * Why this is idiomatic: {@code handleErrorWith} on the OptionalT monad receives {@code Unit}
     * (absence carries no payload) and returns a fresh OptionalT. Defaults provide a present value
     * for empty cases.
     *
     * <p>Alternative: pattern-match the optional after running. Same answer; the transformer keeps
     * the recovery composable.
     *
     * <p>Common wrong attempt: call {@code orElse} on the inner Optional. Works for synchronous
     * code; the transformer-aware {@code handleErrorWith} keeps the future intact.
     */
    @Test
    @DisplayName("Exercise 4: handleErrorWith provides a default for empty")
    void exercise4_handleErrorWithProvidesDefault() {
      Kind<CompletableFutureKind.Witness, Optional<Preferences>> emptyLookup =
          FUTURE.widen(CompletableFuture.completedFuture(Optional.empty()));
      var attempt = OptionalT.fromKind(emptyLookup);
      var defaults = new Preferences("guest", "default-light");

      // SOLUTION: the recovery handler receives Unit (absence has no payload) and returns a
      // fresh OptionalT. No widen needed: OptionalT already implements Kind.
      Kind<OptionalTKind.Witness<CompletableFutureKind.Witness>, Preferences> recovered =
          optionalTMonad.handleErrorWith(
              attempt, (Unit v) -> OptionalT.some(futureMonad, defaults));

      var result = FUTURE.join(OPTIONAL_T.narrow(recovered).value());
      assertThat(result).isPresent();
      assertThat(result.get().theme()).isEqualTo("default-light");
    }
  }

  @Nested
  @DisplayName("Part 2: MaybeT")
  class MaybeTExercises {

    /**
     * Why this is idiomatic: {@code MaybeT.fromKind} mirrors {@code OptionalT} but for
     * higher-kinded-j's {@code Maybe}. Use it when the surrounding code already speaks in {@code
     * Maybe} for absence.
     *
     * <p>Alternative: {@code OptionalT} for {@code java.util.Optional}-shaped absence. Pick
     * whichever absence type the rest of the system uses.
     *
     * <p>Common wrong attempt: mix {@code Optional} and {@code Maybe} in the same transformer
     * pipeline. Convert at the boundary or pick one.
     */
    @Test
    @DisplayName("Exercise 5: MaybeT.fromKind for an async Maybe")
    void exercise5_maybeTFromKind() {
      Kind<CompletableFutureKind.Witness, Maybe<User>> bob =
          FUTURE.widen(CompletableFuture.completedFuture(Maybe.just(new User("bob", "Bob"))));

      // SOLUTION: MaybeT.fromKind mirrors OptionalT.fromKind for Higher-Kinded-J's Maybe.
      MaybeT<CompletableFutureKind.Witness, User> wrapped = MaybeT.fromKind(bob);

      var result = FUTURE.join(wrapped.value());
      assertThatMaybe(result).isJust();
      assertThatMaybe(result).hasValueSatisfying(u -> assertThat(u.name()).isEqualTo("Bob"));
    }
  }
}
