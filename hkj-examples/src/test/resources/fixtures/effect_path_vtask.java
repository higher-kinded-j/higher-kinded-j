// Fixture for hkj-book/src/effect/path_vtask.md
//
// The page catalogues VTaskPath's constructors, combinators, three execution modes and error
// handling over a small fetch/config sketch. The services behind it live here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;

record Config(String name) {

  static Config defaults() {
    return new Config("defaults");
  }
}

record Data(String value) {}

class Fixture {

  static final String url = "https://example.test/data";

  static final Logger logger = new Logger();

  static final HttpClient httpClient = new HttpClient();

  static final ConfigService configService = new ConfigService();

  static final VTask<Config> existingVTask = VTask.delay(() -> new Config("app"));

  static Integer compute() {
    return 42;
  }

  static String enrichWithTimestamp(String value) {
    return value + " @ now";
  }

  static Unit initResources() {
    return Unit.INSTANCE;
  }

  static Data loadData() {
    return new Data("data");
  }

  static Config loadFallbackConfig() {
    return new Config("fallback");
  }

  static void handleError(Throwable cause) {}

  static final class HttpClient {

    String get(String url) {
      return "{}";
    }
  }

  static final class ConfigService {

    Config load() {
      return new Config("app");
    }
  }

  /** Stands in for whatever logger the reader has. */
  static final class Logger {

    Unit info(String message) {
      return Unit.INSTANCE;
    }
  }
}
