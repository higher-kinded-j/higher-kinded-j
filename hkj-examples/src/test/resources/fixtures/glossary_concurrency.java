// Fixture for hkj-book/src/glossary/concurrency.md
//
// The glossary defines a term and then shows it in use, so each entry's snippet elides the domain
// it is acting on. The tasks, the resources and the services are declared here.
//
// `Error` is the page's own accumulation error, not `java.lang.Error`; declaring it top-level here
// is what shadows the JDK's, exactly as a reader's own `Error` would.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.nio.channels.FileChannel;
import javax.sql.DataSource;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vtask.Par.Tuple2;
import org.higherkindedj.hkt.vtask.Resource;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.ScopeJoiner;
import org.higherkindedj.hkt.vtask.VTask;

record User(String id, String name) {}

record UserData(String id) {}

record Package(String name) {}

record Result(String value) {}

/** The page's own accumulation error, which shadows `java.lang.Error`. */
record Error(String message) {

  static Error from(Throwable cause) {
    return new Error(String.valueOf(cause.getMessage()));
  }
}

interface UserDao {
  List<User> findAll(Connection connection);
}

interface HttpClient {
  String get(String url);
}

interface Metrics {
  void recordLockRelease();
}

class Fixture {

  static final DataSource dataSource = sample();

  static final UserDao userDao = sample();

  static final HttpClient httpClient = sample();

  static final String sql = "select 1";

  static final Resource<FileChannel> fileChannel = sample();

  static final Lock rwLock = new ReentrantLock();

  static final Metrics metrics = sample();

  static final String userId = "u-1";

  static final String id = "u-1";

  static final String mirror1 = "https://mirror1.test";

  static final String mirror2 = "https://mirror2.test";

  static final List<String> ids = List.of("u-1", "u-2");

  static final VTask<Result> taskA = VTask.of(() -> new Result("a"));

  static final VTask<Result> taskB = VTask.of(() -> new Result("b"));

  // The gate compiles snippets; it never runs them.
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static VTask<String> task1() {
    return VTask.of(() -> "one");
  }

  static VTask<String> task2() {
    return VTask.of(() -> "two");
  }

  static Result processData(Connection connection) {
    return new Result("processed");
  }

  static Result processId(String id) {
    return new Result(id);
  }

  static UserData fetchPermissions(String userId) {
    return new UserData(userId);
  }

  static UserData fetchProfile(String userId) {
    return new UserData(userId);
  }

  static UserData fetchUser(String id) {
    return new UserData(id);
  }

  static UserData fetchPreferences(String id) {
    return new UserData(id);
  }

  static Package fetchFrom(String mirror) {
    return new Package(mirror);
  }
}
