// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.free.DirectOpticInterpreter;
import org.higherkindedj.optics.free.LoggingOpticInterpreter;
import org.higherkindedj.optics.free.OpticInterpreters;
import org.higherkindedj.optics.free.OpticOpKind;
import org.higherkindedj.optics.free.OpticPrograms;
import org.higherkindedj.optics.free.ValidationOpticInterpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 11: Advanced Optics DSL — Free Monad for complex workflows.
 *
 * <p>Pain → Promise. The optic operations from Tutorials 01-10 execute eagerly. Sometimes we want
 * to <em>describe</em> a sequence of optic operations as data — to log them, validate them before
 * running, run a dry-run for testing, or fuse them for performance. The hand-rolled version is a
 * {@code List<Operation>} ADT we have to interpret per scenario.
 *
 * <p>The Free Monad DSL captures the same idea, generally:
 *
 * <pre>
 *   Free&lt;OpticOp, Order&gt; program = OpticPrograms.modify(orderItems, ...)
 *       .flatMap(o -&gt; OpticPrograms.set(orderStatus, Shipped.of("ABC123"), o));
 *
 *   Order out = OpticInterpreters.direct.run(program, order);          // execute
 *   List&lt;String&gt; trace = OpticInterpreters.logging.run(program, ...);  // audit log
 *   Validation report = ValidationOpticInterpreter.dryRun(program);     // pre-check
 * </pre>
 *
 * <p>One program; many interpreters. This is the same trick used by the Foundations chapter's Free
 * Monad section (the Coyoneda / Free Applicative tutorials in the Advanced journey).
 *
 * <ul>
 *   <li><b>Direct execution</b>: Normal optic operations
 *   <li><b>Logging</b>: Record all operations for audit trails
 *   <li><b>Validation</b>: Dry-run to check constraints before executing
 *   <li><b>Testing</b>: Mock operations without real data
 *   <li><b>Optimization</b>: Analyze and fuse operations for efficiency
 * </ul>
 *
 * <p>When to Use: - Complex multi-step workflows - Audit trails (logging what changed) - Validation
 * before execution (dry-run) - Testing without side effects - Performance optimization (batch
 * operations)
 *
 * <p>When NOT to Use: - Simple one-off operations (use OpticOps instead) - No need for multiple
 * interpretations
 *
 * <p>This is an advanced topic. Complete Tutorial 09 first.
 */
public class Tutorial11_AdvancedOpticsDSL {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  /**
   * Exercise 1: Build a simple Free program with get / set.
   *
   * <pre>
   *   // Nudge:    OpticPrograms.get(source, optic) and OpticPrograms.set(source, optic, value).
   *   // Strategy: OpticPrograms.get(person, ageLens)
   *   //           OpticPrograms.set(person, ageLens, 31)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: build Free programs from optic operations")
  void exercise1_simpleProgram() {
    @GenerateLenses
    record Person(String name, int age) {}

    // Manual implementation (annotation processor would generate this)
    class PersonLenses {
      public static Lens<Person, String> name() {
        return Lens.of(Person::name, (p, newName) -> new Person(newName, p.age()));
      }

      public static Lens<Person, Integer> age() {
        return Lens.of(Person::age, (p, newAge) -> new Person(p.name(), newAge));
      }
    }

    Person person = new Person("Alice", 30);

    Lens<Person, String> nameLens = PersonLenses.name();
    Lens<Person, Integer> ageLens = PersonLenses.age();

    // TODO: Replace null with OpticPrograms.get() to create a program that reads the age
    // Hint: OpticPrograms.get(person, ageLens)
    Free<OpticOpKind.Witness, Integer> getProgram = answerRequired();

    // Execute the program
    DirectOpticInterpreter interpreter = OpticInterpreters.direct();
    Integer age = interpreter.run(getProgram);

    assertThat(age).isEqualTo(30);

    // TODO: Replace null with OpticPrograms.set() to create a program that sets age to 31
    // Hint: OpticPrograms.set(person, ageLens, 31)
    Free<OpticOpKind.Witness, Person> setProgram = answerRequired();

    Person updated = interpreter.run(setProgram);
    assertThat(updated.age()).isEqualTo(31);
  }

  /**
   * Exercise 2: Compose programs with {@code flatMap}.
   *
   * <pre>
   *   // Nudge:    Inside the lambda, build a set program with the new value.
   *   // Strategy: OpticPrograms.set(counter, valueLens, currentValue + 10)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: read-then-write programs via Free.flatMap")
  void exercise2_composingPrograms() {
    @GenerateLenses
    record Counter(int value) {}

    // Manual implementation (annotation processor would generate this)
    class CounterLenses {
      public static Lens<Counter, Integer> value() {
        return Lens.of(Counter::value, (c, newValue) -> new Counter(newValue));
      }
    }

    Counter counter = new Counter(5);

    Lens<Counter, Integer> valueLens = CounterLenses.value();

    // Build a program that reads the current value, adds 10, then sets it back
    // TODO: Replace null with a composed program using flatMap
    // Hint: OpticPrograms.get(counter, valueLens).flatMap(v -> OpticPrograms.set(...))
    Free<OpticOpKind.Witness, Counter> program =
        OpticPrograms.get(counter, valueLens).flatMap(currentValue -> answerRequired());

    Counter result = OpticInterpreters.direct().run(program);
    assertThat(result.value()).isEqualTo(15);
  }

  /**
   * Exercise 3: Conditional workflow inside a Free program.
   *
   * <pre>
   *   // Nudge:    Each branch returns a different set program; pick the new status.
   *   // Strategy: OpticPrograms.set(account, statusLens, "APPROVED")
   *   //           OpticPrograms.set(account, statusLens, "DENIED")
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: branching Free programs by runtime value")
  void exercise3_conditionalWorkflows() {
    @GenerateLenses
    record Account(String id, double balance, String status) {}

    // Manual implementation (annotation processor would generate this)
    class AccountLenses {
      public static Lens<Account, String> id() {
        return Lens.of(Account::id, (a, newId) -> new Account(newId, a.balance(), a.status()));
      }

      public static Lens<Account, Double> balance() {
        return Lens.of(
            Account::balance, (a, newBalance) -> new Account(a.id(), newBalance, a.status()));
      }

      public static Lens<Account, String> status() {
        return Lens.of(
            Account::status, (a, newStatus) -> new Account(a.id(), a.balance(), newStatus));
      }
    }

    Account account = new Account("ACC-001", 1500.0, "PENDING");

    Lens<Account, Double> balanceLens = AccountLenses.balance();
    Lens<Account, String> statusLens = AccountLenses.status();

    // Build a program that:
    // 1. Reads the balance
    // 2. If balance >= 1000, set status to "APPROVED"
    // 3. If balance < 1000, set status to "DENIED"
    //
    // TODO: Replace null with a conditional program
    // Hint: Use OpticPrograms.get().flatMap() with if-else to choose which program to run
    Free<OpticOpKind.Witness, Account> program =
        OpticPrograms.get(account, balanceLens)
            .flatMap(
                balance -> {
                  if (balance >= 1000) {
                    return answerRequired();
                  } else {
                    return answerRequired();
                  }
                });

    Account result = OpticInterpreters.direct().run(program);
    assertThat(result.status()).isEqualTo("APPROVED");

    // Test with low balance
    Account lowBalanceAccount = new Account("ACC-002", 500.0, "PENDING");

    Free<OpticOpKind.Witness, Account> lowBalanceProgram =
        OpticPrograms.get(lowBalanceAccount, balanceLens)
            .flatMap(
                balance -> {
                  if (balance >= 1000) {
                    return OpticPrograms.set(lowBalanceAccount, statusLens, "APPROVED");
                  } else {
                    return OpticPrograms.set(lowBalanceAccount, statusLens, "DENIED");
                  }
                });

    Account lowBalanceResult = OpticInterpreters.direct().run(lowBalanceProgram);
    assertThat(lowBalanceResult.status()).isEqualTo("DENIED");
  }

  /**
   * Exercise 4: Three-step transformation in one Free program.
   *
   * <pre>
   *   // Nudge:    The third step toggles the active flag.
   *   // Strategy: OpticPrograms.set(u2, activeLens, true)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: multi-step Free program — capitalise / lowercase / activate")
  void exercise4_multiStepTransformations() {
    @GenerateLenses
    record User(String id, String name, String email, boolean active) {}

    // Manual implementation (annotation processor would generate this)
    class UserLenses {
      public static Lens<User, String> id() {
        return Lens.of(User::id, (u, newId) -> new User(newId, u.name(), u.email(), u.active()));
      }

      public static Lens<User, String> name() {
        return Lens.of(
            User::name, (u, newName) -> new User(u.id(), newName, u.email(), u.active()));
      }

      public static Lens<User, String> email() {
        return Lens.of(
            User::email, (u, newEmail) -> new User(u.id(), u.name(), newEmail, u.active()));
      }

      public static Lens<User, Boolean> active() {
        return Lens.of(
            User::active, (u, newActive) -> new User(u.id(), u.name(), u.email(), newActive));
      }
    }

    User user = new User("user1", "alice", "ALICE@EXAMPLE.COM", false);

    Lens<User, String> nameLens = UserLenses.name();
    Lens<User, String> emailLens = UserLenses.email();
    Lens<User, Boolean> activeLens = UserLenses.active();

    // Build a program that:
    // 1. Capitalizes the name (first letter uppercase, rest lowercase)
    // 2. Lowercases the email
    // 3. Sets active to true
    //
    // TODO: Replace null with a multi-step program using flatMap chains
    // Hint: OpticPrograms.modify().flatMap(u1 -> OpticPrograms.modify(...).flatMap(u2 -> ...))
    Free<OpticOpKind.Witness, User> program =
        OpticPrograms.modify(user, nameLens, name -> capitalise(name))
            .flatMap(
                u1 ->
                    OpticPrograms.modify(u1, emailLens, String::toLowerCase)
                        .flatMap(u2 -> answerRequired()));

    User result = OpticInterpreters.direct().run(program);
    assertThat(result.name()).isEqualTo("Alice");
    assertThat(result.email()).isEqualTo("alice@example.com");
    assertThat(result.active()).isTrue();
  }

  // Helper method for capitalisation
  private static String capitalise(String s) {
    if (s == null || s.isEmpty()) return s;
    return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
  }

  /**
   * Exercise 5: Logging interpreter for audit trails.
   *
   * <pre>
   *   // Nudge:    OpticInterpreters.logging() returns a LoggingOpticInterpreter; getLog reads it.
   *   // Strategy: OpticInterpreters.logging()
   *   //           logger.getLog()
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 5: logging interpreter records operations")
  void exercise5_loggingInterpreter() {
    @GenerateLenses
    record Document(String id, String title, String status) {}

    // Manual implementation (annotation processor would generate this)
    class DocumentLenses {
      public static Lens<Document, String> id() {
        return Lens.of(Document::id, (d, newId) -> new Document(newId, d.title(), d.status()));
      }

      public static Lens<Document, String> title() {
        return Lens.of(
            Document::title, (d, newTitle) -> new Document(d.id(), newTitle, d.status()));
      }

      public static Lens<Document, String> status() {
        return Lens.of(
            Document::status, (d, newStatus) -> new Document(d.id(), d.title(), newStatus));
      }
    }

    Document doc = new Document("DOC-001", "Report", "DRAFT");

    Lens<Document, String> statusLens = DocumentLenses.status();
    Lens<Document, String> titleLens = DocumentLenses.title();

    // Build a program that updates title and status
    Free<OpticOpKind.Witness, Document> program =
        OpticPrograms.modify(doc, titleLens, title -> title + " (Revised)")
            .flatMap(d -> OpticPrograms.set(d, statusLens, "PUBLISHED"));

    // TODO: Replace null with the logging interpreter
    // Hint: OpticInterpreters.logging()
    LoggingOpticInterpreter logger = answerRequired();

    Document result = logger.run(program);

    assertThat(result.title()).isEqualTo("Report (Revised)");
    assertThat(result.status()).isEqualTo("PUBLISHED");

    // Check the audit log
    // TODO: Replace null with logger.getLog()
    List<String> log = answerRequired();

    assertThat(log).isNotEmpty();
    // Log should contain operations performed
    assertThat(log.size()).isGreaterThan(0);
  }

  /**
   * Exercise 6: Validation interpreter for dry-runs.
   *
   * <pre>
   *   // Nudge:    OpticInterpreters.validation() / new ValidationOpticInterpreter() depending on
   *   //           the API; check the surrounding scaffolding for the exact factory.
   *   // Strategy: build a ValidationOpticInterpreter and call validate(program).
   *   // Spoiler:  see assertions and the matching solution file for the exact wiring.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 6: validation interpreter dry-runs without executing")
  void exercise6_validationInterpreter() {
    @GenerateLenses
    record Config(String env, int maxConnections, boolean debugMode) {}

    // Manual implementation (annotation processor would generate this)
    class ConfigLenses {
      public static Lens<Config, String> env() {
        return Lens.of(
            Config::env, (c, newEnv) -> new Config(newEnv, c.maxConnections(), c.debugMode()));
      }

      public static Lens<Config, Integer> maxConnections() {
        return Lens.of(
            Config::maxConnections, (c, newMax) -> new Config(c.env(), newMax, c.debugMode()));
      }

      public static Lens<Config, Boolean> debugMode() {
        return Lens.of(
            Config::debugMode, (c, newDebug) -> new Config(c.env(), c.maxConnections(), newDebug));
      }
    }

    Config config = new Config("production", 100, false);

    Lens<Config, String> envLens = ConfigLenses.env();
    Lens<Config, Boolean> debugLens = ConfigLenses.debugMode();

    // Build a program that enables debug mode in production (generally a bad idea!)
    Free<OpticOpKind.Witness, Config> program =
        OpticPrograms.get(config, envLens)
            .flatMap(
                env -> {
                  // Handle null case for validation interpreter (dry-run returns null)
                  if (env != null && env.equals("production")) {
                    return OpticPrograms.set(config, debugLens, true);
                  } else {
                    return OpticPrograms.pure(config);
                  }
                });

    // TODO: Replace null with the validation interpreter
    // Hint: OpticInterpreters.validating()
    ValidationOpticInterpreter validator = answerRequired();

    // Validate the program (dry-run)
    // TODO: Replace null with validator.validate(program)
    ValidationOpticInterpreter.ValidationResult result = answerRequired();

    // The validator can detect potential issues
    // In this simple case, it validates the structure is correct
    assertThat(result).isNotNull();
    assertThat(result.isValid()).isTrue();
  }

  /**
   * Exercise 7: Real-world workflow — order processing pipeline.
   *
   * <pre>
   *   // Nudge:    Build a chain: read total -&gt; conditional set status -&gt; flip paid -&gt; flip shipped.
   *   // Strategy: walk the assertions; each step is one OpticPrograms call.
   *   // Spoiler:  the matching solution file has the full pipeline.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 7: end-to-end order processing as a Free program")
  void exercise7_realWorldWorkflow() {
    @GenerateLenses
    record Order(String id, double total, String status, boolean paid, boolean shipped) {}

    // Manual implementation (annotation processor would generate this)
    class OrderLenses {
      public static Lens<Order, String> id() {
        return Lens.of(
            Order::id,
            (o, newId) -> new Order(newId, o.total(), o.status(), o.paid(), o.shipped()));
      }

      public static Lens<Order, Double> total() {
        return Lens.of(
            Order::total,
            (o, newTotal) -> new Order(o.id(), newTotal, o.status(), o.paid(), o.shipped()));
      }

      public static Lens<Order, String> status() {
        return Lens.of(
            Order::status,
            (o, newStatus) -> new Order(o.id(), o.total(), newStatus, o.paid(), o.shipped()));
      }

      public static Lens<Order, Boolean> paid() {
        return Lens.of(
            Order::paid,
            (o, newPaid) -> new Order(o.id(), o.total(), o.status(), newPaid, o.shipped()));
      }

      public static Lens<Order, Boolean> shipped() {
        return Lens.of(
            Order::shipped,
            (o, newShipped) -> new Order(o.id(), o.total(), o.status(), o.paid(), newShipped));
      }
    }

    Order order = new Order("ORD-001", 150.0, "PENDING", false, false);

    Lens<Order, Double> totalLens = OrderLenses.total();
    Lens<Order, String> statusLens = OrderLenses.status();
    Lens<Order, Boolean> paidLens = OrderLenses.paid();
    Lens<Order, Boolean> shippedLens = OrderLenses.shipped();

    // Build an order processing workflow:
    // 1. Read the total
    // 2. If total >= 100, apply 10% discount
    // 3. Mark as paid
    // 4. Update status to "PROCESSING"
    // 5. Mark as shipped
    // 6. Update status to "SHIPPED"
    //
    // TODO: Replace null with the complete workflow program
    Free<OpticOpKind.Witness, Order> workflow =
        OpticPrograms.get(order, totalLens)
            .flatMap(
                total -> {
                  // Apply discount if eligible
                  Free<OpticOpKind.Witness, Order> afterDiscount =
                      total >= 100
                          ? OpticPrograms.modify(order, totalLens, t -> t * 0.9)
                          : OpticPrograms.pure(order);

                  // Chain the rest of the workflow
                  return afterDiscount
                      .flatMap(o1 -> OpticPrograms.set(o1, paidLens, true))
                      .flatMap(o2 -> OpticPrograms.set(o2, statusLens, "PROCESSING"))
                      .flatMap(o3 -> OpticPrograms.set(o3, shippedLens, true))
                      .flatMap(o4 -> answerRequired());
                });

    // Execute with logging
    LoggingOpticInterpreter logger = OpticInterpreters.logging();
    Order result = logger.run(workflow);

    // Verify the final state
    assertThat(result.total()).isCloseTo(135.0, within(0.01)); // 10% discount applied
    assertThat(result.paid()).isTrue();
    assertThat(result.shipped()).isTrue();
    assertThat(result.status()).isEqualTo("SHIPPED");

    // Verify we have an audit trail
    List<String> auditLog = logger.getLog();
    assertThat(auditLog).isNotEmpty();
  }

  /**
   * Congratulations! You've completed Tutorial 11: Advanced Optics DSL
   *
   * <p>You now understand: ✓ How to build optic programs as Free monad data structures ✓ Composing
   * programs with flatMap for multi-step workflows ✓ Conditional logic in programs (if-then-else) ✓
   * Multi-step transformations with chained operations ✓ Using the logging interpreter for audit
   * trails ✓ Using the validation interpreter for dry-runs ✓ Building complex real-world workflows
   *
   * <p>Key Takeaways: - Free monads separate program description from execution - Multiple
   * interpreters enable different execution strategies - Great for complex workflows requiring
   * audit trails - Logging interpreter provides automatic audit trail - Validation interpreter
   * enables dry-run checks - For simple operations, use OpticOps (Tutorial 09) instead
   *
   * <p>You've completed all optics tutorials! 🎉
   *
   * <p>You now master: - Core optics: Lens, Prism, Traversal - Composition and advanced patterns -
   * Generated optics with annotations - Fluent API for ergonomic operations - Free Monad DSL for
   * complex workflows
   */
}
