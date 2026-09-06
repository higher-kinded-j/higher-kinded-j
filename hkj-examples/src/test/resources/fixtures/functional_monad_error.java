// Fixture for hkj-book/src/functional/monad_error.md
//
// The page loads a configuration from a file, then the environment, then defaults. The exceptions
// the imperative contrast throws and the loaders the recovery chain calls are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;

import java.nio.file.Files;
import java.nio.file.Path;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;

record Config(String value, String dbHost) {

  static Config defaults() {
    return new Config("default", "localhost");
  }

  static Config parse(String path) {
    return new Config("parsed", "localhost");
  }
}

record Settings(String value, String dbHost) {

  static Settings from(Config config) {
    return new Settings(config.value(), config.dbHost());
  }

  boolean isReachable() {
    return true;
  }

  static Settings fallback() {
    return new Settings("fallback", "localhost");
  }
}

record Connection(String url) {

  static Connection open(Settings settings) {
    return new Connection("db://" + settings.dbHost());
  }
}

record DbConnection(String url) {}

final class ParseException extends RuntimeException {}

final class ValidationException extends RuntimeException {}

final class DbException extends RuntimeException {}

final class SimpleLogger {

  void warn(String message) {}

  void error(String message) {}
}

class Fixture {

  static final MonadError<EitherKind.Witness<String>, String> me =
      Instances.monadError(either());

  static final String path = "application.conf";

  static final SimpleLogger log = new SimpleLogger();

  static Config parseConfigFile(String path) {
    return new Config("parsed", "localhost");
  }

  static Settings validateSettings(Config config) {
    return Settings.from(config);
  }

  static DbConnection connectToDatabase(Settings settings) {
    return new DbConnection("db://primary");
  }

  static DbConnection connectToFallbackDb(Settings settings) {
    return new DbConnection("db://fallback");
  }

  // Not static: the page declares this method itself further down.
  Kind<EitherKind.Witness<String>, Integer> safeDivide(int a, int b) {
    return b == 0 ? me.raiseError("Cannot divide by zero") : me.of(a / b);
  }

  static Kind<EitherKind.Witness<String>, Config> loadConfigFromFile() {
    return me.of(new Config("from-file", "localhost"));
  }

  static Kind<EitherKind.Witness<String>, Config> loadConfigFromEnv() {
    return me.of(new Config("from-env", "localhost"));
  }

  static DbConnection loadDefaultConfig() {
    return new DbConnection("db://default");
  }
}
