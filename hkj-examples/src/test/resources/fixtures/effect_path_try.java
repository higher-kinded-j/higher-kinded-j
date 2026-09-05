// Fixture for hkj-book/src/effect/path_try.md
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.trymonad.Try;

record Config(String name) {}

record Data(String value) {}

class Fixture {

  static final String input = "42";

  static final Integer defaultValue = 0;

  static Try<Config> loadConfigTry() {
    return Try.success(new Config("app"));
  }

  static Data parseJson(String content) {
    return new Data(content);
  }

  static String readFile(String name) {
    return "contents of " + name;
  }
}
