// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
 * Solution for Tutorial11 StaticAnalysis — teaching-solution format.
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

  /**
   * Why this is idiomatic: {@code FreeApAnalyzer.countOperations} walks the program's AST and
   * tallies the leaves without ever interpreting them. The free applicative's value-as-data shape
   * is exactly what makes static counting possible.
   *
   * <p>Alternative: thread a counter through a custom interpreter that ignores results. Same
   * answer; more code, no clearer signal.
   *
   * <p>Common wrong attempt: run the program against a real backend and count requests. That works,
   * but it conflates analysis with execution — the whole point of free applicatives is that you can
   * answer the question without paying the I/O cost.
   */
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

  /**
   * Why this is idiomatic: {@code collectOperations} returns the leaves themselves, preserving
   * order and shape. From there ordinary {@code Stream} operations classify, narrow, or summarise —
   * no further DSL needed.
   *
   * <p>Alternative: write a {@code Natural} that emits a list per leaf and {@code foldMap} with the
   * list monoid. Equivalent; the canned helper is shorter and signals the intent clearly.
   *
   * <p>Common wrong attempt: try to {@code map} the {@code FreeAp} to extract operations. The AST's
   * {@code map} composes with the result type, not the leaves; reach for an analyser, not the
   * applicative's own combinators.
   */
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

  /**
   * Why this is idiomatic: {@code containsOperation(program, predicate)} short-circuits as soon as
   * the predicate matches — exactly what a "does this program ever do X?" check needs.
   *
   * <p>Alternative: {@code collectOperations(...).stream().anyMatch(...)}. Same outcome; the named
   * helper avoids materialising the full list when the answer is found early.
   *
   * <p>Common wrong attempt: rely on a runtime guard inside the interpreter to refuse dangerous
   * operations. That trips at execution time, after side effects have started; the static check
   * fails at the door, before any I/O.
   */
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

  /**
   * Why this is idiomatic: {@code groupByType} produces a histogram over the leaf operations keyed
   * by class. Pass the {@code narrow} method reference and the analyser knows how to peer inside
   * each {@code Kind}.
   *
   * <p>Alternative: collect-then-group-then-count by hand with {@code Collectors.groupingBy}. The
   * named helper is one call; the manual chain is three and gives no extra control.
   *
   * <p>Common wrong attempt: feed the raw {@code Kind}s into {@code groupingBy} without narrowing.
   * Every key collapses to {@code Holder.class} and the histogram has a single bucket — useless for
   * telling fetches from stores.
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

    // SOLUTION: Use FreeApAnalyzer.groupByType with the narrow function
    Map<Class<?>, Integer> groups =
        FreeApAnalyzer.groupByType(program, DataOpHelper.DATA_OP::narrow);

    assertThat(groups.get(DataOp.Fetch.class)).isEqualTo(3);
    assertThat(groups.get(DataOp.Store.class)).isEqualTo(1);
  }

  // ============================================================================
  // Exercise 5 Solution
  // ============================================================================

  /**
   * Why this is idiomatic: {@code analyseWith(program, transform, monoid)} is the general
   * static-analysis primitive — every other helper specialises this one. The natural transformation
   * says what each leaf contributes, the {@code Monoid} says how to combine contributions, and the
   * analyser walks the AST.
   *
   * <p>Alternative: a bespoke recursive walker over the {@code FreeAp} cases. Doable but fragile;
   * {@code analyseWith} already handles every constructor and is exercised by the library's tests.
   *
   * <p>Common wrong attempt: choose the wrong {@code Monoid} for the question — e.g. the
   * string-concatenation monoid where set-union was wanted. The walker still completes, but the
   * answer accumulates duplicates instead of de-duplicating; pick the monoid that matches the
   * intent.
   */
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

  /**
   * Why this is idiomatic: {@code SelectiveAnalyzer.computeEffectBounds} reports the minimum and
   * maximum number of effects a program might trigger — handy for capacity planning before the
   * program ever executes. A "tight" bound means the count is fixed.
   *
   * <p>Alternative: count operations exactly via {@code FreeApAnalyzer.countOperations} when the
   * program has no conditional structure. {@code SelectiveAnalyzer} earns its keep once conditional
   * effects appear; for purely applicative programs the two agree.
   *
   * <p>Common wrong attempt: assume {@code maximum() == minimum()} always. For programs that
   * include conditionals (selective applicatives), the bounds may differ; assert {@code isTight()}
   * when the contract genuinely requires a fixed count.
   */
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

  /**
   * Why this is idiomatic: {@code collectPossibleEffects} returns a {@code Set} keyed by the
   * narrowed operation, so two {@code fetch("config")} leaves de-duplicate to a single entry. The
   * result describes the program's <em>distinct</em> footprint.
   *
   * <p>Alternative: {@code new HashSet<>(collectOperations(...))} after narrowing. Equivalent in
   * this case; the named helper signals "I want the distinct effect set" clearly.
   *
   * <p>Common wrong attempt: equate distinct effects with distinct keys ({@code Set<String>}). Two
   * operations with the same key but different types ({@code Fetch("k")} vs {@code Remove("k")})
   * would silently collapse — keep the operation as the set element.
   */
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

  /**
   * Why this is idiomatic: {@code containsDangerousEffect} pairs a narrow function with a predicate
   * over the narrowed operation. The intent reads top-to-bottom: "look at every leaf, narrow it,
   * ask the question".
   *
   * <p>Alternative: {@code containsOperation} with the narrow folded into the predicate (as in
   * Exercise 3). Same answer; {@code containsDangerousEffect} is the selective-applicative variant
   * that handles conditional branches as well.
   *
   * <p>Common wrong attempt: implement the check with a runtime audit log inside the interpreter.
   * The audit reports after the fact — the static check refuses the program before any interpreter
   * runs.
   */
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

  /**
   * Why this is idiomatic: collect the distinct operation classes and ask the permission set {@code
   * containsAll}. The check is a one-liner, the permission set is data, and a new permission tier
   * is a {@code Set.of(...)} away.
   *
   * <p>Alternative: walk the program with a custom {@code Natural} that yields a class set per leaf
   * and {@code analyseWith} a set monoid. Equivalent; the {@code stream} pipeline shown here is
   * shorter for a one-off check.
   *
   * <p>Common wrong attempt: try to enforce permissions by aborting the interpreter mid-run on a
   * forbidden operation. By then the earlier operations have already executed; the static check
   * refuses the entire program before anything runs.
   */
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
            .collect(Collectors.toSet());

    boolean readOnlyCanRun = readOnlyPermissions.containsAll(requiredClasses);
    boolean readWriteCanRun = readWritePermissions.containsAll(requiredClasses);

    assertThat(readOnlyCanRun).isFalse();
    assertThat(readWriteCanRun).isTrue();
  }

  // ============================================================================
  // Exercise 10 Solution
  // ============================================================================

  /**
   * Why this is idiomatic: counting {@code Fetch} operations across the AST tells the interpreter
   * how many round-trips a naive run would make — and therefore how big the batching win could be.
   * The analysis happens before any I/O.
   *
   * <p>Alternative: instrument a real interpreter and observe the request count. Useful as a sanity
   * check; static analysis is what lets the optimisation choice be made <em>before</em> the
   * interpreter exists.
   *
   * <p>Common wrong attempt: assume every {@code Fetch} is batchable. Some backends only batch
   * fetches that share a prefix or a tenant; refine the predicate to count only the genuinely
   * batchable ones rather than the gross total.
   */
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
