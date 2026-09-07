// Fixture for hkj-book/src/monads/vtask_resource.md
//
// The page acquires a JDBC connection, a file channel and a lock, and releases them in order. The
// domain the snippets elide is declared here.
//
// The fixture is generic so the page can show the acquisition/release ordering of `Resource<A>`,
// `Resource<B>` and `Resource<C>` as a shape, without inventing three domains for it.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.Par.Tuple3;
import org.higherkindedj.hkt.vtask.Resource;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;

record User(String id, String name) {}

record Config(String name) {}

record Data(String value) {}

record Handle(String name) {}

record Order(String id) {}

record OrderResult(String orderId, String status) {}

final class UserDao {

  List<User> findAll(Connection conn) {
    return List.of(new User("u-1", "Alice"));
  }
}

final class Logger {

  void info(String message) {}

  void warn(String message, Throwable error) {}
}

final class Metrics {

  void recordLockRelease() {}
}

class Fixture<A, B, C> {

  static final DataSource dataSource = null;

  static final Path path = Path.of("build.gradle.kts");

  static final UserDao userDao = new UserDao();

  static final Logger logger = new Logger();

  static final Metrics metrics = new Metrics();

  static final Lock lock = new ReentrantLock();

  static final Config loadedConfig = new Config("app");

  static final boolean someCondition = true;

  static final String sql = "SELECT 1";

  static final String sql1 = "SELECT 1";

  static final String sql2 = "SELECT 2";

  static final Order order = new Order("o-1");

  static final Data cachedData = new Data("cached");

  static final Resource<Connection> connResource =
      Resource.fromAutoCloseable(() -> dataSource.getConnection());

  static final Resource<FileChannel> fileResource =
      Resource.fromAutoCloseable(() -> FileChannel.open(path, StandardOpenOption.READ));

  static final Resource<PreparedStatement> stmtResource =
      connResource.flatMap(
          conn -> Resource.fromAutoCloseable(() -> conn.prepareStatement("SELECT 1")));

  static final Resource<ResultSet> resultSetResource =
      stmtResource.flatMap(stmt -> Resource.fromAutoCloseable(stmt::executeQuery));

  static final ConnectionPool pool = new ConnectionPool();

  // The ordering example names three resources without a domain; these lend it the shapes.
  Callable<A> acquireA;
  Consumer<A> releaseA;
  Callable<B> acquireB;
  Consumer<B> releaseB;
  Callable<C> acquireC;
  Consumer<C> releaseC;
  Callable<Handle> acquire;
  Consumer<Handle> release;

  static Data fetchData(Connection conn) {
    return new Data("payload");
  }

  static String processData(Connection conn, FileChannel file) {
    return "processed";
  }

  static String query(Connection conn, String sql) {
    return "row";
  }

  static void updateInventory(Connection conn, Order order) {}

  static void chargePayment(Connection conn, Order order) {}

  static void sendNotification(Connection conn, Order order) {}

  static void cleanupStep1() {}

  static void cleanupStep2() {}

  static void cleanupStep3() {}
}

final class ConnectionPool {

  Connection getConnection() throws SQLException {
    throw new SQLException("no pool in the fixture");
  }
}
