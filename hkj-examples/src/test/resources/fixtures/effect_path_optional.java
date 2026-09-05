// Fixture for hkj-book/src/effect/path_optional.md
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.Optional;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;

record User(String name) {}

class Fixture {

  static final String id = "u-1";

  static final Repository repository = new Repository();

  static final class Repository {

    Optional<User> findById(String id) {
      return Optional.of(new User("Ada"));
    }
  }
}
