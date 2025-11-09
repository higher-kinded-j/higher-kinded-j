// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.maybe_t;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.maybe_t.MaybeTKindHelper.MAYBE_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe_t.MaybeT;
import org.higherkindedj.hkt.maybe_t.MaybeTKind;
import org.higherkindedj.hkt.maybe_t.MaybeTMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;

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
    OptionalMonad optMonad = OptionalMonad.INSTANCE;
    String presentValue = "Hello";

    MaybeT<OptionalKind.Witness, String> mtJust = MaybeT.just(optMonad, presentValue);

    MaybeT<OptionalKind.Witness, String> mtNothing = MaybeT.nothing(optMonad);

    Maybe<Integer> plainMaybe = Maybe.just(123);
    MaybeT<OptionalKind.Witness, Integer> mtFromMaybe = MaybeT.fromMaybe(optMonad, plainMaybe);

    Maybe<Integer> plainNothing = Maybe.nothing();
    MaybeT<OptionalKind.Witness, Integer> mtFromMaybeNothing =
        MaybeT.fromMaybe(optMonad, plainNothing);

    Kind<OptionalKind.Witness, String> outerOptional = OPTIONAL.widen(Optional.of("World"));
    MaybeT<OptionalKind.Witness, String> mtLiftF = MaybeT.liftF(optMonad, outerOptional);

    Kind<OptionalKind.Witness, String> outerEmptyOptional = OPTIONAL.widen(Optional.empty());
    MaybeT<OptionalKind.Witness, String> mtLiftFEmpty = MaybeT.liftF(optMonad, outerEmptyOptional);

    Kind<OptionalKind.Witness, Maybe<String>> nestedKind =
        OPTIONAL.widen(Optional.of(Maybe.just("Present")));
    MaybeT<OptionalKind.Witness, String> mtFromKind = MaybeT.fromKind(nestedKind);

    Kind<OptionalKind.Witness, Maybe<String>> wrappedValue = mtJust.value();
    Optional<Maybe<String>> unwrappedOptional = OPTIONAL.narrow(wrappedValue);
    System.out.println("mtJust unwrapped: " + unwrappedOptional);
  }

  public static class MaybeTAsyncExample {
    Monad<CompletableFutureKind.Witness> futureMonad = CompletableFutureMonad.INSTANCE;
    MonadError<MaybeTKind.Witness<CompletableFutureKind.Witness>, Unit> maybeTMonad =
        new MaybeTMonad<>(futureMonad);

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
      return FUTURE.widen(future);
    }

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
                return Maybe.nothing();
              });
      return FUTURE.widen(future);
    }

    Kind<CompletableFutureKind.Witness, Maybe<UserPreferences>> getUserPreferencesWorkflow(
        String userIdToFetch) {

      Kind<MaybeTKind.Witness<CompletableFutureKind.Witness>, User> userMT =
          MAYBE_T.widen(MaybeT.fromKind(fetchUserAsync(userIdToFetch)));

      Kind<MaybeTKind.Witness<CompletableFutureKind.Witness>, UserPreferences> preferencesMT =
          maybeTMonad.flatMap(
              user -> {
                System.out.println("User found: " + user.name() + ". Now fetching preferences.");
                return MAYBE_T.widen(MaybeT.fromKind(fetchPreferencesAsync(user.id())));
              },
              userMT);

      Kind<MaybeTKind.Witness<CompletableFutureKind.Witness>, UserPreferences>
          preferencesWithDefaultMT =
              maybeTMonad.handleErrorWith(
                  preferencesMT,
                  (Unit unitVal) -> {
                    System.out.println("Preferences not found, attempting to use default.");
                    // For this example, if user was found but prefs not, we still return Nothing.
                    // A real app might provide actual default preferences here using
                    // MaybeT.just(...)
                    return maybeTMonad.raiseError(Unit.INSTANCE);
                  });

      MaybeT<CompletableFutureKind.Witness, UserPreferences> finalMaybeT =
          MAYBE_T.narrow(preferencesWithDefaultMT);
      return finalMaybeT.value();
    }

    public void asyncExample() {
      System.out.println("--- Fetching preferences for known user (user123) ---");
      Kind<CompletableFutureKind.Witness, Maybe<UserPreferences>> resultKnownUserKind =
          getUserPreferencesWorkflow("user123");
      Maybe<UserPreferences> resultKnownUser = FUTURE.join(resultKnownUserKind);
      System.out.println("Known User Result: " + resultKnownUser);

      System.out.println("\n--- Fetching preferences for unknown user (user999) ---");
      Kind<CompletableFutureKind.Witness, Maybe<UserPreferences>> resultUnknownUserKind =
          getUserPreferencesWorkflow("user999");
      Maybe<UserPreferences> resultUnknownUser = FUTURE.join(resultUnknownUserKind);
      System.out.println("Unknown User Result: " + resultUnknownUser);
    }

    record User(String id, String name) {}

    record UserPreferences(String userId, String theme) {}
  }
}
