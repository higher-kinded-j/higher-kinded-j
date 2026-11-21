// Fixture for hkj-book/src/release-history/unreleased.md
//
// The page opens with the same "Coming in" band as the home page, whose lead card is a Path chain
// javac rejects. Only the names that snippet uses are declared here. At a release, the page is
// renamed to the version's, and this fixture is renamed with it.

import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.maybe.Maybe;

sealed interface AppError permits UserNotFound {}

record UserNotFound(String userId) implements AppError {}

record User(String name) {}

class Fixture {

  static final String userId = "u-1";

  static Maybe<User> findUser(String userId) {
    return Maybe.just(new User("Ada"));
  }
}
