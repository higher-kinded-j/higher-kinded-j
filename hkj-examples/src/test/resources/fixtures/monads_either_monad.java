// Fixture for hkj-book/src/monads/either_monad.md
//
// One lookup that can fail and one order built from it carry the page's Either examples.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;

import java.util.NoSuchElementException;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;

record User(String id) {}

record Order(String id) {}

record Error(String message) {}

class Fixture {

  static final String id = "u-1";

  static final Either<String, Integer> success = Either.right(123);

  static final Either<String, Integer> failure = Either.left("File not found");

  static Either<Error, User> findUser(String id) {
    return Either.right(new User(id));
  }

  static Either<Error, Order> createOrder(User user) {
    return Either.right(new Order("o-1"));
  }

  static EitherPath<Error, Order> createOrderPath(User user) {
    return Path.right(new Order("o-1"));
  }
}
