// Fixture for hkj-book/src/effect/path_id.md
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.id.Id;

record User(String name) {}

class Fixture {

  static final Id<User> idUser = Id.of(new User("Ada"));

  String transform(String value) {
    return value.toUpperCase();
  }
}
