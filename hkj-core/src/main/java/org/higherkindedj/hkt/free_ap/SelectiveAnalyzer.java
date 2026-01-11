// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free_ap;

import static java.util.Objects.requireNonNull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.constant.ConstApplicative;
import org.higherkindedj.hkt.constant.ConstKind;
import org.higherkindedj.hkt.constant.ConstKindHelper;
import org.jspecify.annotations.NullMarked;

/**
 * Static analysis utilities for programs with selective semantics.
 *
 * <p>This class provides methods to analyse programs that use conditional effects, determining
 * bounds on what effects might or will execute. The analysis is based on the concept of "Under" and
 * "Over" approximations from the Selective Applicative Functors paper.
 *
 * <h2>Under and Over Semantics</h2>
 *
 * <p>When analysing conditional programs, we can compute two bounds:
 *
 * <ul>
 *   <li><b>Under (minimum effects)</b>: Effects that will definitely execute regardless of runtime
 *       conditions. This is a conservative lower bound.
 *   <li><b>Over (maximum effects)</b>: Effects that might possibly execute if the right conditions
 *       occur. This explores all branches and is an upper bound.
 * </ul>
 *
 * <h2>Integration with FreeAp</h2>
 *
 * <p>For practical static analysis, programs should be expressed using {@link FreeAp} (Free
 * Applicative). While Selective adds conditional branching on top of Applicative, the static
 * structure remains visible when using FreeAp as the underlying representation.
 *
 * <p>This class provides utilities that work with FreeAp programs interpreted with selective-like
 * semantics, as well as general utilities for understanding effect bounds.
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Build a program with conditional effects
 * FreeAp<Op.Witness, Result> program = ...;
 *
 * // Collect all possible effects (Over semantics)
 * Set<Op<?>> possibleEffects = SelectiveAnalyzer.collectPossibleEffects(
 *     program,
 *     OpHelper::narrow
 * );
 *
 * // Report to user
 * System.out.println("This program might execute: " + possibleEffects);
 * }</pre>
 *
 * @see FreeApAnalyzer
 * @see org.higherkindedj.hkt.Selective
 */
@NullMarked
public interface SelectiveAnalyzer {

  /**
   * Collects all possible effects from a FreeAp program (Over semantics).
   *
   * <p>This method returns all effects that might possibly execute, treating conditional branches
   * as if both sides could run. This is useful for:
   *
   * <ul>
   *   <li>Permission checking (ensuring all possible operations are authorised)
   *   <li>Resource estimation (maximum possible resource usage)
   *   <li>Audit trails (what could this program do?)
   * </ul>
   *
   * <p>For FreeAp programs, this is equivalent to {@link FreeApAnalyzer#collectOperations} since
   * FreeAp captures all independent operations. The difference becomes meaningful when programs
   * include conditional logic.
   *
   * @param program The FreeAp program to analyse. Must not be null.
   * @param narrow A function to narrow Kind to the concrete operation type. Must not be null.
   * @param <F> The type constructor of the instruction set
   * @param <A> The result type of the program
   * @param <Op> The base type of operations
   * @return A set of all operations that might execute
   * @throws NullPointerException if any argument is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, Op> Set<Op> collectPossibleEffects(
      FreeAp<F, A> program, Function<Kind<F, ?>, Op> narrow) {
    requireNonNull(program, "Program cannot be null");
    requireNonNull(narrow, "Narrow function cannot be null");

    Monoid<Set<Op>> setMonoid = Monoids.set();
    // Use anonymous class because Natural.apply is a generic method
    Natural<F, ConstKind.Witness<Set<Op>>> toSet =
        new Natural<>() {
          @Override
          public <B> Kind<ConstKind.Witness<Set<Op>>, B> apply(Kind<F, B> fa) {
            Op op = narrow.apply(fa);
            Set<Op> singleton = new HashSet<>();
            singleton.add(op);
            return ConstKindHelper.CONST.widen(new Const<>(singleton));
          }
        };

    ConstApplicative<Set<Op>> constApplicative = new ConstApplicative<>(setMonoid);
    Kind<ConstKind.Witness<Set<Op>>, A> result = program.analyse(toSet, constApplicative);
    return ConstKindHelper.CONST.narrow(result).value();
  }

  /**
   * Counts the maximum possible operations in a program (Over semantics).
   *
   * <p>Returns the total count of operations assuming all branches execute. This provides an upper
   * bound on execution count.
   *
   * @param program The FreeAp program to analyse. Must not be null.
   * @param <F> The type constructor of the instruction set
   * @param <A> The result type of the program
   * @return The maximum number of operations that might execute
   * @throws NullPointerException if program is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> int countMaximumEffects(
      FreeAp<F, A> program) {
    requireNonNull(program, "Program cannot be null");
    return FreeApAnalyzer.countOperations(program);
  }

  /**
   * Computes effect bounds for a FreeAp program.
   *
   * <p>Returns both the minimum (Under) and maximum (Over) effect counts. For pure FreeAp programs
   * without conditional logic, these will be equal. The difference becomes meaningful when the
   * program includes selective branching.
   *
   * @param program The FreeAp program to analyse. Must not be null.
   * @param <F> The type constructor of the instruction set
   * @param <A> The result type of the program
   * @return An {@link EffectBounds} record containing min and max effect counts
   * @throws NullPointerException if program is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> EffectBounds computeEffectBounds(
      FreeAp<F, A> program) {
    requireNonNull(program, "Program cannot be null");

    // For pure FreeAp (no conditional logic), min == max
    int count = FreeApAnalyzer.countOperations(program);
    return new EffectBounds(count, count);
  }

  /**
   * Partitions effects into guaranteed and conditional categories.
   *
   * <p>This method analyses a FreeAp program and categorises operations based on whether they will
   * definitely execute or only conditionally execute. For pure FreeAp programs, all operations are
   * guaranteed.
   *
   * @param program The FreeAp program to analyse. Must not be null.
   * @param narrow A function to narrow Kind to the concrete operation type. Must not be null.
   * @param <F> The type constructor of the instruction set
   * @param <A> The result type of the program
   * @param <Op> The base type of operations
   * @return An {@link EffectPartition} containing guaranteed and conditional effect sets
   * @throws NullPointerException if any argument is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, Op>
      EffectPartition<Op> partitionEffects(FreeAp<F, A> program, Function<Kind<F, ?>, Op> narrow) {
    requireNonNull(program, "Program cannot be null");
    requireNonNull(narrow, "Narrow function cannot be null");

    // For pure FreeAp, all effects are guaranteed
    Set<Op> allEffects = collectPossibleEffects(program, narrow);
    return new EffectPartition<>(allEffects, Set.of());
  }

  /**
   * Checks if a program contains any potentially dangerous operations.
   *
   * <p>This method uses Over semantics to check if any possible execution path includes operations
   * matching the given predicate. Useful for permission checking before execution.
   *
   * @param program The FreeAp program to analyse. Must not be null.
   * @param narrow A function to narrow Kind to the concrete operation type. Must not be null.
   * @param isDangerous A predicate identifying dangerous operations. Must not be null.
   * @param <F> The type constructor of the instruction set
   * @param <A> The result type of the program
   * @param <Op> The base type of operations
   * @return {@code true} if any possible execution path includes a dangerous operation
   * @throws NullPointerException if any argument is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, Op> boolean containsDangerousEffect(
      FreeAp<F, A> program,
      Function<Kind<F, ?>, Op> narrow,
      java.util.function.Predicate<Op> isDangerous) {
    requireNonNull(program, "Program cannot be null");
    requireNonNull(narrow, "Narrow function cannot be null");
    requireNonNull(isDangerous, "Predicate cannot be null");

    Set<Op> allEffects = collectPossibleEffects(program, narrow);
    return allEffects.stream().anyMatch(isDangerous);
  }

  /**
   * Represents bounds on effect execution.
   *
   * <p>For programs with conditional logic:
   *
   * <ul>
   *   <li>{@code minimum}: Effects that will definitely execute (Under semantics)
   *   <li>{@code maximum}: Effects that might possibly execute (Over semantics)
   * </ul>
   *
   * <p>For pure applicative programs, {@code minimum == maximum}.
   *
   * @param minimum The minimum number of effects that will execute
   * @param maximum The maximum number of effects that could execute
   */
  public record EffectBounds(int minimum, int maximum) {

    /**
     * Creates effect bounds.
     *
     * @throws IllegalArgumentException if minimum is negative or greater than maximum
     */
    public EffectBounds {
      if (minimum < 0) {
        throw new IllegalArgumentException("Minimum cannot be negative");
      }
      if (maximum < minimum) {
        throw new IllegalArgumentException("Maximum cannot be less than minimum");
      }
    }

    /** Returns {@code true} if the bounds are tight (minimum equals maximum). */
    public boolean isTight() {
      return minimum == maximum;
    }

    /** Returns the range between minimum and maximum effects. */
    public int range() {
      return maximum - minimum;
    }
  }

  /**
   * Represents a partition of effects into guaranteed and conditional categories.
   *
   * @param guaranteed Effects that will definitely execute
   * @param conditional Effects that may or may not execute depending on runtime conditions
   * @param <Op> The type of operations
   */
  public record EffectPartition<Op>(Set<Op> guaranteed, Set<Op> conditional) {

    /** Creates an effect partition with defensive copies. */
    public EffectPartition {
      guaranteed = Set.copyOf(guaranteed);
      conditional = Set.copyOf(conditional);
    }

    /** Returns all possible effects (union of guaranteed and conditional). */
    public Set<Op> allPossible() {
      Set<Op> all = new HashSet<>(guaranteed);
      all.addAll(conditional);
      return all;
    }

    /** Returns {@code true} if all effects are guaranteed (no conditional effects). */
    public boolean allGuaranteed() {
      return conditional.isEmpty();
    }
  }
}
