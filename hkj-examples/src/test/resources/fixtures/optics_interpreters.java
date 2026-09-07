// Fixture for hkj-book/src/optics/interpreters.md
//
// The page runs one program under each interpreter in turn - direct, logging, validating, and two
// the reader writes. The models are declared here, along with the profiling and mock interpreters
// the page builds and then reuses further down; the snippet that shows one shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.free.DirectOpticInterpreter;
import org.higherkindedj.optics.free.LoggingOpticInterpreter;
import org.higherkindedj.optics.free.OpticInterpreters;
import org.higherkindedj.optics.free.OpticOp;
import org.higherkindedj.optics.free.OpticOpKind;
import org.higherkindedj.optics.free.OpticOpKindHelper;
import org.higherkindedj.optics.free.OpticPrograms;
import org.higherkindedj.optics.free.ValidationOpticInterpreter;
import org.junit.jupiter.api.Test;

@GenerateLenses
record Person(String name, int age) {}

enum PerformanceRating {
  EXCELLENT,
  GOOD,
  SATISFACTORY,
  POOR
}

@GenerateLenses
record Employee(String name, int salary, String status) {}

@GenerateLenses
record Account(String accountId, BigDecimal balance) {}

@GenerateLenses
record Transaction(
    String txnId,
    Account from,
    Account to,
    BigDecimal amount,
    LocalDateTime timestamp) {}

@GenerateLenses
record UserV1(String username, String email, Integer age) {}

@GenerateLenses
record UserV2(String username, String email, int age, boolean verified) {}

@GenerateLenses
record Player(String name, int score) {}

@GenerateLenses
@GenerateTraversals
record Team(String name, List<Player> players) {}

enum OrderStatus {
  PENDING,
  COMPLETED
}

@GenerateLenses
record Order(String id, OrderStatus status) {}

record Dataset(String name, List<String> rows) {}

class ValidationException extends RuntimeException {

  ValidationException(String message) {
    super(message);
  }

  ValidationException(List<String> errors) {
    super("Validation failed: " + String.join(", ", errors));
  }
}

class BusinessException extends RuntimeException {

  BusinessException(String message, Throwable cause) {
    super(message, cause);
  }
}

// The two interpreters the page writes: named here so the sections that use one compile on their
// own. The section that shows an interpreter shadows this copy.
final class ProfilingOpticInterpreter {

  Map<String, Long> getAverageExecutionTimes() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  <A> A run(Free<OpticOpKind.Witness, A> program) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

final class MockOpticInterpreter {

  private final Function<OpticOp<?, ?>, Object> stubs;

  MockOpticInterpreter(Function<OpticOp<?, ?>, Object> stubs) {
    this.stubs = stubs;
  }

  @SuppressWarnings("unchecked")
  <A> A run(Free<OpticOpKind.Witness, A> program) {
    Function<Kind<OpticOpKind.Witness, ?>, Kind<IdKind.Witness, ?>> transform =
        kind -> {
          OpticOp<?, ?> op =
              OpticOpKindHelper.OP.narrow((Kind<OpticOpKind.Witness, Object>) kind);
          return Id.of(Free.pure(stubs.apply(op)));
        };
    Kind<IdKind.Witness, A> resultKind = program.foldMap(transform, Instances.monad(id()));
    return IdKindHelper.ID.narrow(resultKind).value();
  }
}

// The reader's own logger and stores, whatever they are.
class Log {

  void error(String message, Throwable cause) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class AuditService {

  void record(String key, String entry) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  void record(String entry) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class AuditRepository {

  void save(String key, String entry) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  void saveAll(List<String> entries) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Log log = new Log();

  static final AuditService auditService = new AuditService();

  static final AuditRepository auditRepository = new AuditRepository();

  static final Person person = sample();

  static final Person testData = person;

  static final Team team = sample();

  static final Order order = sample();

  static final Order mockOrder = order;

  static final Dataset dataset = sample();

  static final List<Transaction> transactions = List.of();

  static final Free<OpticOpKind.Witness, Person> program = sample();

  static final Free<OpticOpKind.Witness, Order> orderProcessing = sample();

  static final Free<OpticOpKind.Witness, Person> program1 = program;

  static final Free<OpticOpKind.Witness, Person> program2 = program;

  static Free<OpticOpKind.Witness, Employee> buildReviewProgram(
      Employee employee, PerformanceRating rating) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Free<OpticOpKind.Witness, Person> buildComplexProgram(Person data) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Free<OpticOpKind.Witness, Person> buildComplexBusinessLogic(Person data) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Free<OpticOpKind.Witness, Team> buildComplexTeamUpdate(Team source) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Free<OpticOpKind.Witness, Order> buildOrderProgram(Order source) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Free<OpticOpKind.Witness, Order> buildOrderProcessing(Order source) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Free<OpticOpKind.Witness, Transaction> buildTransfer(Transaction source) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Free<OpticOpKind.Witness, Dataset> buildDataPipeline(Dataset source) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Free<OpticOpKind.Witness, Dataset> optimiseProgram(
      Free<OpticOpKind.Witness, Dataset> source, String operation) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static List<UserV1> loadOldUsers() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static void saveNewUser(UserV2 user) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
