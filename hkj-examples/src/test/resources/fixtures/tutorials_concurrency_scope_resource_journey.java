// Fixture for hkj-book/src/tutorials/concurrency/scope_resource_journey.md
//
// The journey forks tasks in a scope and then brackets JDBC and file resources. The services and
// values the snippets fork or acquire are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.io.FileReader;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import javax.sql.DataSource;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.Resource;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;

record User(String id, String name) {}

record Config(String name) {}

record Error(String message) {

  static Error from(Throwable cause) {
    return new Error(cause.getMessage());
  }
}

class UserDao {

  List<User> findAll(Connection connection) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final DataSource dataSource = sample();

  static final Lock theLock = sample();

  static final UserDao userDao = new UserDao();

  static final Config loadedConfig = new Config("app");

  static final Callable<Connection> getConnection = () -> dataSource.getConnection();

  static final Resource<FileReader> readerResource = sample();

  static final Resource<FileWriter> writerResource = sample();

  static String fetchFromServerA() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static String fetchFromServerB() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static String expensiveOperation() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static String anotherOperation() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static VTask<String> validateUsername() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static VTask<String> validateEmail() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static VTask<String> validatePassword() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static VTask<String> validateField1() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static VTask<String> validateField2() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static String showAllErrors(List<String> errors) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static String proceedWithValues(List<String> values) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static List<User> doWork(Connection connection) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
