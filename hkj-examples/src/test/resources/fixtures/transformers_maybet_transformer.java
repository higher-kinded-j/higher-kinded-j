// Fixture for hkj-book/src/transformers/maybet_transformer.md
//
// The page looks up a user and then their preferences, three ways: nested futures, a synchronous
// MaybePath, and MaybeT. Each way needs the lookup in a different shape, so all three are declared
// here. `fetchUserAsync`/`fetchPreferencesAsync` are instance methods because the worked example
// declares them itself, and a snippet's method can only replace an inherited one of the same kind.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.maybe_t.MaybeTKindHelper.MAYBE_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe_t.MaybeT;
import org.higherkindedj.hkt.maybe_t.MaybeTKind;
import org.higherkindedj.hkt.optional.OptionalKind;

record User(String id, String name) {}

record UserPreferences(String theme) {}

class Fixture {

  static final String userId = "user123";

  static final MonadError<OptionalKind.Witness, Unit> optMonad = Instances.monadError(optional());

  /** The plain-future lookups the "problem" snippet nests by hand. */
  static CompletableFuture<Maybe<User>> fetchUserFuture(String userId) {
    return CompletableFuture.completedFuture(Maybe.just(new User(userId, "Alice")));
  }

  static CompletableFuture<Maybe<UserPreferences>> fetchPreferencesFuture(String userId) {
    return CompletableFuture.completedFuture(Maybe.just(new UserPreferences("dark")));
  }

  /** The synchronous lookups MaybePath composes. */
  static Maybe<User> lookupUser(String userId) {
    return Maybe.just(new User(userId, "Alice"));
  }

  static Maybe<UserPreferences> lookupPreferences(String userId) {
    return Maybe.just(new UserPreferences("dark"));
  }

  /** The Kind-shaped lookups MaybeT wraps. */
  Kind<CompletableFutureKind.Witness, Maybe<User>> fetchUserAsync(String userId) {
    return FUTURE.widen(fetchUserFuture(userId));
  }

  Kind<CompletableFutureKind.Witness, Maybe<UserPreferences>> fetchPreferencesAsync(
      String userId) {
    return FUTURE.widen(fetchPreferencesFuture(userId));
  }
}
