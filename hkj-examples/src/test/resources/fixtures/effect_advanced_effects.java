// Fixture for hkj-book/src/effect/advanced_effects.md
//
// The ReaderPath sections thread one application environment through a user lookup. `getUser` is
// declared public here so the snippet that shows its body can override it, and the snippet that
// calls it can see it. The StatePath and WriterPath sections bring their own domains: a batch
// pipeline that counts what it has processed, and a repository that writes an audit trail.
//
// Snippets that declare a top-level class become siblings of this one rather than subclasses, so
// they cannot see these members. Those snippets hold their own collaborators as fields.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ReaderPath;
import org.higherkindedj.hkt.effect.WithStatePath;
import org.higherkindedj.hkt.effect.WriterPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.tuple.Tuple;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.writer.Writer;
import org.springframework.web.bind.annotation.GetMapping;

record User(String id) {}

record Order(String id) {}

record Result(String value) {}

record Config(String databaseUrl, String username, String password, int timeout, boolean testMode) {

  DbConnection database() {
    return new DbConnection();
  }

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

  User findUser(String id) {
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

  // The per-request environment the ReaderPath-vs-Spring comparison assembles at the edge.

  static DataSource dataSource;

  static List<Order> queryOrders(DataSource ds, String tenantId) {
    return List.of();
  }

  // The state pipeline.

  static final Batch batch = new Batch("b-1");

  static final WithStatePath<Counter, String> operationA = WithStatePath.pure("a");

  static final WithStatePath<Counter, String> operationB = WithStatePath.pure("b");

  static ResultA processA(Batch batch) {
    return new ResultA("a");
  }

  static ResultA processA(Batch batch, Stats stats) {
    return new ResultA("a");
  }

  static ResultB processB(ResultA a) {
    return new ResultB("b");
  }

  static ResultB processB(ResultA a, Stats stats) {
    return new ResultB("b");
  }

  static ResultC processC(ResultB b) {
    return new ResultC("c");
  }

  // The audit trail.

  static final UserInput input = new UserInput("Ada");

  static final User user = new User("u-1");

  static final UserRepository repository = new UserRepository();

  static List<String> append(List<String> log, String entry) {
    return Stream.concat(log.stream(), Stream.of(entry)).toList();
  }

  static ValidatedInput validate(UserInput input) {
    return new ValidatedInput(input.name());
  }

  public WriterPath<List<String>, User> createUser(UserInput input) {
    return WriterPath.writer(new User(input.name()), List.of("created"), Monoids.list());
  }

  static WriterPath<List<String>, String> stepOne() {
    return WriterPath.pure("one", Monoids.list());
  }

  static WriterPath<List<String>, Integer> stepTwo(String a) {
    return WriterPath.pure(a.length(), Monoids.list());
  }

  static WriterPath<List<String>, Result> stepThree(Integer b) {
    return WriterPath.pure(new Result(b.toString()), Monoids.list());
  }

  // The game that is both stateful and logged.

  static Move calculateMove(GameState state, Position pos) {
    return new Move(pos, pos);
  }
}

// --- StatePath: the pipeline the page threads statistics through ---

record Batch(String id) {}

record ResultA(String value) {}

record ResultB(String value) {}

record ResultC(String value) {}

record Stats(int processed) {

  Stats() {
    this(0);
  }

  static Stats initial() {
    return new Stats(0);
  }

  Stats incrementProcessed() {
    return new Stats(processed + 1);
  }
}

record Counter(int value) {

  static Counter zero() {
    return new Counter(0);
  }

  Counter increment() {
    return new Counter(value + 1);
  }
}

// --- WriterPath: the audit trail the page accumulates ---

record UserInput(String name) {}

record ValidatedInput(String name) {}

sealed interface Error {
  record NotFound(String id) implements Error {}

  record ServiceDisabled() implements Error {}

  record ProcessingFailed(Throwable cause) implements Error {}
}

sealed interface AuditEvent {
  record AttemptSave(String id) implements AuditEvent {}

  record SaveSucceeded(String id) implements AuditEvent {}

  record SaveFailed(String id, Error error) implements AuditEvent {}
}

// `save` is overloaded because the page writes both halves of a repository: the one that takes a
// validated input and hands back a user, and the one that takes a user and can fail.
final class UserRepository {

  User save(ValidatedInput validated) {
    return new User(validated.name());
  }

  Either<Error, User> save(User user) {
    return Either.right(user);
  }
}

// --- Combining the two: the game the page tracks and logs ---

record Position(int row, int col) {}

record Move(Position from, Position to) {}

sealed interface Event {
  record MoveMade(Position position, Move move) implements Event {}
}

record GameState(int turn) {

  GameState apply(Move move) {
    return new GameState(turn + 1);
  }
}

record ServiceConfig(boolean enabled) {

  boolean isEnabled() {
    return enabled;
  }
}

record Request(String id) {}
