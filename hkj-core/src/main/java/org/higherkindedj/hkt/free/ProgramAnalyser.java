// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.higherkindedj.hkt.util.validation.Operation.FROM_KIND;

import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * Static analyser for Free monad programs.
 *
 * <p>Performs a structural traversal of the Free program tree to count instructions, error recovery
 * points, and parallel scopes without executing the program. This is the foundation for program
 * inspection — one of the key capabilities that dependency injection frameworks cannot provide.
 *
 * <h2>Limitations</h2>
 *
 * <p>{@code ProgramAnalyser} traverses the visible spine of the program. {@link Free.FlatMapped}
 * continuations are opaque {@code Function} values that produce further Free programs only when
 * applied to a concrete value. The analyser reports a <b>lower bound</b> on operation counts. The
 * {@link ProgramAnalysis#hasOpaqueRegions()} flag indicates whether the tree contains such opaque
 * regions.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * Free<AppEffects, PaymentResult> program = service.processPayment(customer, amount, method);
 * ProgramAnalysis analysis = ProgramAnalyser.analyse(program);
 *
 * System.out.println(analysis.suspendCount() + " instructions");
 * System.out.println(analysis.recoveryPoints() + " error recovery points");
 * System.out.println(analysis.parallelScopes() + " parallel scopes");
 * }</pre>
 *
 * @see ProgramAnalysis
 * @see Free
 */
@NullMarked
public final class ProgramAnalyser {

  private ProgramAnalyser() {}

  /**
   * Analyses a Free program tree, counting instructions, recovery points, and parallel scopes.
   *
   * <p>The traversal is structural and does not execute the program. Results are lower bounds due
   * to opaque {@link Free.FlatMapped} continuations.
   *
   * @param program the Free program to analyse
   * @param <F> the functor type
   * @param <A> the result type
   * @return the analysis results
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> ProgramAnalysis analyse(
      Free<F, A> program) {
    Validation.function().require(program, "program", FROM_KIND);
    return traverseTree(program);
  }

  /**
   * Internal recursive traversal of the Free program tree.
   *
   * @param free the Free node to traverse
   * @param <F> the functor type
   * @param <A> the result type
   * @return the analysis for this sub-tree
   */
  private static <F extends WitnessArity<TypeArity.Unary>, A> ProgramAnalysis traverseTree(
      Free<F, A> free) {
    return switch (free) {
      case Free.Pure<F, A> _ -> ProgramAnalysis.EMPTY;

      case Free.Suspend<F, A> _ -> new ProgramAnalysis(1, 0, 0, 0, false);

      case Free.FlatMapped<F, ?, A> fm -> {
        // Analyse the sub-program (visible spine)
        ProgramAnalysis subAnalysis = traverseTree(fm.sub());
        // The continuation is opaque — we cannot inspect it without a value
        yield subAnalysis.combine(new ProgramAnalysis(0, 0, 0, 1, true));
      }

      case Free.HandleError<F, ?, A> he -> {
        // Analyse the inner program
        ProgramAnalysis innerAnalysis = traverseTree(he.program());
        // Count this as a recovery point; handler is opaque
        yield innerAnalysis.combine(new ProgramAnalysis(0, 1, 0, 0, false));
      }

      case Free.Ap<F, A> ap -> {
        // The Ap node wraps a FreeAp. Count it as a parallel scope.
        // FreeAp sub-tree analysis could be added by traversing the applicative structure,
        // but for now we count the Ap node itself.
        yield new ProgramAnalysis(0, 0, 1, 0, false);
      }
    };
  }
}
