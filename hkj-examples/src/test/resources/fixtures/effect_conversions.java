// Fixture for hkj-book/src/effect/conversions.md
//
// The conversion matrix page moves one small user/order domain between every Path type, so the
// domain and the services around it live here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.GenericPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.jspecify.annotations.Nullable;

record User(String name) {}

record Order(String id) {}

record UserDto(String name) {

  static UserDto from(User user) {
    return new UserDto(user.name());
  }
}

record HttpError(int status) {}

record OrderInput(String sku) {}

record ValidatedOrder(String sku) {}

record Config(String name) {}

record ConfigError(String message) {}

record Data(String value) {}

record UserError(String message) {

  static final UserError NOT_FOUND = new UserError("not found");
}

/** The page's typed error. It is constructed directly and through a factory. */
record Error(String message) {

  static Error notFound(String id) {
    return new Error("User " + id + " not found");
  }
}

sealed interface ServiceError {

  record UserNotFound() implements ServiceError {}

  record ValidationFailed(Throwable cause) implements ServiceError {}
}

class Fixture {

  static final String id = "u-1";

  static final String input = "raw";

  static final int value = 42;

  static final @Nullable String possiblyNull = null;

  static final Maybe<String> maybeValue = Maybe.just("hello");

  static final GenericPath<MaybeKind.Witness, String> generic =
      Path.generic(MAYBE.widen(maybeValue), Instances.monadError(maybe()));

  static final Repository repository = new Repository();

  static final Repository userRepository = new Repository();

  static final Configuration config = new Configuration();

  static final UserService userService = new UserService();

  HttpError toHttpError(UserError error) {
    return new HttpError(404);
  }

  static Maybe<User> findUser(String id) {
    return Maybe.just(new User("Ada"));
  }

  static Optional<User> findUserOptional(String id) {
    return Optional.of(new User("Ada"));
  }

  static Either<String, User> validateUser(String input) {
    return Either.right(new User("Ada"));
  }

  /** The accumulating sibling; the page names it separately so one name means one function. */
  static Validated<String, User> validatedUser(String input) {
    return Validated.valid(new User("Ada"));
  }

  static ValidationPath<List<String>, User> validateUserPath(String input) {
    return Path.valid(new User("Ada"), org.higherkindedj.hkt.Semigroups.list());
  }

  static Config loadConfig() {
    return new Config("app");
  }

  static String readFile() {
    return "contents";
  }

  static Data fetchData() {
    return new Data("data");
  }

  static Data fetchFromNetwork() {
    return new Data("network");
  }

  static ValidatedOrder validateOrder(OrderInput input) {
    return new ValidatedOrder(input.sku());
  }

  static Either<ServiceError, Order> createOrder(User user, ValidatedOrder validated) {
    return Either.right(new Order("o-1"));
  }

  static Order createOrder(User user) {
    return new Order("o-1");
  }

  static String computeDefault() {
    return "computed";
  }

  static Integer computeIntDefault() {
    return -1;
  }

  static @Nullable String possiblyNullValue() {
    return null;
  }

  static final class Repository {

    Maybe<User> findById(String id) {
      return Maybe.just(new User("Ada"));
    }
  }

  static final class UserService {

    EitherPath<UserError, User> getUserById(String id) {
      return Path.right(new User("Ada"));
    }
  }

  static final class Configuration {

    String get(String key) {
      return "8080";
    }
  }
}
