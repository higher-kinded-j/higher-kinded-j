// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free_ap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link SelectiveAnalyzer}.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Collecting possible effects (Over semantics)
 *   <li>Counting maximum effects
 *   <li>Computing effect bounds
 *   <li>Partitioning effects
 *   <li>Detecting dangerous operations
 *   <li>Null argument handling
 * </ul>
 */
@DisplayName("SelectiveAnalyzer Tests")
class SelectiveAnalyzerTest {

  // ============================================================================
  // Test DSL: Simple operations for testing
  // ============================================================================

  /** Simple test operation sealed interface. */
  sealed interface DbOp<A> {
    record Select(String table) implements DbOp<String> {}

    record Insert(String table, String data) implements DbOp<Integer> {}

    record Update(String table, String data) implements DbOp<Integer> {}

    record Delete(String table) implements DbOp<Integer> {}
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

  // Smart constructors
  private static FreeAp<DbOpKind.Witness, String> selectFrom(String table) {
    return FreeAp.lift(DbOpHelper.DB_OP.widen(new DbOp.Select(table)));
  }

  private static FreeAp<DbOpKind.Witness, Integer> insertInto(String table, String data) {
    return FreeAp.lift(DbOpHelper.DB_OP.widen(new DbOp.Insert(table, data)));
  }

  private static FreeAp<DbOpKind.Witness, Integer> update(String table, String data) {
    return FreeAp.lift(DbOpHelper.DB_OP.widen(new DbOp.Update(table, data)));
  }

  private static FreeAp<DbOpKind.Witness, Integer> deleteFrom(String table) {
    return FreeAp.lift(DbOpHelper.DB_OP.widen(new DbOp.Delete(table)));
  }

  // ============================================================================
  // collectPossibleEffects Tests
  // ============================================================================

  @Nested
  @DisplayName("collectPossibleEffects()")
  class CollectPossibleEffectsTests {

    @Test
    @DisplayName("returns empty set for pure programs")
    void returnsEmptySetForPure() {
      FreeAp<DbOpKind.Witness, String> program = FreeAp.pure("hello");

      Set<DbOp<?>> effects =
          SelectiveAnalyzer.collectPossibleEffects(program, DbOpHelper.DB_OP::narrow);

      assertThat(effects).isEmpty();
    }

    @Test
    @DisplayName("returns single effect for single operation")
    void returnsSingleEffectForSingleOperation() {
      FreeAp<DbOpKind.Witness, String> program = selectFrom("users");

      Set<DbOp<?>> effects =
          SelectiveAnalyzer.collectPossibleEffects(program, DbOpHelper.DB_OP::narrow);

      assertThat(effects).hasSize(1);
      assertThat(effects).allSatisfy(op -> assertThat(op).isInstanceOf(DbOp.Select.class));
    }

    @Test
    @DisplayName("returns all effects for complex program")
    void returnsAllEffectsForComplexProgram() {
      FreeAp<DbOpKind.Witness, String> program =
          selectFrom("users")
              .map2(insertInto("logs", "entry"), (user, _) -> user)
              .map2(deleteFrom("temp"), (user, _) -> user);

      Set<DbOp<?>> effects =
          SelectiveAnalyzer.collectPossibleEffects(program, DbOpHelper.DB_OP::narrow);

      assertThat(effects).hasSize(3);

      // Verify we have one of each type
      long selectCount = effects.stream().filter(op -> op instanceof DbOp.Select).count();
      long insertCount = effects.stream().filter(op -> op instanceof DbOp.Insert).count();
      long deleteCount = effects.stream().filter(op -> op instanceof DbOp.Delete).count();

      assertThat(selectCount).isEqualTo(1);
      assertThat(insertCount).isEqualTo(1);
      assertThat(deleteCount).isEqualTo(1);
    }

    @Test
    @DisplayName("deduplicates identical effects")
    void deduplicatesIdenticalEffects() {
      // Same operation twice
      FreeAp<DbOpKind.Witness, String> program =
          selectFrom("users").map2(selectFrom("users"), (a, b) -> a + b);

      Set<DbOp<?>> effects =
          SelectiveAnalyzer.collectPossibleEffects(program, DbOpHelper.DB_OP::narrow);

      // Set should deduplicate, but since each Select is a new instance,
      // we need record equality. Records use structural equality.
      assertThat(effects).hasSize(1);
    }

    @Test
    @DisplayName("throws NullPointerException for null program")
    void throwsForNullProgram() {
      assertThatThrownBy(
              () ->
                  SelectiveAnalyzer.collectPossibleEffects(
                      null, (Kind<DbOpKind.Witness, ?> k) -> DbOpHelper.DB_OP.narrow(k)))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Program cannot be null");
    }

    @Test
    @DisplayName("throws NullPointerException for null narrow function")
    void throwsForNullNarrowFunction() {
      FreeAp<DbOpKind.Witness, String> program = selectFrom("users");

      assertThatThrownBy(() -> SelectiveAnalyzer.collectPossibleEffects(program, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Narrow function cannot be null");
    }
  }

  // ============================================================================
  // countMaximumEffects Tests
  // ============================================================================

  @Nested
  @DisplayName("countMaximumEffects()")
  class CountMaximumEffectsTests {

    @Test
    @DisplayName("returns 0 for pure programs")
    void returnsZeroForPure() {
      FreeAp<DbOpKind.Witness, String> program = FreeAp.pure("hello");

      int count = SelectiveAnalyzer.countMaximumEffects(program);

      assertThat(count).isZero();
    }

    @Test
    @DisplayName("returns correct count for complex program")
    void returnsCorrectCountForComplexProgram() {
      FreeAp<DbOpKind.Witness, String> program =
          selectFrom("users")
              .map2(selectFrom("posts"), (u, p) -> u + p)
              .map2(insertInto("logs", "data"), (up, _) -> up)
              .map2(deleteFrom("temp"), (ups, _) -> ups);

      int count = SelectiveAnalyzer.countMaximumEffects(program);

      assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("throws NullPointerException for null program")
    void throwsForNullProgram() {
      assertThatThrownBy(() -> SelectiveAnalyzer.countMaximumEffects(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Program cannot be null");
    }
  }

  // ============================================================================
  // computeEffectBounds Tests
  // ============================================================================

  @Nested
  @DisplayName("computeEffectBounds()")
  class ComputeEffectBoundsTests {

    @Test
    @DisplayName("returns tight bounds for pure FreeAp")
    void returnsTightBoundsForPureFreeAp() {
      FreeAp<DbOpKind.Witness, String> program =
          selectFrom("users").map2(selectFrom("posts"), (u, p) -> u + p);

      SelectiveAnalyzer.EffectBounds bounds = SelectiveAnalyzer.computeEffectBounds(program);

      assertThat(bounds.minimum()).isEqualTo(2);
      assertThat(bounds.maximum()).isEqualTo(2);
      assertThat(bounds.isTight()).isTrue();
      assertThat(bounds.range()).isZero();
    }

    @Test
    @DisplayName("returns zero bounds for pure programs")
    void returnsZeroBoundsForPure() {
      FreeAp<DbOpKind.Witness, String> program = FreeAp.pure("hello");

      SelectiveAnalyzer.EffectBounds bounds = SelectiveAnalyzer.computeEffectBounds(program);

      assertThat(bounds.minimum()).isZero();
      assertThat(bounds.maximum()).isZero();
      assertThat(bounds.isTight()).isTrue();
    }

    @Test
    @DisplayName("throws NullPointerException for null program")
    void throwsForNullProgram() {
      assertThatThrownBy(() -> SelectiveAnalyzer.computeEffectBounds(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Program cannot be null");
    }
  }

  // ============================================================================
  // EffectBounds Record Tests
  // ============================================================================

  @Nested
  @DisplayName("EffectBounds record")
  class EffectBoundsTests {

    @Test
    @DisplayName("rejects negative minimum")
    void rejectsNegativeMinimum() {
      assertThatThrownBy(() -> new SelectiveAnalyzer.EffectBounds(-1, 5))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Minimum cannot be negative");
    }

    @Test
    @DisplayName("rejects maximum less than minimum")
    void rejectsMaximumLessThanMinimum() {
      assertThatThrownBy(() -> new SelectiveAnalyzer.EffectBounds(5, 3))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Maximum cannot be less than minimum");
    }

    @Test
    @DisplayName("isTight returns true when min equals max")
    void isTightReturnsTrueWhenEqual() {
      SelectiveAnalyzer.EffectBounds bounds = new SelectiveAnalyzer.EffectBounds(5, 5);

      assertThat(bounds.isTight()).isTrue();
    }

    @Test
    @DisplayName("isTight returns false when min differs from max")
    void isTightReturnsFalseWhenDifferent() {
      SelectiveAnalyzer.EffectBounds bounds = new SelectiveAnalyzer.EffectBounds(2, 5);

      assertThat(bounds.isTight()).isFalse();
    }

    @Test
    @DisplayName("range returns correct difference")
    void rangeReturnsCorrectDifference() {
      SelectiveAnalyzer.EffectBounds bounds = new SelectiveAnalyzer.EffectBounds(2, 7);

      assertThat(bounds.range()).isEqualTo(5);
    }
  }

  // ============================================================================
  // partitionEffects Tests
  // ============================================================================

  @Nested
  @DisplayName("partitionEffects()")
  class PartitionEffectsTests {

    @Test
    @DisplayName("returns all effects as guaranteed for pure FreeAp")
    void returnsAllGuaranteedForPureFreeAp() {
      FreeAp<DbOpKind.Witness, String> program =
          selectFrom("users").map2(insertInto("logs", "data"), (u, _) -> u);

      SelectiveAnalyzer.EffectPartition<DbOp<?>> partition =
          SelectiveAnalyzer.partitionEffects(program, DbOpHelper.DB_OP::narrow);

      assertThat(partition.guaranteed()).hasSize(2);
      assertThat(partition.conditional()).isEmpty();
      assertThat(partition.allGuaranteed()).isTrue();
    }

    @Test
    @DisplayName("allPossible returns union of guaranteed and conditional")
    void allPossibleReturnsUnion() {
      FreeAp<DbOpKind.Witness, String> program =
          selectFrom("users").map2(selectFrom("posts"), (u, p) -> u + p);

      SelectiveAnalyzer.EffectPartition<DbOp<?>> partition =
          SelectiveAnalyzer.partitionEffects(program, DbOpHelper.DB_OP::narrow);

      assertThat(partition.allPossible()).hasSize(2);
    }

    @Test
    @DisplayName("throws NullPointerException for null program")
    void throwsForNullProgram() {
      assertThatThrownBy(
              () ->
                  SelectiveAnalyzer.partitionEffects(
                      null, (Kind<DbOpKind.Witness, ?> k) -> DbOpHelper.DB_OP.narrow(k)))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Program cannot be null");
    }

    @Test
    @DisplayName("throws NullPointerException for null narrow function")
    void throwsForNullNarrowFunction() {
      FreeAp<DbOpKind.Witness, String> program = selectFrom("users");

      assertThatThrownBy(() -> SelectiveAnalyzer.partitionEffects(program, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Narrow function cannot be null");
    }
  }

  // ============================================================================
  // containsDangerousEffect Tests
  // ============================================================================

  @Nested
  @DisplayName("containsDangerousEffect()")
  class ContainsDangerousEffectTests {

    @Test
    @DisplayName("returns true when dangerous effect present")
    void returnsTrueWhenDangerousPresent() {
      FreeAp<DbOpKind.Witness, String> program =
          selectFrom("users").map2(deleteFrom("users"), (u, _) -> u);

      boolean hasDangerous =
          SelectiveAnalyzer.containsDangerousEffect(
              program, DbOpHelper.DB_OP::narrow, op -> DbOp.Delete.class.isInstance(op));

      assertThat(hasDangerous).isTrue();
    }

    @Test
    @DisplayName("returns false when no dangerous effect present")
    void returnsFalseWhenNoDangerousPresent() {
      FreeAp<DbOpKind.Witness, String> program =
          selectFrom("users").map2(selectFrom("posts"), (u, p) -> u + p);

      boolean hasDangerous =
          SelectiveAnalyzer.containsDangerousEffect(
              program, DbOpHelper.DB_OP::narrow, op -> DbOp.Delete.class.isInstance(op));

      assertThat(hasDangerous).isFalse();
    }

    @Test
    @DisplayName("returns false for pure programs")
    void returnsFalseForPure() {
      FreeAp<DbOpKind.Witness, String> program = FreeAp.pure("hello");

      boolean hasDangerous =
          SelectiveAnalyzer.containsDangerousEffect(
              program, DbOpHelper.DB_OP::narrow, op -> DbOp.Delete.class.isInstance(op));

      assertThat(hasDangerous).isFalse();
    }

    @Test
    @DisplayName("throws NullPointerException for null program")
    void throwsForNullProgram() {
      assertThatThrownBy(
              () ->
                  SelectiveAnalyzer.containsDangerousEffect(
                      null,
                      (Kind<DbOpKind.Witness, ?> k) -> DbOpHelper.DB_OP.narrow(k),
                      op -> true))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Program cannot be null");
    }

    @Test
    @DisplayName("throws NullPointerException for null narrow function")
    void throwsForNullNarrowFunction() {
      FreeAp<DbOpKind.Witness, String> program = selectFrom("users");

      assertThatThrownBy(() -> SelectiveAnalyzer.containsDangerousEffect(program, null, op -> true))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Narrow function cannot be null");
    }

    @Test
    @DisplayName("throws NullPointerException for null predicate")
    void throwsForNullPredicate() {
      FreeAp<DbOpKind.Witness, String> program = selectFrom("users");

      assertThatThrownBy(
              () ->
                  SelectiveAnalyzer.containsDangerousEffect(
                      program, DbOpHelper.DB_OP::narrow, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Predicate cannot be null");
    }
  }

  // ============================================================================
  // Real-World Scenario Tests
  // ============================================================================

  @Nested
  @DisplayName("Real-World Scenarios")
  class RealWorldScenarioTests {

    @Test
    @DisplayName("security check: detects delete operations before execution")
    void securityCheckDetectsDeleteOperations() {
      // User submits a query that includes a delete
      FreeAp<DbOpKind.Witness, String> userQuery =
          selectFrom("users")
              .map2(selectFrom("orders"), (u, o) -> u + o)
              .map2(deleteFrom("users"), (uo, _) -> uo); // Malicious delete!

      // Security check before execution
      boolean containsDelete =
          SelectiveAnalyzer.containsDangerousEffect(
              userQuery, DbOpHelper.DB_OP::narrow, op -> DbOp.Delete.class.isInstance(op));

      assertThat(containsDelete).isTrue();
      // In real code: if (containsDelete) throw new SecurityException("Delete not allowed");
    }

    @Test
    @DisplayName("audit: collects all table accesses")
    void auditCollectsAllTableAccesses() {
      FreeAp<DbOpKind.Witness, String> program =
          selectFrom("users")
              .map2(selectFrom("orders"), (u, o) -> u + o)
              .map2(insertInto("audit_log", "access"), (uo, _) -> uo);

      Set<DbOp<?>> operations =
          SelectiveAnalyzer.collectPossibleEffects(program, DbOpHelper.DB_OP::narrow);

      // Extract table names for audit
      Set<String> tablesAccessed =
          operations.stream()
              .map(
                  op ->
                      switch (op) {
                        case DbOp.Select s -> s.table();
                        case DbOp.Insert i -> i.table();
                        case DbOp.Update u -> u.table();
                        case DbOp.Delete d -> d.table();
                      })
              .collect(java.util.stream.Collectors.toSet());

      assertThat(tablesAccessed).containsExactlyInAnyOrder("users", "orders", "audit_log");
    }

    @Test
    @DisplayName("cost estimation: counts write operations")
    void costEstimationCountsWrites() {
      FreeAp<DbOpKind.Witness, String> program =
          selectFrom("users")
              .map2(insertInto("orders", "new order"), (u, _) -> u)
              .map2(update("users", "last_order"), (u, _) -> u)
              .map2(insertInto("logs", "order created"), (u, _) -> u);

      Set<DbOp<?>> operations =
          SelectiveAnalyzer.collectPossibleEffects(program, DbOpHelper.DB_OP::narrow);

      long writeOperations =
          operations.stream()
              .filter(op -> op instanceof DbOp.Insert || op instanceof DbOp.Update)
              .count();

      // Cost model: reads are cheap, writes are expensive
      int estimatedCost = (int) (operations.size() + writeOperations * 10);

      assertThat(writeOperations).isEqualTo(3); // 2 inserts + 1 update
      assertThat(estimatedCost).isEqualTo(4 + 30); // 4 total ops + 3 writes * 10
    }
  }
}
