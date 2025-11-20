// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;

/**
 * Tutorial 09: Advanced Optics DSL - Free Monad for Complex Workflows
 *
 * <p>The Free Monad DSL allows you to build optic operations as composable data structures that
 * can be interpreted in multiple ways:
 *
 * <ul>
 *   <li><b>Direct execution</b>: Normal optic operations
 *   <li><b>Logging</b>: Record all operations for audit trails
 *   <li><b>Validation</b>: Dry-run to check constraints before executing
 *   <li><b>Testing</b>: Mock operations without real data
 *   <li><b>Optimization</b>: Analyze and fuse operations for efficiency
 * </ul>
 *
 * <p>When to Use: - Complex multi-step workflows - Audit trails (logging what changed) -
 * Validation before execution (dry-run) - Testing without side effects - Performance optimization
 * (batch operations)
 *
 * <p>When NOT to Use: - Simple one-off operations (use OpticOps instead) - No need for multiple
 * interpretations
 *
 * <p>This is an advanced topic. Complete Tutorial 08 first.
 */
public class Tutorial09_AdvancedOpticsDSL {

  /**
   * Exercise 1: Building a simple program
   *
   * <p>OpticPrograms provides methods to build optic operations as Free monad programs.
   *
   * <p>Task: Create a simple program that gets and sets a value
   */
  @Test
  void exercise1_simpleProgram() {
    @GenerateLenses
    record Person(String name, int age) {}

    Person person = new Person("Alice", 30);

    Lens<Person, String> nameLens = PersonLenses.name();
    Lens<Person, Integer> ageLens = PersonLenses.age();

    // TODO: Replace ___ with OpticPrograms.get() to create a program that reads the age
    // Hint: OpticPrograms.get(person, ageLens)
    Free<OpticOpKind.Witness, Integer> getProgram = ___;

    // Execute the program
    DirectOpticInterpreter interpreter = OpticInterpreters.direct();
    Integer age = interpreter.run(getProgram);

    assertThat(age).isEqualTo(30);

    // TODO: Replace ___ with OpticPrograms.set() to create a program that sets age to 31
    // Hint: OpticPrograms.set(person, ageLens, 31)
    Free<OpticOpKind.Witness, Person> setProgram = ___;

    Person updated = interpreter.run(setProgram);
    assertThat(updated.age()).isEqualTo(31);
  }

  /**
   * Exercise 2: Composing programs with flatMap
   *
   * <p>Programs can be composed using flatMap to create multi-step workflows.
   *
   * <p>Task: Build a program that reads a value, then uses it to compute an update
   */
  @Test
  void exercise2_composingPrograms() {
    @GenerateLenses
    record Counter(int value) {}

    Counter counter = new Counter(5);

    Lens<Counter, Integer> valueLens = CounterLenses.value();

    // Build a program that reads the current value, adds 10, then sets it back
    // TODO: Replace ___ with a composed program using flatMap
    // Hint: OpticPrograms.get(counter, valueLens).flatMap(v -> OpticPrograms.set(...))
    Free<OpticOpKind.Witness, Counter> program =
        OpticPrograms.get(counter, valueLens)
            .flatMap(currentValue -> ___);

    Counter result = OpticInterpreters.direct().run(program);
    assertThat(result.value()).isEqualTo(15);
  }

  /**
   * Exercise 3: Conditional workflows
   *
   * <p>Free monads excel at conditional logic - building different programs based on runtime
   * values.
   *
   * <p>Task: Build a program that conditionally updates based on a threshold
   */
  @Test
  void exercise3_conditionalWorkflows() {
    @GenerateLenses
    record Account(String id, double balance, String status) {}

    Account account = new Account("ACC-001", 1500.0, "PENDING");

    Lens<Account, Double> balanceLens = AccountLenses.balance();
    Lens<Account, String> statusLens = AccountLenses.status();

    // Build a program that:
    // 1. Reads the balance
    // 2. If balance >= 1000, set status to "APPROVED"
    // 3. If balance < 1000, set status to "DENIED"
    //
    // TODO: Replace ___ with a conditional program
    // Hint: Use OpticPrograms.get().flatMap() with if-else to choose which program to run
    Free<OpticOpKind.Witness, Account> program =
        OpticPrograms.get(account, balanceLens)
            .flatMap(
                balance -> {
                  if (balance >= 1000) {
                    return ___;
                  } else {
                    return ___;
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
   * Exercise 4: Multi-step transformations
   *
   * <p>Chain multiple operations together in a single program.
   *
   * <p>Task: Build a program that performs multiple updates in sequence
   */
  @Test
  void exercise4_multiStepTransformations() {
    @GenerateLenses
    record User(String id, String name, String email, boolean active) {}

    User user = new User("user1", "alice", "ALICE@EXAMPLE.COM", false);

    Lens<User, String> nameLens = UserLenses.name();
    Lens<User, String> emailLens = UserLenses.email();
    Lens<User, Boolean> activeLens = UserLenses.active();

    // Build a program that:
    // 1. Capitalizes the name (first letter uppercase, rest lowercase)
    // 2. Lowercases the email
    // 3. Sets active to true
    //
    // TODO: Replace ___ with a multi-step program using flatMap chains
    // Hint: OpticPrograms.modify().flatMap(u1 -> OpticPrograms.modify(...).flatMap(u2 -> ...))
    Free<OpticOpKind.Witness, User> program =
        OpticPrograms.modify(user, nameLens, name -> capitalize(name))
            .flatMap(
                u1 ->
                    OpticPrograms.modify(u1, emailLens, String::toLowerCase)
                        .flatMap(u2 -> ___));

    User result = OpticInterpreters.direct().run(program);
    assertThat(result.name()).isEqualTo("Alice");
    assertThat(result.email()).isEqualTo("alice@example.com");
    assertThat(result.active()).isTrue();
  }

  // Helper method for capitalization
  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
  }

  /**
   * Exercise 5: Logging interpreter for audit trails
   *
   * <p>The logging interpreter records all operations without changing behavior.
   *
   * <p>Task: Execute a program with logging to create an audit trail
   */
  @Test
  void exercise5_loggingInterpreter() {
    @GenerateLenses
    record Document(String id, String title, String status) {}

    Document doc = new Document("DOC-001", "Report", "DRAFT");

    Lens<Document, String> statusLens = DocumentLenses.status();
    Lens<Document, String> titleLens = DocumentLenses.title();

    // Build a program that updates title and status
    Free<OpticOpKind.Witness, Document> program =
        OpticPrograms.modify(doc, titleLens, title -> title + " (Revised)")
            .flatMap(d -> OpticPrograms.set(d, statusLens, "PUBLISHED"));

    // TODO: Replace ___ with the logging interpreter
    // Hint: OpticInterpreters.logging()
    LoggingOpticInterpreter logger = ___;

    Document result = logger.run(program);

    assertThat(result.title()).isEqualTo("Report (Revised)");
    assertThat(result.status()).isEqualTo("PUBLISHED");

    // Check the audit log
    // TODO: Replace ___ with logger.getLog()
    List<String> log = ___;

    assertThat(log).isNotEmpty();
    // Log should contain operations performed
    assertThat(log.size()).isGreaterThan(0);
  }

  /**
   * Exercise 6: Validation interpreter for dry-runs
   *
   * <p>The validation interpreter checks a program without executing it.
   *
   * <p>Task: Validate a program before execution
   */
  @Test
  void exercise6_validationInterpreter() {
    @GenerateLenses
    record Config(String env, int maxConnections, boolean debugMode) {}

    Config config = new Config("production", 100, false);

    Lens<Config, String> envLens = ConfigLenses.env();
    Lens<Config, Boolean> debugLens = ConfigLenses.debugMode();

    // Build a program that enables debug mode in production (generally a bad idea!)
    Free<OpticOpKind.Witness, Config> program =
        OpticPrograms.get(config, envLens)
            .flatMap(
                env -> {
                  if (env.equals("production")) {
                    return OpticPrograms.set(config, debugLens, true);
                  } else {
                    return OpticPrograms.pure(config);
                  }
                });

    // TODO: Replace ___ with the validation interpreter
    // Hint: OpticInterpreters.validation()
    ValidationOpticInterpreter validator = ___;

    // Validate the program (dry-run)
    // TODO: Replace ___ with validator.validate(program)
    List<String> issues = ___;

    // The validator can detect potential issues
    // In this simple case, it validates the structure is correct
    assertThat(issues).isNotNull();
  }

  /**
   * Exercise 7: Real-world workflow - Order processing pipeline
   *
   * <p>Build a complex multi-step workflow with conditional logic and logging.
   *
   * <p>Task: Create an order processing pipeline as a Free monad program
   */
  @Test
  void exercise7_realWorldWorkflow() {
    @GenerateLenses
    record Order(
        String id, double total, String status, boolean paid, boolean shipped) {}

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
    // TODO: Replace ___ with the complete workflow program
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
                      .flatMap(o4 -> ___);
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
   * Congratulations! You've completed Tutorial 09: Advanced Optics DSL
   *
   * <p>You now understand: ✓ How to build optic programs as Free monad data structures ✓ Composing
   * programs with flatMap for multi-step workflows ✓ Conditional logic in programs (if-then-else)
   * ✓ Multi-step transformations with chained operations ✓ Using the logging interpreter for audit
   * trails ✓ Using the validation interpreter for dry-runs ✓ Building complex real-world workflows
   *
   * <p>Key Takeaways: - Free monads separate program description from execution - Multiple
   * interpreters enable different execution strategies - Great for complex workflows requiring
   * audit trails - Logging interpreter provides automatic audit trail - Validation interpreter
   * enables dry-run checks - For simple operations, use OpticOps (Tutorial 08) instead
   *
   * <p>You've completed all optics tutorials! 🎉
   *
   * <p>You now master: - Core optics: Lens, Prism, Traversal - Composition and advanced patterns -
   * Generated optics with annotations - Fluent API for ergonomic operations - Free Monad DSL for
   * complex workflows
   */
}
