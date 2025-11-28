// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.optional_t;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.optional_t.OptionalTKindHelper.OPTIONAL_T;

import module java.base;
import module org.higherkindedj.core;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureMonad;
import org.higherkindedj.hkt.optional_t.OptionalT;
import org.higherkindedj.hkt.optional_t.OptionalTKind;
import org.higherkindedj.hkt.optional_t.OptionalTMonad;

/**
 * see {<a href="https://higher-kinded-j.github.io/optionalt_transformer.html">OptionalT
 * Transformer</a>}
 */
public class OptionalTExample {
  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    createExample();
    OptionalTAsyncExample.main(new String[0]);
  }

  public void createExample() {
    Monad<CompletableFutureKind.Witness> futureMonad = CompletableFutureMonad.INSTANCE;
    String presentValue = "Data";
    Integer numericValue = 123;

    Kind<CompletableFutureKind.Witness, Optional<String>> fOptional =
        FUTURE.widen(CompletableFuture.completedFuture(Optional.of(presentValue)));
    OptionalT<CompletableFutureKind.Witness, String> ot1 = OptionalT.fromKind(fOptional);

    OptionalT<CompletableFutureKind.Witness, String> ot2 =
        OptionalT.some(futureMonad, presentValue);

    OptionalT<CompletableFutureKind.Witness, String> ot3 = OptionalT.none(futureMonad);

    Optional<Integer> optInt = Optional.of(numericValue);
    OptionalT<CompletableFutureKind.Witness, Integer> ot4 =
        OptionalT.fromOptional(futureMonad, optInt);

    Optional<Integer> optEmpty = Optional.empty();
    OptionalT<CompletableFutureKind.Witness, Integer> ot4Empty =
        OptionalT.fromOptional(futureMonad, optEmpty);

    Kind<CompletableFutureKind.Witness, String> fValue =
        FUTURE.widen(CompletableFuture.completedFuture(presentValue));
    OptionalT<CompletableFutureKind.Witness, String> ot5 = OptionalT.liftF(futureMonad, fValue);

    Kind<CompletableFutureKind.Witness, String> fNullValue =
        FUTURE.widen(CompletableFuture.completedFuture(null));
    OptionalT<CompletableFutureKind.Witness, String> ot5Null =
        OptionalT.liftF(futureMonad, fNullValue);

    Kind<CompletableFutureKind.Witness, Optional<String>> wrappedFVO = ot1.value();
    CompletableFuture<Optional<String>> futureOptional = FUTURE.narrow(wrappedFVO);
    futureOptional.thenAccept(optStr -> System.out.println("ot1 result: " + optStr));
  }

  public static class OptionalTAsyncExample {

    static final Monad<CompletableFutureKind.Witness> futureMonad = CompletableFutureMonad.INSTANCE;
    // OptionalTMonad now uses Unit as its error type
    static final OptionalTMonad<CompletableFutureKind.Witness> optionalTFutureMonad =
        new OptionalTMonad<>(futureMonad);
    static final ExecutorService executor = Executors.newFixedThreadPool(2);

    public static Kind<CompletableFutureKind.Witness, Optional<User>> fetchUserAsync(
        String userId) {
      return FUTURE.widen(
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
      return FUTURE.widen(
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
      return FUTURE.widen(
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
                return "user1".equals(userId) && Math.random() > 0.3
                    ? Optional.of(new UserPreferences(userId, "dark"))
                    : Optional.empty();
              },
              executor));
    }

    public static OptionalT<CompletableFutureKind.Witness, UserPreferences> getFullUserPreferences(
        String userId) {
      OptionalT<CompletableFutureKind.Witness, User> userOT =
          OptionalT.fromKind(fetchUserAsync(userId));

      OptionalT<CompletableFutureKind.Witness, UserProfile> profileOT =
          OPTIONAL_T.narrow(
              optionalTFutureMonad.flatMap(
                  user -> OPTIONAL_T.widen(OptionalT.fromKind(fetchProfileAsync(user.id()))),
                  OPTIONAL_T.widen(userOT)));

      OptionalT<CompletableFutureKind.Witness, UserPreferences> preferencesOT =
          OPTIONAL_T.narrow(
              optionalTFutureMonad.flatMap(
                  profile ->
                      OPTIONAL_T.widen(OptionalT.fromKind(fetchPrefsAsync(profile.userId()))),
                  OPTIONAL_T.widen(profileOT)));
      return preferencesOT;
    }

    public static OptionalT<CompletableFutureKind.Witness, UserPreferences> getPrefsWithDefault(
        String userId) {
      OptionalT<CompletableFutureKind.Witness, UserPreferences> prefsAttemptOT =
          getFullUserPreferences(userId);

      Kind<OptionalTKind.Witness<CompletableFutureKind.Witness>, UserPreferences>
          recoveredPrefsOTKind =
              optionalTFutureMonad.handleErrorWith(
                  OPTIONAL_T.widen(prefsAttemptOT),
                  (Unit unitVal) -> {
                    System.out.println(
                        "Preferences not found for " + userId + ", providing default.");
                    UserPreferences defaultPrefs = new UserPreferences(userId, "default-light");
                    return OPTIONAL_T.widen(OptionalT.some(futureMonad, defaultPrefs));
                  });
      return OPTIONAL_T.narrow(recoveredPrefsOTKind);
    }

    /** Java 25 instance main method - no static modifier or String[] args required. */
    void main() {
      System.out.println("--- Attempting to get preferences for existing user (user1) ---");
      OptionalT<CompletableFutureKind.Witness, UserPreferences> resultUser1OT =
          getFullUserPreferences("user1");
      CompletableFuture<Optional<UserPreferences>> future1 = FUTURE.narrow(resultUser1OT.value());

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
      CompletableFuture<Optional<UserPreferences>> future2 = FUTURE.narrow(resultUser2OT.value());

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
          FUTURE.narrow(resultUser1WithDefaultOT.value());

      future3.whenComplete(
          (optPrefs, ex) -> {
            if (ex != null) {
              System.err.println("Error for user1 (with default): " + ex.getMessage());
            } else {
              System.out.println(
                  "User1 Preferences (with default): "
                      + optPrefs
                          .map(UserPreferences::toString)
                          .orElse("THIS SHOULD NOT HAPPEN if default works"));
            }
            try {
              TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            executor.shutdown();
          });
    }

    public record User(String id, String name) {}

    public record UserProfile(String userId, String bio) {}

    public record UserPreferences(String userId, String theme) {}
  }
}
