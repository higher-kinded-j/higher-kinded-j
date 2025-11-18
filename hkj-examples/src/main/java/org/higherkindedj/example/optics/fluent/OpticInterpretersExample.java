// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.Free;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.free.DirectOpticInterpreter;
import org.higherkindedj.optics.free.LoggingOpticInterpreter;
import org.higherkindedj.optics.free.OpticInterpreter;
import org.higherkindedj.optics.free.OpticInterpreters;
import org.higherkindedj.optics.free.OpticOp;
import org.higherkindedj.optics.free.OpticOpKind;
import org.higherkindedj.optics.free.OpticPrograms;
import org.higherkindedj.optics.free.ValidationOpticInterpreter;

/**
 * A runnable example demonstrating the power of different optic interpreters. This example shows
 * how the same optic program can be executed in multiple ways: directly, with logging, with
 * validation, and with custom interpreters for profiling and testing.
 *
 * <p>The scenario is a financial transaction processing system where we need to:
 *
 * <ul>
 *   <li>Execute transactions directly in production
 *   <li>Audit all operations for compliance
 *   <li>Validate operations before execution
 *   <li>Profile performance in development
 *   <li>Mock operations for testing
 * </ul>
 */
public class OpticInterpretersExample {

  // Domain model for financial transactions
  @GenerateLenses
  public record Account(String accountId, String owner, BigDecimal balance, AccountStatus status) {}

  @GenerateLenses
  public record Transaction(
      String txnId,
      Account fromAccount,
      Account toAccount,
      BigDecimal amount,
      TransactionStatus status,
      Instant timestamp) {}

  public enum AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED
  }

  public enum TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
  }

  public static void main(String[] args) {
    System.out.println("=== OPTIC INTERPRETERS EXAMPLE ===\n");

    // Create sample transaction
    Transaction txn = createSampleTransaction();
    System.out.println("Initial Transaction:");
    printTransaction(txn);
    System.out.println("\n" + "=".repeat(60) + "\n");

    directInterpreterExample(txn);
    System.out.println("\n" + "=".repeat(60) + "\n");

    loggingInterpreterExample(txn);
    System.out.println("\n" + "=".repeat(60) + "\n");

    validationInterpreterExample(txn);
    System.out.println("\n" + "=".repeat(60) + "\n");

    customProfilingInterpreterExample(txn);
    System.out.println("\n" + "=".repeat(60) + "\n");

    customMockingInterpreterExample(txn);
    System.out.println("\n" + "=".repeat(60) + "\n");

    combiningInterpretersExample(txn);
  }

  // ============================================================================
  // PART 1: Direct Interpreter - Production Execution
  // ============================================================================

  private static void directInterpreterExample(Transaction txn) {
    System.out.println("--- Part 1: Direct Interpreter (Production) ---\n");

    // Build the transaction processing program
    Free<OpticOpKind.Witness, Transaction> program = processTransactionProgram(txn);

    // Execute with direct interpreter
    System.out.println("Executing transaction directly...");
    DirectOpticInterpreter direct = OpticInterpreters.direct();
    Transaction result = direct.run(program);

    System.out.println("\nResult:");
    System.out.println("  Transaction Status: " + result.status());
    System.out.println("  From Balance: " + result.fromAccount().balance());
    System.out.println("  To Balance: " + result.toAccount().balance());
    System.out.println();
  }

  // ============================================================================
  // PART 2: Logging Interpreter - Audit Trail
  // ============================================================================

  private static void loggingInterpreterExample(Transaction txn) {
    System.out.println("--- Part 2: Logging Interpreter (Audit Trail) ---\n");

    // Build the same program
    Free<OpticOpKind.Witness, Transaction> program = processTransactionProgram(txn);

    // Execute with logging interpreter for audit trail
    System.out.println("Executing transaction with audit logging...");
    LoggingOpticInterpreter logging = OpticInterpreters.logging();
    Transaction result = logging.run(program);

    System.out.println("\n✓ Transaction completed");
    System.out.println("\nAudit Trail:");
    System.out.println("  " + "=".repeat(55));
    logging.getLog().forEach(entry -> System.out.println("  " + entry));
    System.out.println("  " + "=".repeat(55));
    System.out.println();

    // This audit log would be saved to a compliance database
    System.out.println("Audit log saved to compliance database for transaction: " + txn.txnId());
    System.out.println();
  }

  // ============================================================================
  // PART 3: Validation Interpreter - Dry-Run Testing
  // ============================================================================

  private static void validationInterpreterExample(Transaction txn) {
    System.out.println("--- Part 3: Validation Interpreter (Dry-Run) ---\n");

    // Build the program
    Free<OpticOpKind.Witness, Transaction> program = processTransactionProgram(txn);

    // Execute with validation interpreter (doesn't modify data)
    System.out.println("Validating transaction (dry-run, no changes applied)...");
    ValidationOpticInterpreter validation = OpticInterpreters.validation();
    validation.run(program);

    System.out.println("\nValidation Log:");
    System.out.println("  " + "=".repeat(55));
    validation.getLog().forEach(entry -> System.out.println("  " + entry));
    System.out.println("  " + "=".repeat(55));
    System.out.println();

    // Verify original transaction unchanged
    System.out.println("Original transaction unchanged:");
    System.out.println("  From Balance: " + txn.fromAccount().balance());
    System.out.println("  To Balance: " + txn.toAccount().balance());
    System.out.println("  Status: " + txn.status());
    System.out.println();
  }

  // ============================================================================
  // PART 4: Custom Profiling Interpreter
  // ============================================================================

  private static void customProfilingInterpreterExample(Transaction txn) {
    System.out.println("--- Part 4: Custom Profiling Interpreter ---\n");

    // Build a more complex program to profile
    Free<OpticOpKind.Witness, Transaction> program = complexTransactionProgram(txn);

    // Execute with profiling interpreter
    System.out.println("Executing transaction with performance profiling...");
    ProfilingOpticInterpreter profiler = new ProfilingOpticInterpreter();
    Transaction result = profiler.run(program);

    System.out.println("\n✓ Transaction completed");
    System.out.println("\nPerformance Profile:");
    System.out.println("  " + "=".repeat(55));
    profiler
        .getProfile()
        .forEach(
            (opType, stats) -> {
              System.out.printf(
                  "  %-15s: %d operations, avg %.3fms%n", opType, stats.count, stats.avgTimeMs);
            });
    System.out.println("  " + "=".repeat(55));
    System.out.println("  Total operations: " + profiler.getTotalOperations());
    System.out.println();
  }

  // ============================================================================
  // PART 5: Custom Mocking Interpreter for Testing
  // ============================================================================

  private static void customMockingInterpreterExample(Transaction txn) {
    System.out.println("--- Part 5: Custom Mocking Interpreter (Testing) ---\n");

    // Build the program
    Free<OpticOpKind.Witness, Transaction> program = processTransactionProgram(txn);

    // Execute with mocking interpreter
    System.out.println("Executing transaction with mocked data...");
    MockingOpticInterpreter mocker = new MockingOpticInterpreter();

    // Configure mock responses
    mocker.mockGet(AccountLenses.balance(), new BigDecimal("999999.99"));

    Transaction result = mocker.run(program);

    System.out.println("\nMocked Result:");
    System.out.println("  From Balance (mocked): " + result.fromAccount().balance());
    System.out.println("  To Balance (mocked): " + result.toAccount().balance());
    System.out.println();

    System.out.println("Mock Operations Log:");
    mocker.getLog().forEach(entry -> System.out.println("  " + entry));
    System.out.println();
  }

  // ============================================================================
  // PART 6: Combining Interpreters
  // ============================================================================

  private static void combiningInterpretersExample(Transaction txn) {
    System.out.println("--- Part 6: Combining Interpreters ---\n");

    Free<OpticOpKind.Witness, Transaction> program = processTransactionProgram(txn);

    System.out.println("Phase 1: Validation");
    ValidationOpticInterpreter validator = OpticInterpreters.validation();
    validator.run(program);

    if (validator.getLog().stream().anyMatch(log -> log.contains("ERROR"))) {
      System.out.println("  ✗ Validation failed, transaction aborted");
      return;
    }
    System.out.println("  ✓ Validation passed");
    System.out.println();

    System.out.println("Phase 2: Execution with Audit Logging");
    LoggingOpticInterpreter logger = OpticInterpreters.logging();
    Transaction result = logger.run(program);
    System.out.println("  ✓ Transaction completed");
    System.out.println();

    System.out.println("Phase 3: Profiling (Development)");
    ProfilingOpticInterpreter profiler = new ProfilingOpticInterpreter();
    profiler.run(program);
    System.out.println("  ✓ Performance metrics collected");
    System.out.println("  Total operations: " + profiler.getTotalOperations());
    System.out.println();

    System.out.println("All phases completed successfully!");
  }

  // ============================================================================
  // Program Builders
  // ============================================================================

  /** Builds a program that processes a financial transaction. */
  private static Free<OpticOpKind.Witness, Transaction> processTransactionProgram(Transaction txn) {
    BigDecimal amount = txn.amount();

    // Step 1: Update status to PROCESSING
    return OpticPrograms.set(txn, TransactionLenses.status(), TransactionStatus.PROCESSING)
        .flatMap(
            t1 ->
                // Step 2: Deduct from source account
                OpticPrograms.modify(
                    t1,
                    TransactionLenses.fromAccount().andThen(AccountLenses.balance()),
                    balance -> balance.subtract(amount)))
        .flatMap(
            t2 ->
                // Step 3: Add to destination account
                OpticPrograms.modify(
                    t2,
                    TransactionLenses.toAccount().andThen(AccountLenses.balance()),
                    balance -> balance.add(amount)))
        .flatMap(
            t3 ->
                // Step 4: Update status to COMPLETED
                OpticPrograms.set(t3, TransactionLenses.status(), TransactionStatus.COMPLETED));
  }

  /** Builds a more complex program for profiling demonstration. */
  private static Free<OpticOpKind.Witness, Transaction> complexTransactionProgram(Transaction txn) {
    return processTransactionProgram(txn)
        .flatMap(
            t1 ->
                // Additional operations for profiling
                OpticPrograms.get(
                    t1, TransactionLenses.fromAccount().andThen(AccountLenses.balance())))
        .flatMap(
            fromBalance ->
                OpticPrograms.get(
                    txn, TransactionLenses.toAccount().andThen(AccountLenses.balance())))
        .flatMap(toBalance -> OpticPrograms.set(txn, TransactionLenses.timestamp(), Instant.now()));
  }

  // ============================================================================
  // Custom Interpreter Implementations
  // ============================================================================

  /** Custom interpreter that profiles optic operation performance. */
  static class ProfilingOpticInterpreter implements OpticInterpreter {
    private final Map<String, OperationStats> profile = new HashMap<>();
    private int totalOps = 0;

    static class OperationStats {
      int count = 0;
      long totalTimeNs = 0;
      double avgTimeMs = 0.0;

      void addMeasurement(long timeNs) {
        count++;
        totalTimeNs += timeNs;
        avgTimeMs = (totalTimeNs / 1_000_000.0) / count;
      }
    }

    @Override
    public <A> A run(Free<OpticOpKind.Witness, A> program) {
      return program.resume(
          op -> {
            String opType = getOperationType(op);
            long startTime = System.nanoTime();

            Object result = executeOp(op);

            long endTime = System.nanoTime();
            profile
                .computeIfAbsent(opType, k -> new OperationStats())
                .addMeasurement(endTime - startTime);
            totalOps++;

            @SuppressWarnings("unchecked")
            Free<OpticOpKind.Witness, A> next = (Free<OpticOpKind.Witness, A>) result;
            return run(next);
          },
          a -> a);
    }

    private String getOperationType(OpticOp<?, ?, ?, ?> op) {
      if (op instanceof OpticOp.Get<?, ?, ?, ?>) return "GET";
      if (op instanceof OpticOp.Set<?, ?, ?, ?>) return "SET";
      if (op instanceof OpticOp.Modify<?, ?, ?, ?>) return "MODIFY";
      if (op instanceof OpticOp.GetAll<?, ?, ?, ?>) return "GET_ALL";
      if (op instanceof OpticOp.ModifyAll<?, ?, ?, ?>) return "MODIFY_ALL";
      return "UNKNOWN";
    }

    private Object executeOp(OpticOp<?, ?, ?, ?> op) {
      DirectOpticInterpreter direct = OpticInterpreters.direct();
      if (op instanceof OpticOp.Get<?, ?, ?, ?> get) {
        return get.next().apply(direct.executeGet(get));
      } else if (op instanceof OpticOp.Set<?, ?, ?, ?> set) {
        return set.next().apply(direct.executeSet(set));
      } else if (op instanceof OpticOp.Modify<?, ?, ?, ?> modify) {
        return modify.next().apply(direct.executeModify(modify));
      } else if (op instanceof OpticOp.GetAll<?, ?, ?, ?> getAll) {
        return getAll.next().apply(direct.executeGetAll(getAll));
      } else if (op instanceof OpticOp.ModifyAll<?, ?, ?, ?> modifyAll) {
        return modifyAll.next().apply(direct.executeModifyAll(modifyAll));
      }
      throw new UnsupportedOperationException("Unknown operation type");
    }

    public Map<String, OperationStats> getProfile() {
      return profile;
    }

    public int getTotalOperations() {
      return totalOps;
    }
  }

  /** Custom interpreter that mocks optic operations for testing. */
  static class MockingOpticInterpreter implements OpticInterpreter {
    private final Map<Object, Object> mockData = new HashMap<>();
    private final List<String> log = new ArrayList<>();

    public <S, A> void mockGet(Lens<S, A> lens, A mockValue) {
      mockData.put(lens, mockValue);
    }

    @Override
    public <A> A run(Free<OpticOpKind.Witness, A> program) {
      return program.resume(
          op -> {
            Object result = executeWithMocks(op);
            @SuppressWarnings("unchecked")
            Free<OpticOpKind.Witness, A> next = (Free<OpticOpKind.Witness, A>) result;
            return run(next);
          },
          a -> a);
    }

    private Object executeWithMocks(OpticOp<?, ?, ?, ?> op) {
      if (op instanceof OpticOp.Get<?, ?, ?, ?> get) {
        Object mockValue = mockData.get(get.optic());
        if (mockValue != null) {
          log.add("MOCK GET: " + get.optic().getClass().getSimpleName() + " -> " + mockValue);
          return get.next().apply(mockValue);
        } else {
          log.add("REAL GET: " + get.optic().getClass().getSimpleName());
          DirectOpticInterpreter direct = OpticInterpreters.direct();
          return get.next().apply(direct.executeGet(get));
        }
      } else if (op instanceof OpticOp.Set<?, ?, ?, ?> set) {
        log.add("MOCK SET: " + set.optic().getClass().getSimpleName() + " -> " + set.newValue());
        DirectOpticInterpreter direct = OpticInterpreters.direct();
        return set.next().apply(direct.executeSet(set));
      } else if (op instanceof OpticOp.Modify<?, ?, ?, ?> modify) {
        log.add("MOCK MODIFY: " + modify.optic().getClass().getSimpleName());
        DirectOpticInterpreter direct = OpticInterpreters.direct();
        return modify.next().apply(direct.executeModify(modify));
      } else if (op instanceof OpticOp.GetAll<?, ?, ?, ?> getAll) {
        log.add("MOCK GET_ALL: " + getAll.optic().getClass().getSimpleName());
        DirectOpticInterpreter direct = OpticInterpreters.direct();
        return getAll.next().apply(direct.executeGetAll(getAll));
      } else if (op instanceof OpticOp.ModifyAll<?, ?, ?, ?> modifyAll) {
        log.add("MOCK MODIFY_ALL: " + modifyAll.optic().getClass().getSimpleName());
        DirectOpticInterpreter direct = OpticInterpreters.direct();
        return modifyAll.next().apply(direct.executeModifyAll(modifyAll));
      }
      throw new UnsupportedOperationException("Unknown operation type");
    }

    public List<String> getLog() {
      return log;
    }
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  private static Transaction createSampleTransaction() {
    Account fromAccount =
        new Account("ACC-001", "Alice Smith", new BigDecimal("1000.00"), AccountStatus.ACTIVE);

    Account toAccount =
        new Account("ACC-002", "Bob Jones", new BigDecimal("500.00"), AccountStatus.ACTIVE);

    return new Transaction(
        "TXN-2025-001",
        fromAccount,
        toAccount,
        new BigDecimal("100.00"),
        TransactionStatus.PENDING,
        Instant.now());
  }

  private static void printTransaction(Transaction txn) {
    System.out.println("  Transaction ID: " + txn.txnId());
    System.out.println("  Amount: £" + txn.amount());
    System.out.println("  Status: " + txn.status());
    System.out.println(
        "  From: "
            + txn.fromAccount().owner()
            + " (Balance: £"
            + txn.fromAccount().balance()
            + ")");
    System.out.println(
        "  To: " + txn.toAccount().owner() + " (Balance: £" + txn.toAccount().balance() + ")");
  }
}
