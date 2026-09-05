// Fixture for hkj-book/src/effect/path_maybe.md
//
// The page catalogues MaybePath's constructors, combinators and extractors over a small user
// lookup. The lookup and its domain live here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.maybe.Maybe;
import org.jspecify.annotations.Nullable;

record User(String name) {

  static User guest() {
    return new User("guest");
  }
}

class Fixture {

  static final String id = "u-1";

  static final @Nullable String possiblyNull = null;

  static final Repository repository = new Repository();

  static Maybe<User> findUser(String id) {
    return Maybe.just(new User("Ada"));
  }

  static String expensiveDefault() {
    return "computed default";
  }

  static final class Repository {

    Maybe<User> findById(String id) {
      return Maybe.just(new User("Ada"));
    }
  }
}
