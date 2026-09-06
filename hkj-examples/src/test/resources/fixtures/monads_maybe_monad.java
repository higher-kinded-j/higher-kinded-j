// Fixture for hkj-book/src/monads/maybe_monad.md
//
// The page works through Maybe's constructors, its Kind bridge and its MonadError instance, then
// bridges to Either and to MaybePath. One user lookup and one order lookup carry all of it.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.jspecify.annotations.Nullable;

record User(String id, @Nullable String name) {}

record Order(String id) {}

record UserError(String message) {}

class Fixture {

  static final MonadError<MaybeKind.Witness, Unit> maybeMonad =
      Instances.monadError(maybe());

  static final String id = "u-1";

  static final String userId = "u-1";

  static final List<String> ids = List.of("o-1", "o-2");

  static final Repository repository = new Repository();

  static Maybe<User> findUser(String id) {
    return Maybe.just(new User(id, "Ada"));
  }

  static final class Repository {

    Maybe<Order> findOrder(String id) {
      return Maybe.just(new Order(id));
    }
  }
}
