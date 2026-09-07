// Fixture for hkj-book/src/quickstart.md
//
// The page is five minutes of "here is what a Path looks like", so every snippet elides the
// domain it operates on: a user/order/payment sketch and the repositories and services behind it.
// They live here so the page stays about the Paths.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;

record User(String name, String email, int age) {

  static User anonymous() {
    return new User("Anonymous", "", 0);
  }
}

record Order(String id) {

  Order confirm() {
    return this;
  }
}

record Charge(String id) {}

record Receipt(String id, BigDecimal amount) {}

record Signup(String name, String email, int age) {}

/** The typed error channel the page's EitherPath examples run on. */
sealed interface AppError {

  record NotFound(String id) implements AppError {}

  record UserNotFound(String id) implements AppError {}

  record PaymentFailed(Throwable cause) implements AppError {}
}

class Fixture {

  static final String id = "u-1";

  static final String userId = "u-1";

  static final Signup input = new Signup("Ada", "ada@example.com", 36);

  static final MaybePath<User> maybePath = Path.maybe(new User("Ada", "ada@example.com", 36));

  static final EitherPath<AppError, User> eitherPath =
      Path.either(Either.right(new User("Ada", "ada@example.com", 36)));

  static final Repository repository = new Repository();

  static final Repository userRepository = new Repository();

  static final OrderService orderService = new OrderService();

  static final Gateway gateway = new Gateway();

  static final Semigroup<List<String>> errors = Semigroups.list();

  static ValidationPath<List<String>, String> validateName(String name) {
    return Path.valid(name, errors);
  }

  static ValidationPath<List<String>, String> validateEmail(String email) {
    return Path.valid(email, errors);
  }

  static ValidationPath<List<String>, Integer> validateAge(int age) {
    return Path.valid(age, errors);
  }

  static Either<AppError, BigDecimal> validateAmount(User user, BigDecimal amount) {
    return Either.right(amount);
  }

  static final class Repository {

    Maybe<User> findById(String id) {
      return Maybe.just(new User("Ada", "ada@example.com", 36));
    }
  }

  static final class OrderService {

    Either<AppError, Order> create(User user) {
      return Either.right(new Order("o-1"));
    }
  }

  static final class Gateway {

    Charge charge(BigDecimal amount) {
      return new Charge("c-1");
    }
  }
}
