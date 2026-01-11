// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

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
import org.higherkindedj.hkt.free_ap.FreeApAnalyzer;
import org.higherkindedj.hkt.free_ap.FreeApApplicative;
import org.higherkindedj.hkt.free_ap.FreeApKindHelper;
import org.higherkindedj.hkt.free_ap.SelectiveAnalyzer;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 11: Static Analysis of Free Applicative Programs
 *
 * <p>This file contains the complete solutions for all exercises.
 */
public class Tutorial11_StaticAnalysis_Solution {

  // ============================================================================
  // Test DSL: Data Operations (same as tutorial)
  // ============================================================================

  sealed interface DataOp<A> {
    record Fetch(String key) implements DataOp<String> {}

    record Store(String key, String value) implements DataOp<Integer> {}

    record Remove(String key) implements DataOp<Boolean> {}
  }

  interface DataOpKind<A> extends Kind<DataOpKind.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
      private Witness() {}
    }
  }

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
  // Exercise 1 Solution
  // ============================================================================

  @Test
  void exercise1_countOperations() {
    FreeAp<DataOpKind.Witness, String> program =
        fetch("user:123").map2(fetch("user:456"), (a, b) -> a + b);

    // SOLUTION: Use FreeApAnalyzer.countOperations
    int count = FreeApAnalyzer.countOperations(program);

    assertThat(count).isEqualTo(2);
  }

  // ============================================================================
  // Exercise 2 Solution
  // ============================================================================

  @Test
  void exercise2_collectOperations() {
    FreeAp<DataOpKind.Witness, String> program =
        fetch("config")
            .map2(store("cache", "data"), (config, _) -> config)
            .map2(fetch("settings"), (config, settings) -> config + settings);

    // SOLUTION: Use FreeApAnalyzer.collectOperations
    List<Kind<DataOpKind.Witness, ?>> operations = FreeApAnalyzer.collectOperations(program);

    assertThat(operations).hasSize(3);

    List<String> opNames =
        operations.stream()
            .map(DataOpHelper.DATA_OP::narrow)
            .map(op -> op.getClass().getSimpleName())
            .toList();

    assertThat(opNames).containsExactlyInAnyOrder("Fetch", "Store", "Fetch");
  }

  // ============================================================================
  // Exercise 3 Solution
  // ============================================================================

  @Test
  void exercise3_checkDangerousOperations() {
    FreeAp<DataOpKind.Witness, String> safeProgram = fetch("key1").map2(fetch("key2"), (a, b) -> a);

    FreeAp<DataOpKind.Witness, String> dangerousProgram =
        fetch("key1").map2(remove("key1"), (value, deleted) -> deleted ? "removed" : value);

    // SOLUTION: Use FreeApAnalyzer.containsOperation with a predicate
    boolean safeHasRemove =
        FreeApAnalyzer.containsOperation(
            safeProgram, op -> DataOp.Remove.class.isInstance(DataOpHelper.DATA_OP.narrow(op)));

    boolean dangerousHasRemove =
        FreeApAnalyzer.containsOperation(
            dangerousProgram,
            op -> DataOp.Remove.class.isInstance(DataOpHelper.DATA_OP.narrow(op)));

    assertThat(safeHasRemove).isFalse();
    assertThat(dangerousHasRemove).isTrue();
  }

  // ============================================================================
  // Exercise 4 Solution
  // ============================================================================

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

    // SOLUTION: Use FreeApAnalyzer.groupByType with the narrow function
    Map<Class<?>, Integer> groups =
        FreeApAnalyzer.groupByType(program, DataOpHelper.DATA_OP::narrow);

    assertThat(groups.get(DataOp.Fetch.class)).isEqualTo(3);
    assertThat(groups.get(DataOp.Store.class)).isEqualTo(1);
  }

  // ============================================================================
  // Exercise 5 Solution
  // ============================================================================

  @Test
  void exercise5_customAnalysis() {
    FreeAp<DataOpKind.Witness, String> program =
        fetch("user:123")
            .map2(store("cache:user:123", "data"), (u, _) -> u)
            .map2(fetch("settings:global"), (u, s) -> u + s);

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

    // SOLUTION: Use FreeApAnalyzer.analyseWith
    Set<String> keys = FreeApAnalyzer.analyseWith(program, extractKeys, setMonoid);

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
  // Exercise 6 Solution
  // ============================================================================

  @Test
  void exercise6_effectBounds() {
    FreeAp<DataOpKind.Witness, String> program =
        fetch("key1").map2(fetch("key2"), (a, b) -> a + b).map2(fetch("key3"), (ab, c) -> ab + c);

    // SOLUTION: Use SelectiveAnalyzer.computeEffectBounds
    SelectiveAnalyzer.EffectBounds bounds = SelectiveAnalyzer.computeEffectBounds(program);

    assertThat(bounds.minimum()).isEqualTo(3);
    assertThat(bounds.maximum()).isEqualTo(3);
    assertThat(bounds.isTight()).isTrue();
  }

  // ============================================================================
  // Exercise 7 Solution
  // ============================================================================

  @Test
  void exercise7_collectPossibleEffects() {
    FreeAp<DataOpKind.Witness, String> program =
        fetch("config").map2(fetch("config"), (a, b) -> a + b);

    // SOLUTION: Use SelectiveAnalyzer.collectPossibleEffects
    Set<DataOp<?>> effects =
        SelectiveAnalyzer.collectPossibleEffects(program, DataOpHelper.DATA_OP::narrow);

    assertThat(effects).hasSize(1);
    assertThat(effects.iterator().next()).isEqualTo(new DataOp.Fetch("config"));
  }

  // ============================================================================
  // Exercise 8 Solution
  // ============================================================================

  @Test
  void exercise8_containsDangerousEffect() {
    FreeAp<DataOpKind.Witness, String> program =
        fetch("user")
            .map2(store("backup", "user_data"), (u, _) -> u)
            .map2(remove("user"), (u, deleted) -> deleted ? "cleaned" : u);

    // SOLUTION: Use SelectiveAnalyzer.containsDangerousEffect
    boolean hasDangerous =
        SelectiveAnalyzer.containsDangerousEffect(
            program, DataOpHelper.DATA_OP::narrow, op -> DataOp.Remove.class.isInstance(op));

    assertThat(hasDangerous).isTrue();
  }

  // ============================================================================
  // Exercise 9 Solution
  // ============================================================================

  @Test
  void exercise9_permissionCheck() {
    FreeAp<DataOpKind.Witness, String> program =
        fetch("data").map2(store("cache", "data"), (data, _) -> data);

    Set<Class<?>> readOnlyPermissions = Set.of(DataOp.Fetch.class);
    Set<Class<?>> readWritePermissions = Set.of(DataOp.Fetch.class, DataOp.Store.class);

    // SOLUTION: Collect operations, check their classes against permissions
    List<Kind<DataOpKind.Witness, ?>> operations = FreeApAnalyzer.collectOperations(program);

    Set<Class<?>> requiredClasses =
        operations.stream()
            .map(DataOpHelper.DATA_OP::narrow)
            .map(Object::getClass)
            .collect(java.util.stream.Collectors.toSet());

    boolean readOnlyCanRun = readOnlyPermissions.containsAll(requiredClasses);
    boolean readWriteCanRun = readWritePermissions.containsAll(requiredClasses);

    assertThat(readOnlyCanRun).isFalse();
    assertThat(readWriteCanRun).isTrue();
  }

  // ============================================================================
  // Exercise 10 Solution
  // ============================================================================

  @Test
  void exercise10_batchingAnalysis() {
    FreeApApplicative<DataOpKind.Witness> app = FreeApApplicative.instance();

    FreeAp<DataOpKind.Witness, String> dashboard =
        FreeApKindHelper.FREE_AP.narrow(
            app.map4(
                FreeApKindHelper.FREE_AP.widen(fetch("user:1")),
                FreeApKindHelper.FREE_AP.widen(fetch("user:2")),
                FreeApKindHelper.FREE_AP.widen(fetch("user:3")),
                FreeApKindHelper.FREE_AP.widen(fetch("user:4")),
                (u1, u2, u3, u4) -> String.join(", ", u1, u2, u3, u4)));

    // SOLUTION: Group by type and count Fetch operations
    Map<Class<?>, Integer> groups =
        FreeApAnalyzer.groupByType(dashboard, DataOpHelper.DATA_OP::narrow);

    int batchableFetches = groups.getOrDefault(DataOp.Fetch.class, 0);

    assertThat(batchableFetches).isEqualTo(4);

    System.out.println("Potential optimisation: " + batchableFetches + " fetches could be batched");
  }
}
