// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.higherkindedj.hkt.free_ap.FreeApApplicative;
import org.higherkindedj.hkt.free_ap.FreeApKindHelper;
import org.higherkindedj.hkt.free_ap.SelectiveAnalyzer;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 11: Static Analysis of Free Applicative Programs
 *
 * <p>One of the key advantages of Free Applicative over Free Monad is the ability to statically
 * analyse programs BEFORE execution. Because all operations are independent (no operation depends
 * on the result of another), we can inspect the entire program structure.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>Counting operations before execution
 *   <li>Collecting and inspecting all operations
 *   <li>Checking for dangerous operations (security)
 *   <li>Grouping operations by type (batching potential)
 *   <li>Custom analysis using the Const functor
 * </ul>
 *
 * <p>This is part of the "expressiveness spectrum" - by choosing Applicative over Monad, we trade
 * some expressive power for the ability to reason about programs statically.
 *
 * <p>Links to documentation: <a
 * href="https://higher-kinded-j.github.io/latest/functional/abstraction_levels.html">Choosing
 * Abstraction Levels</a>
 */
public class Tutorial11_StaticAnalysis {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ============================================================================
  // Test DSL: Data Operations
  // ============================================================================

  /** A simple DSL for data operations. */
  sealed interface DataOp<A> {
    record Fetch(String key) implements DataOp<String> {}

    record Store(String key, String value) implements DataOp<Integer> {}

    record Remove(String key) implements DataOp<Boolean> {}
  }

  /** HKT bridge for DataOp. */
  interface DataOpKind<A> extends Kind<DataOpKind.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
      private Witness() {}
    }
  }

  /** Helper for DataOp HKT conversions. */
  enum DataOpHelper {
    DATA_OP;

    record Holder<A>(DataOp<A> op) implements DataOpKind<A> {}

    public <A> Kind<DataOpKind.Witness, A> widen(DataOp<A> op) {
      return new Holder<>(op);
    }

    @SuppressWarnings("unchecked")
    public <A> DataOp<A> narrow(Kind<DataOpKind.Witness, ?> kind) {
      return (DataOp<A>) ((Holder<?>) kind).op();
    }
  }

  // Smart constructors for cleaner code
  private static FreeAp<DataOpKind.Witness, String> fetch(String key) {
    return FreeAp.lift(DataOpHelper.DATA_OP.widen(new DataOp.Fetch(key)));
  }

  private static FreeAp<DataOpKind.Witness, Integer> store(String key, String value) {
    return FreeAp.lift(DataOpHelper.DATA_OP.widen(new DataOp.Store(key, value)));
  }

  private static FreeAp<DataOpKind.Witness, Boolean> remove(String key) {
    return FreeAp.lift(DataOpHelper.DATA_OP.widen(new DataOp.Remove(key)));
  }

  // ============================================================================
  // Exercise 1: Counting Operations
  // ============================================================================

  /**
   * Exercise 1: Count operations in a program
   *
   * <p>Before running a program, we can count how many operations it will perform. This is useful
   * for cost estimation, rate limiting, or progress tracking.
   *
   * <p>Task: Use FreeApAnalyzer.countOperations to count the operations
   */
  @Test
  void exercise1_countOperations() {
    // A program that fetches two keys and stores one
    FreeAp<DataOpKind.Witness, String> program =
        fetch("user:123").map2(fetch("user:456"), (a, b) -> a + b);

    // TODO: Count the operations in the program
    // Hint: FreeApAnalyzer.countOperations(program)
    int count = answerRequired();

    assertThat(count).isEqualTo(2);
  }

  // ============================================================================
  // Exercise 2: Collecting Operations
  // ============================================================================

  /**
   * Exercise 2: Collect all operations from a program
   *
   * <p>We can extract all operations from a program into a list for inspection. This is useful for
   * logging, auditing, or generating documentation.
   *
   * <p>Task: Use FreeApAnalyzer.collectOperations to get the operations
   */
  @Test
  void exercise2_collectOperations() {
    FreeAp<DataOpKind.Witness, String> program =
        fetch("config")
            .map2(store("cache", "data"), (config, _) -> config)
            .map2(fetch("settings"), (config, settings) -> config + settings);

    // TODO: Collect all operations from the program
    // Hint: FreeApAnalyzer.collectOperations(program)
    List<Kind<DataOpKind.Witness, ?>> operations = answerRequired();

    assertThat(operations).hasSize(3);

    // Verify we can inspect the operations
    List<String> opNames =
        operations.stream()
            .map(DataOpHelper.DATA_OP::narrow)
            .map(op -> op.getClass().getSimpleName())
            .toList();

    assertThat(opNames).containsExactlyInAnyOrder("Fetch", "Store", "Fetch");
  }

  // ============================================================================
  // Exercise 3: Checking for Dangerous Operations
  // ============================================================================

  /**
   * Exercise 3: Check for dangerous operations
   *
   * <p>Before executing a program, we can check if it contains dangerous operations (like Remove).
   * This is crucial for security: we can reject programs before they execute any effects.
   *
   * <p>Task: Use FreeApAnalyzer.containsOperation to check for Remove operations
   */
  @Test
  void exercise3_checkDangerousOperations() {
    FreeAp<DataOpKind.Witness, String> safeProgram = fetch("key1").map2(fetch("key2"), (a, b) -> a);

    FreeAp<DataOpKind.Witness, String> dangerousProgram =
        fetch("key1").map2(remove("key1"), (value, deleted) -> deleted ? "removed" : value);

    // TODO: Check if each program contains Remove operations
    // Hint: FreeApAnalyzer.containsOperation(program, op -> ...)
    boolean safeHasRemove = answerRequired();
    boolean dangerousHasRemove = answerRequired();

    assertThat(safeHasRemove).isFalse();
    assertThat(dangerousHasRemove).isTrue();
  }

  // ============================================================================
  // Exercise 4: Grouping by Type
  // ============================================================================

  /**
   * Exercise 4: Group operations by type
   *
   * <p>Grouping operations by type helps identify batching opportunities. If we have 10 Fetch
   * operations, we might be able to batch them into a single database query.
   *
   * <p>Task: Use FreeApAnalyzer.groupByType to categorise operations
   */
  @Test
  void exercise4_groupByType() {
    FreeApApplicative<DataOpKind.Witness> app = FreeApApplicative.instance();

    FreeAp<DataOpKind.Witness, String> program =
        FreeApKindHelper.FREE_AP.narrow(
            app.map4(
                FreeApKindHelper.FREE_AP.widen(fetch("user:1")),
                FreeApKindHelper.FREE_AP.widen(fetch("user:2")),
                FreeApKindHelper.FREE_AP.widen(store("log", "access")),
                FreeApKindHelper.FREE_AP.widen(fetch("user:3")),
                (u1, u2, _, u3) -> u1 + u2 + u3));

    // TODO: Group operations by their class
    // Hint: FreeApAnalyzer.groupByType(program, DataOpHelper.DATA_OP::narrow)
    Map<Class<?>, Integer> groups = answerRequired();

    assertThat(groups.get(DataOp.Fetch.class)).isEqualTo(3);
    assertThat(groups.get(DataOp.Store.class)).isEqualTo(1);
  }

  // ============================================================================
  // Exercise 5: Custom Analysis with Const
  // ============================================================================

  /**
   * Exercise 5: Custom analysis using the Const functor
   *
   * <p>The Const functor allows us to perform arbitrary analysis. We provide a natural
   * transformation that extracts information from each operation, and a Monoid to combine the
   * results.
   *
   * <p>Task: Collect all keys accessed by the program
   */
  @Test
  void exercise5_customAnalysis() {
    FreeAp<DataOpKind.Witness, String> program =
        fetch("user:123")
            .map2(store("cache:user:123", "data"), (u, _) -> u)
            .map2(fetch("settings:global"), (u, s) -> u + s);

    // Define a natural transformation that extracts keys
    Natural<DataOpKind.Witness, ConstKind.Witness<Set<String>>> extractKeys =
        new Natural<>() {
          @Override
          public <A> Kind<ConstKind.Witness<Set<String>>, A> apply(Kind<DataOpKind.Witness, A> fa) {
            DataOp<?> op = DataOpHelper.DATA_OP.narrow(fa);
            String key = extractKey(op);
            return ConstKindHelper.CONST.widen(new Const<>(Set.of(key)));
          }
        };

    Monoid<Set<String>> setMonoid = Monoids.set();

    // TODO: Use FreeApAnalyzer.analyseWith to collect all keys
    // Hint: FreeApAnalyzer.analyseWith(program, extractKeys, setMonoid)
    Set<String> keys = answerRequired();

    assertThat(keys).containsExactlyInAnyOrder("user:123", "cache:user:123", "settings:global");
  }

  private static String extractKey(DataOp<?> op) {
    return switch (op) {
      case DataOp.Fetch f -> f.key();
      case DataOp.Store s -> s.key();
      case DataOp.Remove r -> r.key();
    };
  }

  // ============================================================================
  // Exercise 6: Effect Bounds
  // ============================================================================

  /**
   * Exercise 6: Compute effect bounds
   *
   * <p>For programs with conditional logic, we can compute bounds on how many effects might
   * execute. For pure FreeAp (no conditions), min equals max.
   *
   * <p>Task: Use SelectiveAnalyzer to compute effect bounds
   */
  @Test
  void exercise6_effectBounds() {
    FreeAp<DataOpKind.Witness, String> program =
        fetch("key1").map2(fetch("key2"), (a, b) -> a + b).map2(fetch("key3"), (ab, c) -> ab + c);

    // TODO: Compute effect bounds for the program
    // Hint: SelectiveAnalyzer.computeEffectBounds(program)
    SelectiveAnalyzer.EffectBounds bounds = answerRequired();

    assertThat(bounds.minimum()).isEqualTo(3);
    assertThat(bounds.maximum()).isEqualTo(3);
    assertThat(bounds.isTight()).isTrue();
  }

  // ============================================================================
  // Exercise 7: Collect Possible Effects
  // ============================================================================

  /**
   * Exercise 7: Collect all possible effects
   *
   * <p>SelectiveAnalyzer can collect all possible effects, deduplicating identical operations. This
   * uses Set semantics, so repeated operations appear once.
   *
   * <p>Task: Collect possible effects and verify deduplication
   */
  @Test
  void exercise7_collectPossibleEffects() {
    // Same fetch twice
    FreeAp<DataOpKind.Witness, String> program =
        fetch("config").map2(fetch("config"), (a, b) -> a + b);

    // TODO: Collect possible effects (should deduplicate)
    // Hint: SelectiveAnalyzer.collectPossibleEffects(program, DataOpHelper.DATA_OP::narrow)
    Set<DataOp<?>> effects = answerRequired();

    // Records have structural equality, so identical Fetch operations are deduplicated
    assertThat(effects).hasSize(1);
    assertThat(effects.iterator().next()).isEqualTo(new DataOp.Fetch("config"));
  }

  // ============================================================================
  // Exercise 8: Dangerous Effect Detection
  // ============================================================================

  /**
   * Exercise 8: Check for dangerous effects using SelectiveAnalyzer
   *
   * <p>SelectiveAnalyzer provides a convenient method to check if a program contains dangerous
   * operations based on a predicate.
   *
   * <p>Task: Use containsDangerousEffect to check for Remove operations
   */
  @Test
  void exercise8_containsDangerousEffect() {
    FreeAp<DataOpKind.Witness, String> program =
        fetch("user")
            .map2(store("backup", "user_data"), (u, _) -> u)
            .map2(remove("user"), (u, deleted) -> deleted ? "cleaned" : u);

    // TODO: Check if the program contains dangerous (Remove) operations
    // Hint: SelectiveAnalyzer.containsDangerousEffect(program, narrow, predicate)
    boolean hasDangerous = answerRequired();

    assertThat(hasDangerous).isTrue();
  }

  // ============================================================================
  // Exercise 9: Real-World Permission Check
  // ============================================================================

  /**
   * Exercise 9: Implement a permission check
   *
   * <p>Combine what you've learned to implement a permission check: analyse a program and determine
   * if the user has permission to run it.
   *
   * <p>Task: Check if a "read-only" user can run a program
   */
  @Test
  void exercise9_permissionCheck() {
    // A program that reads and writes
    FreeAp<DataOpKind.Witness, String> program =
        fetch("data").map2(store("cache", "data"), (data, _) -> data);

    // User permissions
    Set<Class<?>> readOnlyPermissions = Set.of(DataOp.Fetch.class);
    Set<Class<?>> readWritePermissions = Set.of(DataOp.Fetch.class, DataOp.Store.class);

    // TODO: Check if each user can run the program
    // 1. Collect all operations
    // 2. Check if all operation types are in the user's permissions
    // Hint: Collect operations, get their classes, check against permissions
    boolean readOnlyCanRun = answerRequired();
    boolean readWriteCanRun = answerRequired();

    assertThat(readOnlyCanRun).isFalse(); // Read-only user cannot Store
    assertThat(readWriteCanRun).isTrue(); // Read-write user can Fetch and Store
  }

  // ============================================================================
  // Exercise 10: Batching Analysis
  // ============================================================================

  /**
   * Exercise 10: Analyse for batching opportunities
   *
   * <p>A smart interpreter could batch multiple Fetch operations into a single database query. Use
   * groupByType to identify batching opportunities.
   *
   * <p>Task: Identify how many Fetch operations could be batched
   */
  @Test
  void exercise10_batchingAnalysis() {
    FreeApApplicative<DataOpKind.Witness> app = FreeApApplicative.instance();

    // A dashboard that fetches multiple users
    FreeAp<DataOpKind.Witness, String> dashboard =
        FreeApKindHelper.FREE_AP.narrow(
            app.map4(
                FreeApKindHelper.FREE_AP.widen(fetch("user:1")),
                FreeApKindHelper.FREE_AP.widen(fetch("user:2")),
                FreeApKindHelper.FREE_AP.widen(fetch("user:3")),
                FreeApKindHelper.FREE_AP.widen(fetch("user:4")),
                (u1, u2, u3, u4) -> String.join(", ", u1, u2, u3, u4)));

    // TODO: Analyse the program to find batching opportunities
    // How many Fetch operations could be batched?
    Map<Class<?>, Integer> groups = answerRequired();
    int batchableFetches = answerRequired();

    assertThat(batchableFetches).isEqualTo(4);

    // A smart interpreter could execute all 4 fetches in a single batch query!
    System.out.println("Potential optimisation: " + batchableFetches + " fetches could be batched");
  }

  /**
   * Congratulations! You've completed Tutorial 11: Static Analysis
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How to count operations before execution
   *   <li>✓ How to collect and inspect all operations
   *   <li>✓ How to check for dangerous operations
   *   <li>✓ How to group operations for batching
   *   <li>✓ How to perform custom analysis with Const
   *   <li>✓ How to compute effect bounds
   *   <li>✓ Real-world permission checking patterns
   * </ul>
   *
   * <p>Key Insight: By choosing Free Applicative over Free Monad, you gain the ability to analyse
   * programs statically. This is the power of the expressiveness spectrum.
   *
   * <p>Next Steps:
   *
   * <ul>
   *   <li>Explore the StaticAnalysisExample in hkj-examples
   *   <li>Explore the PermissionCheckingExample in hkj-examples
   *   <li>Read about Selective Applicative Functors for more nuanced control
   * </ul>
   */
}
