// Fixture for hkj-book/src/effect/compiler_errors.md
//
// Each section shows a trigger and a fix over the same small domain: a user looked up by id and
// an order carrying a total.
//
// `findUser` and `lookupUser` are deliberately absent: several snippets declare those methods
// themselves, with different return types from one section to the next, and a fixture copy would
// turn each of those declarations into an invalid override rather than the thing the page is
// showing.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.jspecify.annotations.Nullable;

sealed interface AppError {
  record UserNotFound(String id) implements AppError {}

  record NotFound(String reason) implements AppError {}
}

record User(String id) {

  String name() {
    return id;
  }
}

record Order(String id, double total, boolean valid) {

  boolean isValid() {
    return valid;
  }
}

final class Repository {

  User findById(String id) {
    return new User(id);
  }
}

class Fixture {

  static final String userId = "u-1";

  static final String input = "u-1";

  static final User user = new User("u-1");

  static final Order order = new Order("o-1", 9.99, true);

  static final Repository repository = new Repository();

  static @Nullable User loadUser(String id) {
    return new User(id);
  }

  String processOrder(Order order) {
    return order.id();
  }

  EitherPath<AppError, Order> validateAndProcessOrder(Order order) {
    return Path.right(order);
  }

  static EitherPath<AppError, String> validateInput(String input) {
    return Path.right(input);
  }
}
