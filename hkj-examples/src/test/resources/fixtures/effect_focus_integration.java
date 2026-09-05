// Fixture for hkj-book/src/effect/focus_integration.md
//
// The page bridges the optics side and the effect side over one user/company graph: lifting a
// FocusPath into each Path type, and focusing a Path through an optic. The records and the
// ready-made paths live here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.ListPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.StreamPath;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;

@GenerateLenses
@GenerateFocus
record User(String name, Optional<String> email) {}

@GenerateLenses
@GenerateFocus
record Company(String name, List<User> employees) {}

record Error(String message) {}

final class MissingEmailException extends RuntimeException {

  MissingEmailException() {
    super("No email");
  }
}

class Fixture {

  static final String userId = "u-1";

  static final User alice = new User("Alice", Optional.of("alice@example.com"));

  static final User bob = new User("Bob", Optional.empty());

  static final User charlie = new User("Charlie", Optional.of("charlie@example.com"));

  static final EitherPath<Error, User> userResult = Path.either(Either.right(alice));

  static final TryPath<User> userTryPath = Path.success(alice);

  static final MaybePath<User> userMaybePath = Path.just(alice);

  static EitherPath<Error, User> fetchUser(String id) {
    return Path.either(Either.right(alice));
  }
}
