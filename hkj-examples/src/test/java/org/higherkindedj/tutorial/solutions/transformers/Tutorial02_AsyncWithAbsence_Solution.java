// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.transformers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.optional_t.OptionalTKindHelper.OPTIONAL_T;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.expression.For;
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
 * Solutions for Tutorial 02: Async with Absence.
 *
 * <p>This file contains the completed solutions for all exercises. Compare your answers with these
 * solutions after attempting the tutorial.
 */
@DisplayName("Tutorial 02: Async with Absence - Solutions")
public class Tutorial02_AsyncWithAbsence_Solution {

  record User(String id, String name) {}

  record Profile(String userId, String avatarUrl) {}

  record Preferences(String userId, String theme) {}

  private CompletableFutureMonad futureMonad;
  private OptionalTMonad<CompletableFutureKind.Witness> optionalTMonad;

  @BeforeEach
  void setUp() {
    futureMonad = CompletableFutureMonad.INSTANCE;
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

    @Test
    @DisplayName("Exercise 5: MaybeT.fromKind for an async Maybe")
    void exercise5_maybeTFromKind() {
      Kind<CompletableFutureKind.Witness, Maybe<User>> bob =
          FUTURE.widen(CompletableFuture.completedFuture(Maybe.just(new User("bob", "Bob"))));

      // SOLUTION: MaybeT.fromKind mirrors OptionalT.fromKind for Higher-Kinded-J's Maybe.
      MaybeT<CompletableFutureKind.Witness, User> wrapped = MaybeT.fromKind(bob);

      var result = FUTURE.join(wrapped.value());
      assertThat(result.isJust()).isTrue();
      assertThat(result.get().name()).isEqualTo("Bob");
    }
  }
}
