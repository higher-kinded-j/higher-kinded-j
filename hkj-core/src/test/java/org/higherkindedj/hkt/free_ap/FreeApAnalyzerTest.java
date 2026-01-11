// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free_ap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.constant.ConstKind;
import org.higherkindedj.hkt.constant.ConstKindHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link FreeApAnalyzer}.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Operation counting
 *   <li>Operation collection
 *   <li>Operation predicate checking
 *   <li>Grouping by type
 *   <li>Custom analysis with Const functor
 *   <li>Null argument handling
 * </ul>
 */
@DisplayName("FreeApAnalyzer Tests")
class FreeApAnalyzerTest {

  // ============================================================================
  // Test DSL: Simple operations for testing
  // ============================================================================

  /** Simple test operation sealed interface. */
  sealed interface TestOp<A> {
    record Read(String key) implements TestOp<String> {}

    record Write(String key, String value) implements TestOp<Void> {}

    record Delete(String key) implements TestOp<Boolean> {}
  }

  /** HKT bridge for TestOp. */
  interface TestOpKind<A> extends Kind<TestOpKind.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
      private Witness() {}
    }
  }

  /** Helper for TestOp HKT conversions. */
  enum TestOpHelper {
    TEST_OP;

    record Holder<A>(TestOp<A> op) implements TestOpKind<A> {}

    public <A> Kind<TestOpKind.Witness, A> widen(TestOp<A> op) {
      return new Holder<>(op);
    }

    @SuppressWarnings("unchecked")
    public <A> TestOp<A> narrow(Kind<TestOpKind.Witness, ?> kind) {
      return (TestOp<A>) ((Holder<?>) kind).op();
    }
  }

  // Smart constructors
  private static FreeAp<TestOpKind.Witness, String> read(String key) {
    return FreeAp.lift(TestOpHelper.TEST_OP.widen(new TestOp.Read(key)));
  }

  private static FreeAp<TestOpKind.Witness, Void> write(String key, String value) {
    return FreeAp.lift(TestOpHelper.TEST_OP.widen(new TestOp.Write(key, value)));
  }

  private static FreeAp<TestOpKind.Witness, Boolean> delete(String key) {
    return FreeAp.lift(TestOpHelper.TEST_OP.widen(new TestOp.Delete(key)));
  }

  // ============================================================================
  // countOperations Tests
  // ============================================================================

  @Nested
  @DisplayName("countOperations()")
  class CountOperationsTests {

    @Test
    @DisplayName("returns 0 for pure programs")
    void returnsZeroForPure() {
      FreeAp<TestOpKind.Witness, String> program = FreeAp.pure("hello");

      int count = FreeApAnalyzer.countOperations(program);

      assertThat(count).isZero();
    }

    @Test
    @DisplayName("returns 1 for single lift")
    void returnsOneForSingleLift() {
      FreeAp<TestOpKind.Witness, String> program = read("key1");

      int count = FreeApAnalyzer.countOperations(program);

      assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("returns correct count for map2 combination")
    void returnsCorrectCountForMap2() {
      FreeAp<TestOpKind.Witness, String> program = read("key1").map2(read("key2"), (a, b) -> a + b);

      int count = FreeApAnalyzer.countOperations(program);

      assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("returns correct count for complex program")
    void returnsCorrectCountForComplexProgram() {
      FreeApApplicative<TestOpKind.Witness> applicative = FreeApApplicative.instance();

      FreeAp<TestOpKind.Witness, String> program =
          FreeApKindHelper.FREE_AP.narrow(
              applicative.map4(
                  FreeApKindHelper.FREE_AP.widen(read("key1")),
                  FreeApKindHelper.FREE_AP.widen(read("key2")),
                  FreeApKindHelper.FREE_AP.widen(read("key3")),
                  FreeApKindHelper.FREE_AP.widen(read("key4")),
                  (a, b, c, d) -> a + b + c + d));

      int count = FreeApAnalyzer.countOperations(program);

      assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("counts operations in nested ap structure")
    void countsOperationsInNestedApStructure() {
      // Build: pure(f).ap(read).ap(read).ap(write)
      FreeAp<TestOpKind.Witness, String> program =
          read("key1")
              .map2(read("key2"), (a, b) -> a + b)
              .map2(write("key3", "value"), (ab, _) -> ab);

      int count = FreeApAnalyzer.countOperations(program);

      assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("throws NullPointerException for null program")
    void throwsForNullProgram() {
      assertThatThrownBy(() -> FreeApAnalyzer.countOperations(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Program cannot be null");
    }
  }

  // ============================================================================
  // collectOperations Tests
  // ============================================================================

  @Nested
  @DisplayName("collectOperations()")
  class CollectOperationsTests {

    @Test
    @DisplayName("returns empty list for pure programs")
    void returnsEmptyListForPure() {
      FreeAp<TestOpKind.Witness, String> program = FreeAp.pure("hello");

      List<Kind<TestOpKind.Witness, ?>> operations = FreeApAnalyzer.collectOperations(program);

      assertThat(operations).isEmpty();
    }

    @Test
    @DisplayName("returns single operation for single lift")
    void returnsSingleOperationForSingleLift() {
      FreeAp<TestOpKind.Witness, String> program = read("key1");

      List<Kind<TestOpKind.Witness, ?>> operations = FreeApAnalyzer.collectOperations(program);

      assertThat(operations).hasSize(1);
      TestOp<?> op = TestOpHelper.TEST_OP.narrow(operations.get(0));
      assertThat(op).isInstanceOf(TestOp.Read.class);
      assertThat(((TestOp.Read) op).key()).isEqualTo("key1");
    }

    @Test
    @DisplayName("returns all operations for complex program")
    void returnsAllOperationsForComplexProgram() {
      FreeAp<TestOpKind.Witness, String> program =
          read("key1").map2(write("key2", "value"), (a, _) -> a).map2(delete("key3"), (a, _) -> a);

      List<Kind<TestOpKind.Witness, ?>> operations = FreeApAnalyzer.collectOperations(program);

      assertThat(operations).hasSize(3);

      // Extract and verify each operation
      List<Class<?>> opClasses =
          operations.stream().map(TestOpHelper.TEST_OP::narrow).map(Object::getClass).toList();

      assertThat(opClasses)
          .containsExactlyInAnyOrder(TestOp.Read.class, TestOp.Write.class, TestOp.Delete.class);
    }

    @Test
    @DisplayName("throws NullPointerException for null program")
    void throwsForNullProgram() {
      assertThatThrownBy(() -> FreeApAnalyzer.collectOperations(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Program cannot be null");
    }
  }

  // ============================================================================
  // containsOperation Tests
  // ============================================================================

  @Nested
  @DisplayName("containsOperation()")
  class ContainsOperationTests {

    @Test
    @DisplayName("returns false for pure programs")
    void returnsFalseForPure() {
      FreeAp<TestOpKind.Witness, String> program = FreeAp.pure("hello");

      boolean result = FreeApAnalyzer.containsOperation(program, _ -> true);

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("returns true when operation matches predicate")
    void returnsTrueWhenOperationMatches() {
      FreeAp<TestOpKind.Witness, Boolean> program = delete("key1");

      boolean result =
          FreeApAnalyzer.containsOperation(
              program, op -> TestOp.Delete.class.isInstance(TestOpHelper.TEST_OP.narrow(op)));

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("returns false when no operation matches predicate")
    void returnsFalseWhenNoOperationMatches() {
      FreeAp<TestOpKind.Witness, String> program = read("key1").map2(read("key2"), (a, b) -> a + b);

      boolean result =
          FreeApAnalyzer.containsOperation(
              program, op -> TestOp.Delete.class.isInstance(TestOpHelper.TEST_OP.narrow(op)));

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("finds operation in complex program")
    void findsOperationInComplexProgram() {
      FreeAp<TestOpKind.Witness, String> program =
          read("key1").map2(write("key2", "value"), (a, _) -> a).map2(delete("key3"), (a, _) -> a);

      boolean hasDelete =
          FreeApAnalyzer.containsOperation(
              program, op -> TestOp.Delete.class.isInstance(TestOpHelper.TEST_OP.narrow(op)));
      boolean hasRead =
          FreeApAnalyzer.containsOperation(
              program, op -> TestOp.Read.class.isInstance(TestOpHelper.TEST_OP.narrow(op)));
      boolean hasWrite =
          FreeApAnalyzer.containsOperation(
              program, op -> TestOp.Write.class.isInstance(TestOpHelper.TEST_OP.narrow(op)));

      assertThat(hasDelete).isTrue();
      assertThat(hasRead).isTrue();
      assertThat(hasWrite).isTrue();
    }

    @Test
    @DisplayName("throws NullPointerException for null program")
    void throwsForNullProgram() {
      assertThatThrownBy(() -> FreeApAnalyzer.containsOperation(null, _ -> true))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Program cannot be null");
    }

    @Test
    @DisplayName("throws NullPointerException for null predicate")
    void throwsForNullPredicate() {
      FreeAp<TestOpKind.Witness, String> program = read("key1");

      assertThatThrownBy(() -> FreeApAnalyzer.containsOperation(program, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Predicate cannot be null");
    }
  }

  // ============================================================================
  // groupByType Tests
  // ============================================================================

  @Nested
  @DisplayName("groupByType()")
  class GroupByTypeTests {

    @Test
    @DisplayName("returns empty map for pure programs")
    void returnsEmptyMapForPure() {
      FreeAp<TestOpKind.Witness, String> program = FreeAp.pure("hello");

      Map<Class<?>, Integer> groups =
          FreeApAnalyzer.groupByType(program, TestOpHelper.TEST_OP::narrow);

      assertThat(groups).isEmpty();
    }

    @Test
    @DisplayName("groups single operation correctly")
    void groupsSingleOperationCorrectly() {
      FreeAp<TestOpKind.Witness, String> program = read("key1");

      Map<Class<?>, Integer> groups =
          FreeApAnalyzer.groupByType(program, TestOpHelper.TEST_OP::narrow);

      assertThat(groups).hasSize(1);
      assertThat(groups.get(TestOp.Read.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("groups multiple operations of same type")
    void groupsMultipleOperationsOfSameType() {
      FreeAp<TestOpKind.Witness, String> program =
          read("key1").map2(read("key2"), (a, b) -> a + b).map2(read("key3"), (ab, c) -> ab + c);

      Map<Class<?>, Integer> groups =
          FreeApAnalyzer.groupByType(program, TestOpHelper.TEST_OP::narrow);

      assertThat(groups).hasSize(1);
      assertThat(groups.get(TestOp.Read.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("groups different operation types correctly")
    void groupsDifferentOperationTypesCorrectly() {
      FreeAp<TestOpKind.Witness, String> program =
          read("key1")
              .map2(read("key2"), (a, b) -> a + b)
              .map2(write("key3", "value"), (ab, _) -> ab)
              .map2(delete("key4"), (abc, _) -> abc);

      Map<Class<?>, Integer> groups =
          FreeApAnalyzer.groupByType(program, TestOpHelper.TEST_OP::narrow);

      assertThat(groups).hasSize(3);
      assertThat(groups.get(TestOp.Read.class)).isEqualTo(2);
      assertThat(groups.get(TestOp.Write.class)).isEqualTo(1);
      assertThat(groups.get(TestOp.Delete.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("throws NullPointerException for null program")
    void throwsForNullProgram() {
      assertThatThrownBy(
              () ->
                  FreeApAnalyzer.groupByType(
                      null, (Kind<TestOpKind.Witness, ?> k) -> TestOpHelper.TEST_OP.narrow(k)))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Program cannot be null");
    }

    @Test
    @DisplayName("throws NullPointerException for null narrow function")
    void throwsForNullNarrowFunction() {
      FreeAp<TestOpKind.Witness, String> program = read("key1");

      assertThatThrownBy(() -> FreeApAnalyzer.groupByType(program, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Narrow function cannot be null");
    }
  }

  // ============================================================================
  // analyseWith Tests
  // ============================================================================

  @Nested
  @DisplayName("analyseWith()")
  class AnalyseWithTests {

    // Helper to create a Natural transformation that maps each op to 1
    private Natural<TestOpKind.Witness, ConstKind.Witness<Integer>> countingNatural() {
      return new Natural<>() {
        @Override
        public <A> Kind<ConstKind.Witness<Integer>, A> apply(Kind<TestOpKind.Witness, A> fa) {
          return ConstKindHelper.CONST.widen(new Const<>(1));
        }
      };
    }

    @Test
    @DisplayName("returns monoid empty for pure programs")
    void returnsMonoidEmptyForPure() {
      FreeAp<TestOpKind.Witness, String> program = FreeAp.pure("hello");
      Monoid<Integer> intAddition = Monoids.integerAddition();

      Integer result = FreeApAnalyzer.analyseWith(program, countingNatural(), intAddition);

      assertThat(result).isEqualTo(0); // Monoid empty for addition
    }

    @Test
    @DisplayName("accumulates values using monoid")
    void accumulatesValuesUsingMonoid() {
      FreeAp<TestOpKind.Witness, String> program =
          read("key1").map2(read("key2"), (a, b) -> a + b).map2(read("key3"), (ab, c) -> ab + c);

      Monoid<Integer> intAddition = Monoids.integerAddition();

      Integer result = FreeApAnalyzer.analyseWith(program, countingNatural(), intAddition);

      assertThat(result).isEqualTo(3);
    }

    @Test
    @DisplayName("collects operation names using list monoid")
    void collectsOperationNamesUsingListMonoid() {
      FreeAp<TestOpKind.Witness, String> program =
          read("key1").map2(write("key2", "value"), (a, _) -> a).map2(delete("key3"), (a, _) -> a);

      Monoid<List<String>> listMonoid = Monoids.list();
      Natural<TestOpKind.Witness, ConstKind.Witness<List<String>>> toNames =
          new Natural<>() {
            @Override
            public <A> Kind<ConstKind.Witness<List<String>>, A> apply(
                Kind<TestOpKind.Witness, A> fa) {
              TestOp<?> op = TestOpHelper.TEST_OP.narrow(fa);
              String name = op.getClass().getSimpleName();
              return ConstKindHelper.CONST.widen(new Const<>(List.of(name)));
            }
          };

      List<String> names = FreeApAnalyzer.analyseWith(program, toNames, listMonoid);

      assertThat(names).hasSize(3);
      assertThat(names).containsExactlyInAnyOrder("Read", "Write", "Delete");
    }

    @Test
    @DisplayName("throws NullPointerException for null program")
    void throwsForNullProgram() {
      Monoid<Integer> intAddition = Monoids.integerAddition();

      assertThatThrownBy(() -> FreeApAnalyzer.analyseWith(null, countingNatural(), intAddition))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Program cannot be null");
    }

    @Test
    @DisplayName("throws NullPointerException for null natural transformation")
    void throwsForNullNaturalTransformation() {
      FreeAp<TestOpKind.Witness, String> program = read("key1");
      Monoid<Integer> intAddition = Monoids.integerAddition();

      assertThatThrownBy(() -> FreeApAnalyzer.analyseWith(program, null, intAddition))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Natural transformation cannot be null");
    }

    @Test
    @DisplayName("throws NullPointerException for null monoid")
    void throwsForNullMonoid() {
      FreeAp<TestOpKind.Witness, String> program = read("key1");

      assertThatThrownBy(() -> FreeApAnalyzer.analyseWith(program, countingNatural(), null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Monoid cannot be null");
    }
  }

  // ============================================================================
  // Real-World Scenario Tests
  // ============================================================================

  @Nested
  @DisplayName("Real-World Scenarios")
  class RealWorldScenarioTests {

    @Test
    @DisplayName("permission checking: detects dangerous operations")
    void permissionCheckingDetectsDangerousOperations() {
      // Build a program that includes a delete operation
      FreeAp<TestOpKind.Witness, String> program =
          read("user:123")
              .map2(read("user:123:posts"), (user, posts) -> user + posts)
              .map2(delete("user:123"), (data, _) -> data);

      // Check for dangerous operations before execution
      boolean hasDangerousOps =
          FreeApAnalyzer.containsOperation(
              program, op -> TestOp.Delete.class.isInstance(TestOpHelper.TEST_OP.narrow(op)));

      assertThat(hasDangerousOps).isTrue();
    }

    @Test
    @DisplayName("batching analysis: groups similar operations")
    void batchingAnalysisGroupsSimilarOperations() {
      // Build a program with multiple read operations (could be batched)
      FreeApApplicative<TestOpKind.Witness> applicative = FreeApApplicative.instance();

      FreeAp<TestOpKind.Witness, String> program =
          FreeApKindHelper.FREE_AP.narrow(
              applicative.map4(
                  FreeApKindHelper.FREE_AP.widen(read("user:1")),
                  FreeApKindHelper.FREE_AP.widen(read("user:2")),
                  FreeApKindHelper.FREE_AP.widen(read("user:3")),
                  FreeApKindHelper.FREE_AP.widen(read("user:4")),
                  (a, b, c, d) -> a + b + c + d));

      // Analyse for potential batching
      Map<Class<?>, Integer> groups =
          FreeApAnalyzer.groupByType(program, TestOpHelper.TEST_OP::narrow);

      // All operations are reads - could be batched into single query
      assertThat(groups).hasSize(1);
      assertThat(groups.get(TestOp.Read.class)).isEqualTo(4);
    }

    @Test
    @DisplayName("cost estimation: counts expensive operations")
    void costEstimationCountsExpensiveOperations() {
      // Build a program
      FreeAp<TestOpKind.Witness, String> program =
          read("key1")
              .map2(read("key2"), (a, b) -> a + b)
              .map2(write("key3", "value"), (ab, _) -> ab)
              .map2(write("key4", "value2"), (abc, _) -> abc)
              .map2(delete("key5"), (abcd, _) -> abcd);

      // Count total operations
      int totalOps = FreeApAnalyzer.countOperations(program);

      // Count writes (potentially expensive)
      List<Kind<TestOpKind.Witness, ?>> allOps = FreeApAnalyzer.collectOperations(program);
      long writeCount =
          allOps.stream()
              .map(k -> TestOpHelper.TEST_OP.narrow(k))
              .filter(op -> TestOp.Write.class.isInstance(op))
              .count();

      assertThat(totalOps).isEqualTo(5);
      assertThat(writeCount).isEqualTo(2);
    }
  }
}
