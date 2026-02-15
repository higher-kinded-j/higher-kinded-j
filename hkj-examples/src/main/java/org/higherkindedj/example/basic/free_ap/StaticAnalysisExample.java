// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.free_ap;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.constant.ConstKind;
import org.higherkindedj.hkt.constant.ConstKindHelper;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.free_ap.FreeApAnalyzer;
import org.higherkindedj.hkt.free_ap.FreeApApplicative;
import org.higherkindedj.hkt.free_ap.FreeApKindHelper;
import org.higherkindedj.hkt.free_ap.SelectiveAnalyzer;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;

/**
 * Demonstrates static analysis of Free Applicative programs.
 *
 * <p>This example shows how to analyse FreeAp programs before execution:
 *
 * <ul>
 *   <li>Counting operations before running
 *   <li>Collecting all operations for inspection
 *   <li>Checking for dangerous operations
 *   <li>Grouping operations by type for batching analysis
 *   <li>Custom analysis using the Const functor
 * </ul>
 *
 * <p>Key insight: Because Free Applicative captures independent computations with a visible
 * structure, we can inspect the entire program before executing any effects.
 *
 * <p>Run with: ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.basic.free_ap.StaticAnalysisExample
 */
public class StaticAnalysisExample {

  // ============================================================================
  // Domain Model
  // ============================================================================

  /** Database operation DSL. */
  sealed interface DbOp<A> {
    record Query(String sql) implements DbOp<List<String>> {}

    record Insert(String table, String data) implements DbOp<Integer> {}

    record Update(String table, String where, String data) implements DbOp<Integer> {}

    record Delete(String table, String where) implements DbOp<Integer> {}
  }

  /** HKT bridge for DbOp. */
  interface DbOpKind<A> extends Kind<DbOpKind.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
      private Witness() {}
    }
  }

  /** Helper for DbOp HKT conversions. */
  enum DbOpHelper {
    DB_OP;

    record Holder<A>(DbOp<A> op) implements DbOpKind<A> {}

    public <A> Kind<DbOpKind.Witness, A> widen(DbOp<A> op) {
      return new Holder<>(op);
    }

    @SuppressWarnings("unchecked")
    public <A> DbOp<A> narrow(Kind<DbOpKind.Witness, ?> kind) {
      return (DbOp<A>) ((Holder<?>) kind).op();
    }
  }

  // ============================================================================
  // Smart Constructors
  // ============================================================================

  static FreeAp<DbOpKind.Witness, List<String>> query(String sql) {
    return FreeAp.lift(DbOpHelper.DB_OP.widen(new DbOp.Query(sql)));
  }

  static FreeAp<DbOpKind.Witness, Integer> insert(String table, String data) {
    return FreeAp.lift(DbOpHelper.DB_OP.widen(new DbOp.Insert(table, data)));
  }

  static FreeAp<DbOpKind.Witness, Integer> update(String table, String where, String data) {
    return FreeAp.lift(DbOpHelper.DB_OP.widen(new DbOp.Update(table, where, data)));
  }

  static FreeAp<DbOpKind.Witness, Integer> delete(String table, String where) {
    return FreeAp.lift(DbOpHelper.DB_OP.widen(new DbOp.Delete(table, where)));
  }

  // ============================================================================
  // Example Programs
  // ============================================================================

  /** A safe program: only reads data. */
  static FreeAp<DbOpKind.Witness, String> safeReadProgram() {
    return query("SELECT * FROM users")
        .map2(query("SELECT * FROM orders"), (users, orders) -> "Users: " + users.size());
  }

  /** A program with writes: requires more careful handling. */
  static FreeAp<DbOpKind.Witness, String> writeProgram() {
    FreeApApplicative<DbOpKind.Witness> app = FreeApApplicative.instance();

    return FreeApKindHelper.FREE_AP.narrow(
        app.map4(
            FreeApKindHelper.FREE_AP.widen(query("SELECT id FROM users WHERE active = false")),
            FreeApKindHelper.FREE_AP.widen(insert("audit_log", "cleanup started")),
            FreeApKindHelper.FREE_AP.widen(update("users", "active = false", "archived = true")),
            FreeApKindHelper.FREE_AP.widen(insert("audit_log", "cleanup completed")),
            (users, log1, updated, log2) -> "Archived " + updated + " users"));
  }

  /** A dangerous program: includes delete operations. */
  static FreeAp<DbOpKind.Witness, String> dangerousProgram() {
    return query("SELECT id FROM temp_data WHERE age > 30")
        .map2(delete("temp_data", "age > 30"), (ids, deleted) -> "Deleted " + deleted + " rows");
  }

  // ============================================================================
  // Analysis Demonstrations
  // ============================================================================

  /** Demonstrates basic operation counting. */
  static void demonstrateOperationCounting() {
    System.out.println("=== Operation Counting ===\n");

    FreeAp<DbOpKind.Witness, String> program = writeProgram();

    // Count operations before execution
    int opCount = FreeApAnalyzer.countOperations(program);
    System.out.println("Program will execute " + opCount + " operations");
    System.out.println("(We know this WITHOUT running any database queries!)\n");
  }

  /** Demonstrates operation collection. */
  static void demonstrateOperationCollection() {
    System.out.println("=== Operation Collection ===\n");

    FreeAp<DbOpKind.Witness, String> program = writeProgram();

    // Collect all operations
    List<Kind<DbOpKind.Witness, ?>> operations = FreeApAnalyzer.collectOperations(program);

    System.out.println("Operations in program:");
    for (int i = 0; i < operations.size(); i++) {
      DbOp<?> op = DbOpHelper.DB_OP.narrow(operations.get(i));
      System.out.println("  " + (i + 1) + ". " + describeOperation(op));
    }
    System.out.println();
  }

  /** Demonstrates dangerous operation detection. */
  static void demonstrateDangerousOperationCheck() {
    System.out.println("=== Dangerous Operation Detection ===\n");

    FreeAp<DbOpKind.Witness, String> safeProgram = safeReadProgram();
    FreeAp<DbOpKind.Witness, String> dangerousProgram = dangerousProgram();

    // Check each program
    boolean safeHasDelete =
        FreeApAnalyzer.containsOperation(
            safeProgram, op -> DbOp.Delete.class.isInstance(DbOpHelper.DB_OP.narrow(op)));

    boolean dangerousHasDelete =
        FreeApAnalyzer.containsOperation(
            dangerousProgram, op -> DbOp.Delete.class.isInstance(DbOpHelper.DB_OP.narrow(op)));

    System.out.println("Safe program contains DELETE: " + safeHasDelete);
    System.out.println("Dangerous program contains DELETE: " + dangerousHasDelete);

    if (dangerousHasDelete) {
      System.out.println("\n[!] SECURITY WARNING: Program contains DELETE operations!");
      System.out.println("    User approval required before execution.");
    }
    System.out.println();
  }

  /** Demonstrates grouping operations for batching analysis. */
  static void demonstrateGroupingForBatching() {
    System.out.println("=== Grouping for Batching ===\n");

    FreeAp<DbOpKind.Witness, String> program = writeProgram();

    // Group operations by type
    Map<Class<?>, Integer> groups = FreeApAnalyzer.groupByType(program, DbOpHelper.DB_OP::narrow);

    System.out.println("Operations grouped by type:");
    groups.forEach(
        (clazz, count) -> {
          String typeName = clazz.getSimpleName();
          System.out.println("  " + typeName + ": " + count);
        });

    // Batching insight
    int queryCount = groups.getOrDefault(DbOp.Query.class, 0);
    int insertCount = groups.getOrDefault(DbOp.Insert.class, 0);

    if (queryCount > 1) {
      System.out.println("\n[Optimisation hint] " + queryCount + " queries could be batched");
    }
    if (insertCount > 1) {
      System.out.println("[Optimisation hint] " + insertCount + " inserts could use batch insert");
    }
    System.out.println();
  }

  /** Demonstrates custom analysis using the Const functor. */
  static void demonstrateCustomAnalysis() {
    System.out.println("=== Custom Analysis with Const Functor ===\n");

    FreeAp<DbOpKind.Witness, String> program = writeProgram();

    // Collect table names accessed
    Monoid<Set<String>> setMonoid = Monoids.set();
    Natural<DbOpKind.Witness, ConstKind.Witness<Set<String>>> tableExtractor =
        new Natural<>() {
          @Override
          public <A> Kind<ConstKind.Witness<Set<String>>, A> apply(Kind<DbOpKind.Witness, A> fa) {
            DbOp<?> op = DbOpHelper.DB_OP.narrow(fa);
            String table = extractTable(op);
            return ConstKindHelper.CONST.widen(new Const<>(Set.of(table)));
          }
        };

    Set<String> tables = FreeApAnalyzer.analyseWith(program, tableExtractor, setMonoid);

    System.out.println("Tables accessed by program:");
    tables.forEach(table -> System.out.println("  - " + table));
    System.out.println();
  }

  /** Demonstrates selective analysis utilities. */
  static void demonstrateSelectiveAnalysis() {
    System.out.println("=== Selective Analysis Utilities ===\n");

    FreeAp<DbOpKind.Witness, String> program = dangerousProgram();

    // Compute effect bounds
    SelectiveAnalyzer.EffectBounds bounds = SelectiveAnalyzer.computeEffectBounds(program);
    System.out.println("Effect bounds:");
    System.out.println("  Minimum effects: " + bounds.minimum());
    System.out.println("  Maximum effects: " + bounds.maximum());
    System.out.println("  Bounds are tight: " + bounds.isTight());

    // Partition effects
    SelectiveAnalyzer.EffectPartition<DbOp<?>> partition =
        SelectiveAnalyzer.partitionEffects(program, DbOpHelper.DB_OP::narrow);

    System.out.println("\nEffect partition:");
    System.out.println("  Guaranteed effects: " + partition.guaranteed().size());
    System.out.println("  Conditional effects: " + partition.conditional().size());
    System.out.println("  All guaranteed: " + partition.allGuaranteed());

    // Check for dangerous effects
    boolean hasDangerous =
        SelectiveAnalyzer.containsDangerousEffect(
            program, DbOpHelper.DB_OP::narrow, op -> DbOp.Delete.class.isInstance(op));

    System.out.println("\nContains dangerous (DELETE) operations: " + hasDangerous);
    System.out.println();
  }

  /** Demonstrates the full workflow: analyse, then execute. */
  static void demonstrateFullWorkflow() {
    System.out.println("=== Full Workflow: Analyse Then Execute ===\n");

    FreeAp<DbOpKind.Witness, String> program = writeProgram();

    // Step 1: Static analysis
    System.out.println("Step 1: Static Analysis");
    int opCount = FreeApAnalyzer.countOperations(program);
    boolean hasDelete =
        FreeApAnalyzer.containsOperation(
            program, op -> DbOp.Delete.class.isInstance(DbOpHelper.DB_OP.narrow(op)));

    System.out.println("  - Operation count: " + opCount);
    System.out.println("  - Contains DELETE: " + hasDelete);

    // Step 2: Approval check
    System.out.println("\nStep 2: Approval Check");
    if (hasDelete) {
      System.out.println("  [BLOCKED] DELETE operations require admin approval");
      return;
    } else {
      System.out.println("  [OK] No dangerous operations detected");
    }

    // Step 3: Execute
    System.out.println("\nStep 3: Execution");
    Natural<DbOpKind.Witness, IdKind.Witness> interpreter = createMockInterpreter();
    Kind<IdKind.Witness, String> result = program.foldMap(interpreter, IdMonad.instance());
    String output = ID.narrow(result).value();

    System.out.println("  Result: " + output);
    System.out.println("\nWorkflow completed successfully!");
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  private static String describeOperation(DbOp<?> op) {
    return switch (op) {
      case DbOp.Query q -> "QUERY: " + q.sql();
      case DbOp.Insert i -> "INSERT into " + i.table();
      case DbOp.Update u -> "UPDATE " + u.table() + " WHERE " + u.where();
      case DbOp.Delete d -> "DELETE from " + d.table() + " WHERE " + d.where();
    };
  }

  private static String extractTable(DbOp<?> op) {
    return switch (op) {
      case DbOp.Query q -> extractTableFromSql(q.sql());
      case DbOp.Insert i -> i.table();
      case DbOp.Update u -> u.table();
      case DbOp.Delete d -> d.table();
    };
  }

  private static String extractTableFromSql(String sql) {
    // Simplified table extraction from SELECT ... FROM table
    String upper = sql.toUpperCase();
    int fromIndex = upper.indexOf("FROM ");
    if (fromIndex >= 0) {
      String afterFrom = sql.substring(fromIndex + 5).trim();
      int spaceIndex = afterFrom.indexOf(' ');
      return spaceIndex > 0 ? afterFrom.substring(0, spaceIndex) : afterFrom;
    }
    return "unknown";
  }

  @SuppressWarnings("unchecked")
  private static Natural<DbOpKind.Witness, IdKind.Witness> createMockInterpreter() {
    return new Natural<>() {
      @Override
      public <A> Kind<IdKind.Witness, A> apply(Kind<DbOpKind.Witness, A> fa) {
        DbOp<?> op = DbOpHelper.DB_OP.narrow(fa);
        Object result =
            switch (op) {
              case DbOp.Query _ -> List.of("mock1", "mock2");
              case DbOp.Insert _ -> 1;
              case DbOp.Update _ -> 5;
              case DbOp.Delete _ -> 3;
            };
        return ID.widen(Id.of((A) result));
      }
    };
  }

  // ============================================================================
  // Main
  // ============================================================================

  public static void main(String[] args) {
    System.out.println("========================================");
    System.out.println("  Static Analysis of Free Applicative");
    System.out.println("========================================\n");

    demonstrateOperationCounting();
    demonstrateOperationCollection();
    demonstrateDangerousOperationCheck();
    demonstrateGroupingForBatching();
    demonstrateCustomAnalysis();
    demonstrateSelectiveAnalysis();
    demonstrateFullWorkflow();

    System.out.println("\n========================================");
    System.out.println("  Key Insight");
    System.out.println("========================================");
    System.out.println("All analysis happened BEFORE execution!");
    System.out.println("We inspected the program structure without");
    System.out.println("running any database operations.");
    System.out.println();
    System.out.println("This is the power of the expressiveness");
    System.out.println("spectrum: by choosing Free Applicative");
    System.out.println("over Free Monad, we gained the ability");
    System.out.println("to analyse programs statically.");
    System.out.println("========================================\n");
  }
}
