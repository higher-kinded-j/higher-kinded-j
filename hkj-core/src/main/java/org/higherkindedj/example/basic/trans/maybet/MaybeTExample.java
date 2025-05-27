// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.trans.maybet;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureKindHelper;
import org.higherkindedj.hkt.future.CompletableFutureMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.trans.maybe_t.MaybeT;
import org.higherkindedj.hkt.trans.maybe_t.MaybeTKind;
import org.higherkindedj.hkt.trans.maybe_t.MaybeTKindHelper;
import org.higherkindedj.hkt.trans.maybe_t.MaybeTMonad;

/**
 * see {<a href="https://higher-kinded-j.github.io/maybet_transformer.html">MaybeT Transformer</a>}
 */
public class MaybeTExample {

  public static void main(String[] args) {
    MaybeTExample example = new MaybeTExample();
    example.createExample();
    MaybeTAsyncExample asyncExample = new MaybeTAsyncExample();
    asyncExample.asyncExample();
  }

  public void createExample() {
    Monad<OptionalKind.Witness> optMonad = new OptionalMonad(); // Outer Monad F=Optional
    String presentValue = "Hello";

    // 1. Lifting a non-null value: Optional<Just(value)>
    MaybeT<OptionalKind.Witness, String> mtJust = MaybeT.just(optMonad, presentValue);
    // Resulting wrapped value: Optional.of(Maybe.just("Hello"))

    // 2. Creating a 'Nothing' state: Optional<Nothing>
    MaybeT<OptionalKind.Witness, String> mtNothing = MaybeT.nothing(optMonad);
    // Resulting wrapped value: Optional.of(Maybe.nothing())

    // 3. Lifting a plain Maybe: Optional<Maybe(input)>
    Maybe<Integer> plainMaybe = Maybe.just(123);
    MaybeT<OptionalKind.Witness, Integer> mtFromMaybe = MaybeT.fromMaybe(optMonad, plainMaybe);
    // Resulting wrapped value: Optional.of(Maybe.just(123))

    Maybe<Integer> plainNothing = Maybe.nothing();
    MaybeT<OptionalKind.Witness, Integer> mtFromMaybeNothing =
        MaybeT.fromMaybe(optMonad, plainNothing);
    // Resulting wrapped value: Optional.of(Maybe.nothing())

    // 4. Lifting an outer monad value F<A>: Optional<Maybe<A>> (using fromNullable)
    Kind<OptionalKind.Witness, String> outerOptional =
        OptionalKindHelper.wrap(Optional.of("World"));
    MaybeT<OptionalKind.Witness, String> mtLiftF = MaybeT.liftF(optMonad, outerOptional);
    // Resulting wrapped value: Optional.of(Maybe.just("World"))

    Kind<OptionalKind.Witness, String> outerEmptyOptional =
        OptionalKindHelper.wrap(Optional.empty());
    MaybeT<OptionalKind.Witness, String> mtLiftFEmpty = MaybeT.liftF(optMonad, outerEmptyOptional);
    // Resulting wrapped value: Optional.of(Maybe.nothing())

    // 5. Wrapping an existing nested Kind: F<Maybe<A>>
    Kind<OptionalKind.Witness, Maybe<String>> nestedKind =
        OptionalKindHelper.wrap(Optional.of(Maybe.just("Present")));
    MaybeT<OptionalKind.Witness, String> mtFromKind = MaybeT.fromKind(nestedKind);
    // Resulting wrapped value: Optional.of(Maybe.just("Present"))

    // Accessing the wrapped value:
    Kind<OptionalKind.Witness, Maybe<String>> wrappedValue = mtJust.value();
    Optional<Maybe<String>> unwrappedOptional = OptionalKindHelper.unwrap(wrappedValue);
    // unwrappedOptional is Optional.of(Maybe.just("Hello"))
  }

  public static class MaybeTAsyncExample {
    // --- Setup ---
    Monad<CompletableFutureKind.Witness> futureMonad = new CompletableFutureMonad();
    MonadError<MaybeTKind.Witness<CompletableFutureKind.Witness>, Void> maybeTMonad =
        new MaybeTMonad<>(futureMonad);

    // Simulates fetching a user asynchronously
    Kind<CompletableFutureKind.Witness, Maybe<User>> fetchUserAsync(String userId) {
      System.out.println("Fetching user: " + userId);
      CompletableFuture<Maybe<User>> future =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException e) {
                  /* ignore */
                }
                if ("user123".equals(userId)) {
                  return Maybe.just(new User(userId, "Alice"));
                }
                return Maybe.nothing();
              });
      return CompletableFutureKindHelper.wrap(future);
    }

    // Simulates fetching user preferences asynchronously
    Kind<CompletableFutureKind.Witness, Maybe<UserPreferences>> fetchPreferencesAsync(
        String userId) {
      System.out.println("Fetching preferences for user: " + userId);
      CompletableFuture<Maybe<UserPreferences>> future =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  TimeUnit.MILLISECONDS.sleep(30);
                } catch (InterruptedException e) {
                  /* ignore */
                }
                if ("user123".equals(userId)) {
                  return Maybe.just(new UserPreferences(userId, "dark-mode"));
                }
                return Maybe.nothing(); // No preferences for other users or if user fetch failed
              });
      return CompletableFutureKindHelper.wrap(future);
    }

    // --- Service Stubs (returning Future<Maybe<T>>) ---

    // Function to run the workflow for a given userId
    Kind<CompletableFutureKind.Witness, Maybe<UserPreferences>> getUserPreferencesWorkflow(
        String userIdToFetch) {

      // Step 1: Fetch User
      // Directly use MaybeT.fromKind as fetchUserAsync already returns F<Maybe<User>>
      Kind<MaybeTKind.Witness<CompletableFutureKind.Witness>, User> userMT =
          MaybeTKindHelper.wrap(MaybeT.fromKind(fetchUserAsync(userIdToFetch)));

      // Step 2: Fetch Preferences if User was found
      Kind<MaybeTKind.Witness<CompletableFutureKind.Witness>, UserPreferences> preferencesMT =
          maybeTMonad.flatMap(
              user -> { // This lambda is only called if userMT contains F<Just(user)>
                System.out.println("User found: " + user.name() + ". Now fetching preferences.");
                // fetchPreferencesAsync returns Kind<CompletableFutureKind.Witness,
                // Maybe<UserPreferences>>
                // which is F<Maybe<A>>, so we can wrap it directly.
                return MaybeTKindHelper.wrap(MaybeT.fromKind(fetchPreferencesAsync(user.id())));
              },
              userMT // Input to flatMap
              );

      // Try to recover if preferences are Nothing, but user was found (conceptual)
      Kind<MaybeTKind.Witness<CompletableFutureKind.Witness>, UserPreferences>
          preferencesWithDefaultMT =
              maybeTMonad.handleErrorWith(
                  preferencesMT,
                  (Void v) -> { // Handler for Nothing
                    System.out.println("Preferences not found, attempting to use default.");
                    // We need userId here. For simplicity, let's assume we could get it or just
                    // return nothing.
                    // This example shows returning nothing again if we can't provide a default.
                    // A real scenario might try to fetch default preferences or construct one.
                    return maybeTMonad.raiseError(
                        null); // Still Nothing, or could be MaybeT.just(defaultPrefs)
                  });

      // Unwrap the final MaybeT to get the underlying Future<Maybe<UserPreferences>>
      MaybeT<CompletableFutureKind.Witness, UserPreferences> finalMaybeT =
          MaybeTKindHelper.unwrap(preferencesWithDefaultMT); // or preferencesMT if no recovery
      return finalMaybeT.value();
    }

    public void asyncExample() {
      System.out.println("--- Fetching preferences for known user (user123) ---");
      Kind<CompletableFutureKind.Witness, Maybe<UserPreferences>> resultKnownUserKind =
          getUserPreferencesWorkflow("user123");
      Maybe<UserPreferences> resultKnownUser =
          CompletableFutureKindHelper.join(resultKnownUserKind);
      System.out.println("Known User Result: " + resultKnownUser);
      // Expected: Just(UserPreferences[userId=user123, theme=dark-mode])

      System.out.println("\n--- Fetching preferences for unknown user (user999) ---");
      Kind<CompletableFutureKind.Witness, Maybe<UserPreferences>> resultUnknownUserKind =
          getUserPreferencesWorkflow("user999");
      Maybe<UserPreferences> resultUnknownUser =
          CompletableFutureKindHelper.join(resultUnknownUserKind);
      System.out.println("Unknown User Result: " + resultUnknownUser);
      // Expected: Nothing
    }

    // --- Workflow Definition using MaybeT ---

    // --- Domain Model ---
    record User(String id, String name) {}

    record UserPreferences(String userId, String theme) {}
  }
}
