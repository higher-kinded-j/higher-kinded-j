// Fixture for hkj-book/src/effect/path_io.md
//
// The page runs one service call and one pooled connection through every IOPath operation. The
// pool is deliberately exception-free: `bracket` takes a plain Supplier, Function and Consumer,
// which is the point the page makes in prose rather than burying in try/catch.
//
// java.nio.file.Path is deliberately not imported: it would collide with the effect Path the
// whole page is about.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.PathOps;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.resilience.Bulkhead;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.trymonad.Try;

record Data(String value) {}

record Result(String value) {}

record Report(String value) {}

record Config(String name) {

  static Config defaults() {
    return new Config("defaults");
  }
}

final class Connection {

  Report query(String sql) {
    return new Report(sql);
  }
}

final class ConnectionPool {

  Connection borrow() {
    return new Connection();
  }

  void release(Connection connection) {}
}

class Fixture {

  static final String url = "https://example.test/api";

  static final String sql = "select 1";

  static final String source = "a document";

  static final ConnectionPool pool = new ConnectionPool();

  static final IO<Connection> databaseIO = IO.delay(Connection::new);

  static final CircuitBreaker serviceBreaker = CircuitBreaker.withDefaults();

  static final Bulkhead serviceBulkhead = Bulkhead.withMaxConcurrent(8);

  static final IOPath<String> io1 = Path.io(() -> "one");

  static final IOPath<String> io2 = Path.io(() -> "two");

  static final IOPath<String> io3 = Path.io(() -> "three");

  static String fetchFromApi(String url) {
    return "{}";
  }

  Data parse(String content) {
    return new Data(content);
  }

  static Result process(String content) {
    return new Result(content);
  }

  static String readHeader() {
    return "header";
  }

  static String readBody() {
    return "body";
  }

  static void log(String message) {}

  static Data loadData() {
    return new Data("data");
  }

  static String fetchData() {
    return "data";
  }

  static Config loadConfig() {
    return new Config("primary");
  }

  static Config loadBackupConfig() {
    return new Config("backup");
  }

  static void releaseResources() {}

  static String callServiceA() {
    return "a";
  }

  static String callServiceB() {
    return "b";
  }

  static String callFlakyService() {
    return "flaky";
  }
}
