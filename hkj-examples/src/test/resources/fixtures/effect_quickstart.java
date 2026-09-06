// Fixture for hkj-book/src/effect/quickstart.md
//
// Three snippets, one lookup: a user by id, the order placed for that user, and the profile that
// goes with them. `AppError` is declared here too, because the snippet that shows it needs the
// order service to already speak it; the extractor drops this copy when a snippet declares its
// own.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.expression.ForPath;
import org.jspecify.annotations.Nullable;

record User(String id, String name) {}

record Order(String id) {}

record Profile(String displayName, String email) {}

record Receipt(String orderId) {

  static Receipt of(Order order) {
    return new Receipt(order.id());
  }
}

record Summary(String userId, String name, String email) {}

sealed interface AppError {
  record UserNotFound(String id) implements AppError {}

  record OrderFailed(String reason) implements AppError {}
}

final class UserRepository {

  @Nullable User findById(String id) {
    return new User(id, "Ada");
  }
}

final class OrderService {

  Either<AppError, Order> create(User user) {
    return Either.right(new Order("o-1"));
  }
}

final class ProfileService {

  @Nullable Profile loadProfile(User user) {
    return new Profile(user.name(), user.name() + "@example.test");
  }
}

class Fixture {

  static final String id = "u-1";

  static final String userId = "u-1";

  static final UserRepository userRepository = new UserRepository();

  static final OrderService orderService = new OrderService();

  static final ProfileService profileService = new ProfileService();
}
