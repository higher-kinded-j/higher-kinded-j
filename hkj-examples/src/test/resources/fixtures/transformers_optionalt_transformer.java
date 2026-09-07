// Fixture for hkj-book/src/transformers/optionalt_transformer.md
//
// The page walks user -> profile -> preferences three ways: nested futures, a synchronous
// OptionalPath, and OptionalT. Each way needs the lookups in a different shape, so all three are
// declared here. The async trio are instance methods because the worked example declares one of
// them itself, and a snippet's method can only replace an inherited one of the same kind.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.optional_t.OptionalTKindHelper.OPTIONAL_T;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional_t.OptionalT;
import org.higherkindedj.hkt.optional_t.OptionalTKind;

record User(String id, String name) {}

record Profile(String userId) {}

record UserPreferences(String userId, String theme) {}

class Fixture {

  static final String userId = "user1";

  static final MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
      Instances.monadError(completableFuture());

  static final MonadError<OptionalTKind.Witness<CompletableFutureKind.Witness>, Unit>
      optionalTMonad = Instances.optionalT(futureMonad);

  static final OptionalT<CompletableFutureKind.Witness, String> asyncOt =
      OptionalT.some(futureMonad, "Data");

  /** The plain-future lookups the "problem" snippet nests by hand. */
  static CompletableFuture<Optional<User>> fetchUserFuture(String userId) {
    return CompletableFuture.completedFuture(Optional.of(new User(userId, "Alice")));
  }

  static CompletableFuture<Optional<Profile>> fetchProfileFuture(String userId) {
    return CompletableFuture.completedFuture(Optional.of(new Profile(userId)));
  }

  static CompletableFuture<Optional<UserPreferences>> fetchPrefsFuture(String userId) {
    return CompletableFuture.completedFuture(Optional.of(new UserPreferences(userId, "dark")));
  }

  /** The synchronous lookups OptionalPath composes. */
  static Optional<User> lookupUser(String userId) {
    return Optional.of(new User(userId, "Alice"));
  }

  static Optional<Profile> lookupProfile(String userId) {
    return Optional.of(new Profile(userId));
  }

  static Optional<UserPreferences> lookupPrefs(String userId) {
    return Optional.of(new UserPreferences(userId, "dark"));
  }

  /** The Kind-shaped lookups OptionalT wraps. */
  Kind<CompletableFutureKind.Witness, Optional<User>> fetchUserAsync(String userId) {
    return FUTURE.widen(fetchUserFuture(userId));
  }

  Kind<CompletableFutureKind.Witness, Optional<Profile>> fetchProfileAsync(String userId) {
    return FUTURE.widen(fetchProfileFuture(userId));
  }

  Kind<CompletableFutureKind.Witness, Optional<UserPreferences>> fetchPrefsAsync(String userId) {
    return FUTURE.widen(fetchPrefsFuture(userId));
  }

  Kind<OptionalTKind.Witness<CompletableFutureKind.Witness>, UserPreferences>
      getFullUserPreferences(String userId) {
    return For.from(optionalTMonad, OPTIONAL_T.widen(OptionalT.fromKind(fetchUserAsync(userId))))
        .from(user -> OPTIONAL_T.widen(OptionalT.fromKind(fetchPrefsAsync(user.id()))))
        .yield((user, prefs) -> prefs);
  }
}
