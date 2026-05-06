// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

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
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial11 AdvancedOpticsDSL — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
public class Tutorial11_AdvancedOpticsDSL_Solution {

  /**
   * Why this is idiomatic: {@code OpticPrograms} lifts get/set/modify into a Free monad program —
   * the optic operations become values you can inspect, transform, and interpret.
   *
   * <p>Alternative: call the optic methods directly. Equivalent runtime; the program form is what
   * enables logging interpreters, dry-runs, and static analysis later in the chapter.
   *
   * <p>Common wrong attempt: try to interpret the program by hand with {@code instanceof} branches
   * over the program's AST. The supplied interpreters cover the cases consistently; write a custom
   * interpreter only when the existing ones cannot express the semantics.
   */
  @Test
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

    // Create a program that reads the age using OpticPrograms.get()
    Free<OpticOpKind.Witness, Integer> getProgram = OpticPrograms.get(person, ageLens);

    // Execute the program
    DirectOpticInterpreter interpreter = OpticInterpreters.direct();
    Integer age = interpreter.run(getProgram);

    assertThat(age).isEqualTo(30);

    // Create a program that sets age to 31 using OpticPrograms.set()
    Free<OpticOpKind.Witness, Person> setProgram = OpticPrograms.set(person, ageLens, 31);

    Person updated = interpreter.run(setProgram);
    assertThat(updated.age()).isEqualTo(31);
  }

  /**
   * Why this is idiomatic: {@code flatMap} on a Free program threads the result of one step into
   * the next — read the value, add ten, write it back, all as a single description that the
   * interpreter executes.
   *
   * <p>Alternative: read with {@code lens.get}, compute, write with {@code lens.set}. Same answer;
   * the Free composition keeps the workflow as data and lets a different interpreter (audit,
   * dry-run) run the same program.
   *
   * <p>Common wrong attempt: nest plain Java lambdas inside {@code flatMap} that already call into
   * the interpreter. The point of Free is to defer interpretation; embedding side effects in the
   * program defeats it.
   */
  @Test
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
    Free<OpticOpKind.Witness, Counter> program =
        OpticPrograms.get(counter, valueLens)
            .flatMap(currentValue -> OpticPrograms.set(counter, valueLens, currentValue + 10));

    Counter result = OpticInterpreters.direct().run(program);
    assertThat(result.value()).isEqualTo(15);
  }

  /**
   * Why this is idiomatic: build different sub-programs for the threshold-met and threshold- missed
   * branches, then choose between them inside the {@code flatMap}. The dispatch is a value-level
   * decision; the interpreter still sees one program tree.
   *
   * <p>Alternative: split the workflow into two top-level methods and pick which one to call
   * outside the program. Loses the inspectability — the audit interpreter no longer sees the choice
   * in the trace.
   *
   * <p>Common wrong attempt: throw an {@code IllegalStateException} from the failing branch. The
   * interpreter would have to catch it, and the failure is now in the wrong channel; if the
   * workflow can fail, encode the failure as a value.
   */
  @Test
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
    Free<OpticOpKind.Witness, Account> program =
        OpticPrograms.get(account, balanceLens)
            .flatMap(
                balance -> {
                  if (balance >= 1000) {
                    return OpticPrograms.set(account, statusLens, "APPROVED");
                  } else {
                    return OpticPrograms.set(account, statusLens, "DENIED");
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
   * Why this is idiomatic: a sequence of {@code flatMap}s threads each updated record into the next
   * step. The interpreter sees one program with a clear order; the audit log can trace every step.
   *
   * <p>Alternative: bind intermediate values to local variables and run them through plain lens
   * calls. Equivalent runtime; loses the program-as-data benefit.
   *
   * <p>Common wrong attempt: try to fold a {@code List<Free>} with a sequencer that doesn't exist
   * on the optic Free yet. Compose with {@code flatMap} explicitly until the helper is provided.
   */
  @Test
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
    // 1. Capitalises the name (first letter uppercase, rest lowercase)
    // 2. Lowercases the email
    // 3. Sets active to true
    Free<OpticOpKind.Witness, User> program =
        OpticPrograms.modify(user, nameLens, name -> capitalise(name))
            .flatMap(
                u1 ->
                    OpticPrograms.modify(u1, emailLens, String::toLowerCase)
                        .flatMap(u2 -> OpticPrograms.set(u2, activeLens, true)));

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
   * Why this is idiomatic: same program, different interpreter. The logging interpreter walks the
   * program tree and emits one audit entry per step before delegating to the standard
   * implementation — observability without changing the workflow.
   *
   * <p>Alternative: weave logging into the workflow itself. Works, but every change to the workflow
   * has to remember to log; the interpreter approach keeps the workflow pure.
   *
   * <p>Common wrong attempt: log inside the {@code flatMap} lambdas instead of in the interpreter.
   * The audit is now duplicated whenever the program is interpreted twice (e.g. dry-run then
   * apply); keep effects in the interpreter.
   */
  @Test
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

    // Create the logging interpreter
    LoggingOpticInterpreter logger = OpticInterpreters.logging();

    Document result = logger.run(program);

    assertThat(result.title()).isEqualTo("Report (Revised)");
    assertThat(result.status()).isEqualTo("PUBLISHED");

    // Check the audit log
    List<String> log = logger.getLog();

    assertThat(log).isNotEmpty();
    // Log should contain operations performed
    assertThat(log.size()).isGreaterThan(0);
  }

  /**
   * Why this is idiomatic: the validation interpreter walks the program and reports issues without
   * writing anything. A dry-run means "would this work?" and the answer is a value, not an
   * exception — perfect for confirmation prompts and CI checks.
   *
   * <p>Alternative: copy the input, run the workflow, and discard the result. Same outcome for
   * read-only checks; the validation interpreter avoids the wasted allocation and runs faster on
   * large structures.
   *
   * <p>Common wrong attempt: assume "validation" means "type-check at compile time". The
   * interpreter validates at runtime against actual data — the two roles are complementary, not
   * redundant.
   */
  @Test
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

    // Create the validation interpreter
    ValidationOpticInterpreter validator = OpticInterpreters.validating();

    // Validate the program (dry-run)
    ValidationOpticInterpreter.ValidationResult result = validator.validate(program);

    // The validator can detect potential issues
    // In this simple case, it validates the structure is correct
    assertThat(result).isNotNull();
    assertThat(result.isValid()).isTrue();
  }

  /**
   * Why this is idiomatic: a real workflow combines reads, conditional logic, and writes. The Free
   * program lets you write each step once and then run it through several interpreters — direct
   * execution, audit logging, dry-run validation — without changes.
   *
   * <p>Alternative: hand-roll the same workflow imperatively and feed it through {@code if}
   * branches. Works for one interpreter; reproducing the audit and dry-run behaviour separately
   * means duplicating the workflow logic.
   *
   * <p>Common wrong attempt: invoke the database (or other side-effecting service) from inside the
   * program's lambdas. The Free program is a description; the interpreter is the only place real
   * effects belong.
   */
  @Test
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
                      .flatMap(o4 -> OpticPrograms.set(o4, statusLens, "SHIPPED"));
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
   * programs with flatMap for multi-step workflows ✓ Conditional logic in programs (if-then-else) ✓
   * Multi-step transformations with chained operations ✓ Using the logging interpreter for audit
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
