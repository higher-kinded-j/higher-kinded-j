// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free_ap;

import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.constant.ConstApplicative;
import org.higherkindedj.hkt.constant.ConstKind;
import org.higherkindedj.hkt.constant.ConstKindHelper;
import org.jspecify.annotations.NullMarked;

/**
 * Static analysis utilities for Free Applicative programs.
 *
 * <p>This class provides methods to analyse {@link FreeAp} programs before execution. Because Free
 * Applicative captures independent computations with a visible structure, we can inspect programs
 * to count operations, check for dangerous effects, or gather information without running the
 * actual effects.
 *
 * <h2>Key Capabilities</h2>
 *
 * <ul>
 *   <li><b>Count operations</b>: Determine how many effects a program will execute
 *   <li><b>Collect operations</b>: Extract all operations for inspection
 *   <li><b>Check for operations</b>: Test if a program contains specific effects
 *   <li><b>Group by type</b>: Categorise operations by their class
 *   <li><b>Custom analysis</b>: Use the Const functor for arbitrary analysis
 * </ul>
 *
 * <h2>Why This Works</h2>
 *
 * <p>Unlike monadic programs where the next effect depends on the previous result (and thus cannot
 * be known until runtime), applicative programs have a static structure. All operations are visible
 * at construction time, enabling analysis before any effects are executed.
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Build a program
 * FreeAp<DbOp.Witness, Dashboard> program = buildDashboard(userId);
 *
 * // Count operations before running
 * int count = FreeApAnalyzer.countOperations(program);
 * System.out.println("Program will execute " + count + " operations");
 *
 * // Check for dangerous operations
 * boolean hasDeletions = FreeApAnalyzer.containsOperation(
 *     program,
 *     op -> op instanceof DeleteOp
 * );
 *
 * if (hasDeletions && !userApproved) {
 *     throw new SecurityException("Delete operations require approval");
 * }
 *
 * // Safe to execute
 * program.foldMap(interpreter, applicative);
 * }</pre>
 *
 * @see FreeAp
 * @see FreeAp#analyse(Natural, org.higherkindedj.hkt.Applicative)
 */
@NullMarked
public interface FreeApAnalyzer {

  /**
   * Counts the total number of operations in a Free Applicative program.
   *
   * <p>This method traverses the program structure and counts each {@code Lift} node, which
   * represents a single operation in the instruction set.
   *
   * <p>Example:
   *
   * <pre>{@code
   * FreeAp<DbOp.Witness, Dashboard> program =
   *     fetchUser(1).map2(fetchPosts(1), Dashboard::new);
   *
   * int count = FreeApAnalyzer.countOperations(program);
   * // count == 2 (fetchUser + fetchPosts)
   * }</pre>
   *
   * @param program The Free Applicative program to analyse. Must not be null.
   * @param <F> The type constructor of the instruction set
   * @param <A> The result type of the program
   * @return The total number of operations in the program
   * @throws NullPointerException if program is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> int countOperations(
      FreeAp<F, A> program) {
    requireNonNull(program, "Program cannot be null");
    return countOps(program);
  }

  /**
   * Collects all operations from a Free Applicative program into a list.
   *
   * <p>This method extracts every {@code Lift} node from the program structure, returning them in
   * the order they appear during traversal.
   *
   * <p>Example:
   *
   * <pre>{@code
   * FreeAp<DbOp.Witness, Dashboard> program =
   *     fetchUser(1).map2(fetchPosts(1), Dashboard::new);
   *
   * List<Kind<DbOp.Witness, ?>> ops = FreeApAnalyzer.collectOperations(program);
   * // ops contains both the fetchUser and fetchPosts operations
   * }</pre>
   *
   * @param program The Free Applicative program to analyse. Must not be null.
   * @param <F> The type constructor of the instruction set
   * @param <A> The result type of the program
   * @return A list of all operations in the program
   * @throws NullPointerException if program is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> List<Kind<F, ?>> collectOperations(
      FreeAp<F, A> program) {
    requireNonNull(program, "Program cannot be null");
    List<Kind<F, ?>> operations = new ArrayList<>();
    collectOps(program, operations);
    return operations;
  }

  /**
   * Checks if a Free Applicative program contains any operation matching the given predicate.
   *
   * <p>This is useful for security checks, permission validation, or detecting specific operation
   * types before execution.
   *
   * <p>Example:
   *
   * <pre>{@code
   * boolean hasDeletions = FreeApAnalyzer.containsOperation(
   *     program,
   *     op -> {
   *         Kind<FileOp.Witness, ?> fileOp = op;
   *         return FileOpHelper.narrow(fileOp) instanceof FileOp.Delete;
   *     }
   * );
   *
   * if (hasDeletions) {
   *     requireAdminApproval();
   * }
   * }</pre>
   *
   * @param program The Free Applicative program to analyse. Must not be null.
   * @param predicate The predicate to test operations against. Must not be null.
   * @param <F> The type constructor of the instruction set
   * @param <A> The result type of the program
   * @return {@code true} if any operation matches the predicate, {@code false} otherwise
   * @throws NullPointerException if program or predicate is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> boolean containsOperation(
      FreeAp<F, A> program, Predicate<Kind<F, ?>> predicate) {
    requireNonNull(program, "Program cannot be null");
    requireNonNull(predicate, "Predicate cannot be null");
    return containsOp(program, predicate);
  }

  /**
   * Groups operations in a Free Applicative program by their runtime class.
   *
   * <p>This method categorises operations by their concrete type, returning a map from class to
   * count. This is useful for understanding the composition of a program or for batching similar
   * operations.
   *
   * <p>Example:
   *
   * <pre>{@code
   * FreeAp<DbOp.Witness, Dashboard> program = ...;
   *
   * Map<Class<?>, Integer> groups = FreeApAnalyzer.groupByType(program, DbOpHelper::narrow);
   * // groups might contain: {FetchUser.class=1, FetchPosts.class=2, FetchSettings.class=1}
   * }</pre>
   *
   * @param program The Free Applicative program to analyse. Must not be null.
   * @param narrow A function to narrow Kind to the concrete operation type. Must not be null.
   * @param <F> The type constructor of the instruction set
   * @param <A> The result type of the program
   * @param <Op> The base type of operations
   * @return A map from operation class to occurrence count
   * @throws NullPointerException if program or narrow is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, Op> Map<Class<?>, Integer> groupByType(
      FreeAp<F, A> program, Function<Kind<F, ?>, Op> narrow) {
    requireNonNull(program, "Program cannot be null");
    requireNonNull(narrow, "Narrow function cannot be null");

    Map<Class<?>, Integer> groups = new HashMap<>();
    List<Kind<F, ?>> operations = collectOperations(program);

    for (Kind<F, ?> op : operations) {
      Op narrowed = narrow.apply(op);
      Class<?> clazz = narrowed.getClass();
      groups.merge(clazz, 1, Integer::sum);
    }

    return groups;
  }

  /**
   * Performs custom analysis on a Free Applicative program using the Const functor.
   *
   * <p>This is the most general analysis method. It uses a natural transformation to convert each
   * operation to a constant value, which is then combined using a monoid. The result accumulates
   * information about all operations without executing them.
   *
   * <p>Example - collecting operation names:
   *
   * <pre>{@code
   * Monoid<List<String>> listMonoid = Monoids.list();
   *
   * Natural<DbOp.Witness, ConstKind.Witness<List<String>>> toNames = fa -> {
   *     DbOp<?> op = DbOpHelper.narrow(fa);
   *     String name = op.getClass().getSimpleName();
   *     return ConstKindHelper.CONST.widen(new Const<>(List.of(name)));
   * };
   *
   * List<String> names = FreeApAnalyzer.analyseWith(program, toNames, listMonoid);
   * // names might be: ["FetchUser", "FetchPosts", "FetchSettings"]
   * }</pre>
   *
   * @param program The Free Applicative program to analyse. Must not be null.
   * @param toConst A natural transformation from operations to Const. Must not be null.
   * @param monoid The monoid used to combine constant values. Must not be null.
   * @param <F> The type constructor of the instruction set
   * @param <A> The result type of the program
   * @param <M> The type of the analysis result
   * @return The combined analysis result
   * @throws NullPointerException if any argument is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, M> M analyseWith(
      FreeAp<F, A> program, Natural<F, ConstKind.Witness<M>> toConst, Monoid<M> monoid) {
    requireNonNull(program, "Program cannot be null");
    requireNonNull(toConst, "Natural transformation cannot be null");
    requireNonNull(monoid, "Monoid cannot be null");

    ConstApplicative<M> constApplicative = new ConstApplicative<>(monoid);
    Kind<ConstKind.Witness<M>, A> result = program.analyse(toConst, constApplicative);
    return ConstKindHelper.CONST.narrow(result).value();
  }

  // ============================================================================
  // Private Implementation (iterative to avoid StackOverflowError on deep trees)
  // ============================================================================

  private static <F extends WitnessArity<TypeArity.Unary>, A> int countOps(FreeAp<F, A> freeAp) {
    int count = 0;
    Deque<FreeAp<F, ?>> stack = new ArrayDeque<>();
    stack.push(freeAp);

    while (!stack.isEmpty()) {
      FreeAp<F, ?> current = stack.pop();
      switch (current) {
        case FreeAp.Pure<F, ?> _ -> {
          // No operation to count
        }
        case FreeAp.Lift<F, ?> _ -> count++;
        case FreeAp.Ap<F, ?, ?> ap -> {
          stack.push(ap.fa());
          stack.push(ap.ff());
        }
      }
    }
    return count;
  }

  private static <F extends WitnessArity<TypeArity.Unary>, A> void collectOps(
      FreeAp<F, A> freeAp, List<Kind<F, ?>> accumulator) {
    Deque<FreeAp<F, ?>> stack = new ArrayDeque<>();
    stack.push(freeAp);

    while (!stack.isEmpty()) {
      FreeAp<F, ?> current = stack.pop();
      switch (current) {
        case FreeAp.Pure<F, ?> _ -> {
          // No operation to collect
        }
        case FreeAp.Lift<F, ?> lift -> accumulator.add(lift.fa());
        case FreeAp.Ap<F, ?, ?> ap -> {
          // Push in reverse order to maintain left-to-right traversal
          stack.push(ap.fa());
          stack.push(ap.ff());
        }
      }
    }
  }

  private static <F extends WitnessArity<TypeArity.Unary>, A> boolean containsOp(
      FreeAp<F, A> freeAp, Predicate<Kind<F, ?>> predicate) {
    Deque<FreeAp<F, ?>> stack = new ArrayDeque<>();
    stack.push(freeAp);

    while (!stack.isEmpty()) {
      FreeAp<F, ?> current = stack.pop();
      switch (current) {
        case FreeAp.Pure<F, ?> _ -> {
          // No operation to check
        }
        case FreeAp.Lift<F, ?> lift -> {
          if (predicate.test(lift.fa())) {
            return true; // Short-circuit on first match
          }
        }
        case FreeAp.Ap<F, ?, ?> ap -> {
          stack.push(ap.fa());
          stack.push(ap.ff());
        }
      }
    }
    return false;
  }
}
