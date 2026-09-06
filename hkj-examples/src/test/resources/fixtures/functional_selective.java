// Fixture for hkj-book/src/functional/selective.md
//
// The page selects between branches under Maybe, IO, Either and Optional. The configuration, the
// services the IO examples call and the three config sources are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOSelective;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalSelective;

record Config(String name, int port, boolean tls) {

  static boolean isDebugMode() {
    return true;
  }

  boolean shouldPersist() {
    return true;
  }
}

record User(String id, Map<String, String> properties) {}

final class SimpleLogger {

  void debug(String message) {}

  void info(String message) {}
}

final class Database {

  int write(String data) {
    return 1;
  }
}

final class Analytics {

  void track(String eventName, String userId, Map<String, String> properties) {}
}

final class FeatureFlags {

  boolean isEnabled(String flag) {
    return true;
  }
}

class Fixture {

  // `IO` alone is java.lang.IO on this release, so the library's is imported above.
  static final Selective<IOKind.Witness> selective = IOSelective.INSTANCE;

  static final SimpleLogger log = new SimpleLogger();

  static final Database database = new Database();

  static final Analytics analytics = new Analytics();

  static final FeatureFlags featureFlags = new FeatureFlags();

  static final Config config = new Config("app", 8080, false);

  static final String data = "payload";

  static Config defaultConfig() {
    return new Config("default", 8080, false);
  }

  static Optional<Choice<String, Config>> tryEnvConfig() {
    return Optional.of(Selective.left("no ENV config"));
  }

  static Optional<Choice<String, Config>> tryFileConfig() {
    return Optional.of(Selective.right(new Config("file", 8080, false)));
  }
}
