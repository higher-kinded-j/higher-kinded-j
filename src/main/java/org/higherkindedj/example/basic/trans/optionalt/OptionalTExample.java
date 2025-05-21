// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.trans.optionalt;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureKindHelper;
import org.higherkindedj.hkt.future.CompletableFutureMonad;
import org.higherkindedj.hkt.trans.optional_t.OptionalT;
import org.higherkindedj.hkt.trans.optional_t.OptionalTKind;
import org.higherkindedj.hkt.trans.optional_t.OptionalTKindHelper;
import org.higherkindedj.hkt.trans.optional_t.OptionalTMonad;

/**
 * see {<a href="https://higher-kinded-j.github.io/optionalt_transformer.html">OptionalT
 * Transformer</a>}
 */
public class OptionalTExample {
  public static void main(String[] args) {
    OptionalTExample example = new OptionalTExample();
    example.createExample();
  }

  public void createExample() {
    // --- Setup ---
    // Outer Monad F = CompletableFutureKind.Witness
    Monad<CompletableFutureKind.Witness> futureMonad = new CompletableFutureMonad();
    String presentValue = "Data";
    Integer numericValue = 123;

    // 1. `OptionalT.fromKind(Kind<F, Optional<A>> value)`
    //    Wraps an existing F<Optional<A>>.
    Kind<CompletableFutureKind.Witness, Optional<String>> fOptional =
        CompletableFutureKindHelper.wrap(
            CompletableFuture.completedFuture(Optional.of(presentValue)));
    OptionalT<CompletableFutureKind.Witness, String> ot1 = OptionalT.fromKind(fOptional);
    // Value: CompletableFuture<Optional.of("Data")>

    // 2. `OptionalT.some(Monad<F> monad, A a)`
    //    Creates an OptionalT with a present value, F<Optional.of(a)>.
    OptionalT<CompletableFutureKind.Witness, String> ot2 =
        OptionalT.some(futureMonad, presentValue);
    // Value: CompletableFuture<Optional.of("Data")>

    // 3. `OptionalT.none(Monad<F> monad)`
    //    Creates an OptionalT representing an absent value, F<Optional.empty()>.
    OptionalT<CompletableFutureKind.Witness, String> ot3 = OptionalT.none(futureMonad);
    // Value: CompletableFuture<Optional.empty()>

    // 4. `OptionalT.fromOptional(Monad<F> monad, Optional<A> optional)`
    //    Lifts a plain java.util.Optional into OptionalT, F<Optional<A>>.
    Optional<Integer> optInt = Optional.of(numericValue);
    OptionalT<CompletableFutureKind.Witness, Integer> ot4 =
        OptionalT.fromOptional(futureMonad, optInt);
    // Value: CompletableFuture<Optional.of(123)>

    Optional<Integer> optEmpty = Optional.empty();
    OptionalT<CompletableFutureKind.Witness, Integer> ot4Empty =
        OptionalT.fromOptional(futureMonad, optEmpty);
    // Value: CompletableFuture<Optional.empty()>

    // 5. `OptionalT.liftF(Monad<F> monad, Kind<F, A> fa)`
    //    Lifts an F<A> into OptionalT. If A is null, it becomes F<Optional.empty()>, otherwise
    // F<Optional.of(A)>.
    Kind<CompletableFutureKind.Witness, String> fValue =
        CompletableFutureKindHelper.wrap(CompletableFuture.completedFuture(presentValue));
    OptionalT<CompletableFutureKind.Witness, String> ot5 = OptionalT.liftF(futureMonad, fValue);
    // Value: CompletableFuture<Optional.of("Data   ")>

    Kind<CompletableFutureKind.Witness, String> fNullValue =
        CompletableFutureKindHelper.wrap(CompletableFuture.completedFuture(null)); // F<null>
    OptionalT<CompletableFutureKind.Witness, String> ot5Null =
        OptionalT.liftF(futureMonad, fNullValue);
    // Value: CompletableFuture<Optional.empty()> (because the value inside F was null)

    // Accessing the wrapped value:
    Kind<CompletableFutureKind.Witness, Optional<String>> wrappedFVO = ot1.value();
    CompletableFuture<Optional<String>> futureOptional =
        CompletableFutureKindHelper.unwrap(wrappedFVO);
    futureOptional.thenAccept(optStr -> System.out.println("ot1 result: " + optStr));
  }

  public static class OptionalTAsyncExample {

    // --- Monad Setup ---
    static final Monad<CompletableFutureKind.Witness> futureMonad = new CompletableFutureMonad();
    static final OptionalTMonad<CompletableFutureKind.Witness> optionalTFutureMonad =
        new OptionalTMonad<>(futureMonad);
    static final ExecutorService executor = Executors.newFixedThreadPool(2);

    public static Kind<CompletableFutureKind.Witness, Optional<User>> fetchUserAsync(
        String userId) {
      return CompletableFutureKindHelper.wrap(
          CompletableFuture.supplyAsync(
              () -> {
                System.out.println(
                    "Fetching user " + userId + " on " + Thread.currentThread().getName());
                try {
                  TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException e) {
                  /* ignore */
                }
                return "user1".equals(userId)
                    ? Optional.of(new User(userId, "Alice"))
                    : Optional.empty();
              },
              executor));
    }

    public static Kind<CompletableFutureKind.Witness, Optional<UserProfile>> fetchProfileAsync(
        String userId) {
      return CompletableFutureKindHelper.wrap(
          CompletableFuture.supplyAsync(
              () -> {
                System.out.println(
                    "Fetching profile for " + userId + " on " + Thread.currentThread().getName());
                try {
                  TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException e) {
                  /* ignore */
                }
                return "user1".equals(userId)
                    ? Optional.of(new UserProfile(userId, "Loves HKJ"))
                    : Optional.empty();
              },
              executor));
    }

    public static Kind<CompletableFutureKind.Witness, Optional<UserPreferences>> fetchPrefsAsync(
        String userId) {
      return CompletableFutureKindHelper.wrap(
          CompletableFuture.supplyAsync(
              () -> {
                System.out.println(
                    "Fetching preferences for "
                        + userId
                        + " on "
                        + Thread.currentThread().getName());
                try {
                  TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException e) {
                  /* ignore */
                }
                // Simulate preferences sometimes missing even for a valid user
                return "user1".equals(userId) && Math.random() > 0.3
                    ? Optional.of(new UserPreferences(userId, "dark"))
                    : Optional.empty();
              },
              executor));
    }

    // --- Service Stubs (simulating async calls returning Future<Optional<T>>) ---

    // --- Workflow using OptionalT ---
    public static OptionalT<CompletableFutureKind.Witness, UserPreferences> getFullUserPreferences(
        String userId) {
      // Start by fetching the user, lifting into OptionalT
      OptionalT<CompletableFutureKind.Witness, User> userOT =
          OptionalT.fromKind(fetchUserAsync(userId));

      // If user exists, fetch profile
      OptionalT<CompletableFutureKind.Witness, UserProfile> profileOT =
          OptionalTKindHelper.unwrap(
              optionalTFutureMonad.flatMap(
                  user ->
                      OptionalTKindHelper.wrap(OptionalT.fromKind(fetchProfileAsync(user.id()))),
                  OptionalTKindHelper.wrap(userOT)));

      // If profile exists, fetch preferences
      OptionalT<CompletableFutureKind.Witness, UserPreferences> preferencesOT =
          OptionalTKindHelper.unwrap(
              optionalTFutureMonad.flatMap(
                  profile ->
                      OptionalTKindHelper.wrap(
                          OptionalT.fromKind(fetchPrefsAsync(profile.userId()))),
                  OptionalTKindHelper.wrap(profileOT)));
      return preferencesOT;
    }

    // Workflow with recovery / default
    public static OptionalT<CompletableFutureKind.Witness, UserPreferences> getPrefsWithDefault(
        String userId) {
      OptionalT<CompletableFutureKind.Witness, UserPreferences> prefsAttemptOT =
          getFullUserPreferences(userId);

      Kind<OptionalTKind.Witness<CompletableFutureKind.Witness>, UserPreferences>
          recoveredPrefsOTKind =
              optionalTFutureMonad.handleErrorWith(
                  OptionalTKindHelper.wrap(prefsAttemptOT),
                  (Void v) -> { // This lambda is called if prefsAttemptOT results in
                    // F<Optional.empty()>
                    System.out.println(
                        "Preferences not found for " + userId + ", providing default.");
                    // Lift a default preference into OptionalT
                    UserPreferences defaultPrefs = new UserPreferences(userId, "default-light");
                    return OptionalTKindHelper.wrap(OptionalT.some(futureMonad, defaultPrefs));
                  });
      return OptionalTKindHelper.unwrap(recoveredPrefsOTKind);
    }

    public static void main(String[] args) {
      System.out.println("--- Attempting to get preferences for existing user (user1) ---");
      OptionalT<CompletableFutureKind.Witness, UserPreferences> resultUser1OT =
          getFullUserPreferences("user1");
      CompletableFuture<Optional<UserPreferences>> future1 =
          CompletableFutureKindHelper.unwrap(resultUser1OT.value());

      future1.whenComplete(
          (optPrefs, ex) -> {
            if (ex != null) {
              System.err.println("Error for user1: " + ex.getMessage());
            } else {
              System.out.println(
                  "User1 Preferences: "
                      + optPrefs.map(UserPreferences::toString).orElse("NOT FOUND"));
            }
          });

      System.out.println("\n--- Attempting to get preferences for non-existing user (user2) ---");
      OptionalT<CompletableFutureKind.Witness, UserPreferences> resultUser2OT =
          getFullUserPreferences("user2");
      CompletableFuture<Optional<UserPreferences>> future2 =
          CompletableFutureKindHelper.unwrap(resultUser2OT.value());

      future2.whenComplete(
          (optPrefs, ex) -> {
            if (ex != null) {
              System.err.println("Error for user2: " + ex.getMessage());
            } else {
              System.out.println(
                  "User2 Preferences: "
                      + optPrefs.map(UserPreferences::toString).orElse("NOT FOUND (as expected)"));
            }
          });

      System.out.println("\n--- Attempting to get preferences for user1 WITH DEFAULT ---");
      OptionalT<CompletableFutureKind.Witness, UserPreferences> resultUser1WithDefaultOT =
          getPrefsWithDefault("user1");
      CompletableFuture<Optional<UserPreferences>> future3 =
          CompletableFutureKindHelper.unwrap(resultUser1WithDefaultOT.value());

      future3.whenComplete(
          (optPrefs, ex) -> {
            if (ex != null) {
              System.err.println("Error for user1 (with default): " + ex.getMessage());
            } else {
              // This will either be the fetched prefs or the default.
              System.out.println(
                  "User1 Preferences (with default): "
                      + optPrefs
                          .map(UserPreferences::toString)
                          .orElse("THIS SHOULD NOT HAPPEN if default works"));
            }
            // Wait for async operations to complete for demonstration
            try {
              TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            executor.shutdown();
          });
    }

    // --- Domain Model ---
    record User(String id, String name) {}

    record UserProfile(String userId, String bio) {}

    record UserPreferences(String userId, String theme) {}
  }
}
