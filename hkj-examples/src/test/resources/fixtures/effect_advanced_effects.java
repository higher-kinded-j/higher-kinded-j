// Fixture for hkj-book/src/effect/advanced_effects.md
//
// The ReaderPath sections thread one application environment through a user lookup. `getUser` is
// declared public here so the snippet that shows its body can override it, and the snippet that
// calls it can see it.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.higherkindedj.hkt.effect.ReaderPath;

record User(String id) {}

record Order(String id) {}

record Result(String value) {}

record Config(String databaseUrl, String username, String password, int timeout, boolean testMode) {

  int getTimeout() {
    return timeout;
  }

  Config withTestMode(boolean testMode) {
    return new Config(databaseUrl, username, password, timeout, testMode);
  }
}

final class DbConnection {

  User query(String sql, String id) {
    return new User(id);
  }
}

final class Logger {

  void debug(String message) {}
}

record AppEnv(DbConnection db, Config config, Logger logger) {}

class Fixture {

  static final ReaderPath<Config, Result> computation = ReaderPath.pure(new Result("r"));

  static Config loadConfig() {
    return new Config("jdbc:postgresql://localhost/app", "app", "secret", 30, false);
  }

  static AppEnv loadEnv() {
    return new AppEnv(new DbConnection(), loadConfig(), new Logger());
  }

  public ReaderPath<AppEnv, User> getUser(String id) {
    return ReaderPath.pure(new User(id));
  }
}
