// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.boundary;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.jspecify.annotations.NullMarked;

/**
 * A test-oriented boundary that interprets Free monad programs using the Id monad.
 *
 * <p>Programs execute purely, synchronously, and deterministically. There is no IO, no threads, and
 * no network calls. This makes tests fast, reproducible, and easy to reason about.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * var stubFraud = new FixedRiskInterpreter(RiskScore.of(15));
 * var stubGateway = new RecordingGatewayInterpreter();
 * var interpreter = Interpreters.combine(stubGateway, stubFraud, stubLedger, stubNotification);
 * var boundary = TestBoundary.of(interpreter);
 *
 * PaymentResult result = boundary.run(
 *     service.processPayment(customer, tenDollars, visa));
 *
 * assertThat(result.isApproved()).isTrue();
 * assertThat(stubGateway.charges()).hasSize(1);
 * }</pre>
 *
 * <h2>Why Separate from EffectBoundary</h2>
 *
 * <p>Test interpreters target Id (pure values), not IO (deferred side effects). Separating the
 * types means:
 *
 * <ul>
 *   <li>Production code uses {@link EffectBoundary} (always IO)
 *   <li>Test code uses {@code TestBoundary} (always Id)
 *   <li>The {@code Free<G, A>} program itself is unchanged
 * </ul>
 *
 * @param <F> the composed effect witness type
 * @see EffectBoundary
 */
@NullMarked
public final class TestBoundary<F extends WitnessArity<TypeArity.Unary>> {

  private final Natural<F, IdKind.Witness> interpreter;

  private TestBoundary(Natural<F, IdKind.Witness> interpreter) {
    this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
  }

  /**
   * Creates a new TestBoundary with the given interpreter.
   *
   * @param interpreter the natural transformation from effect algebra F to Id
   * @param <F> the effect witness type
   * @return a new TestBoundary instance
   * @throws NullPointerException if interpreter is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>> TestBoundary<F> of(
      Natural<F, IdKind.Witness> interpreter) {
    return new TestBoundary<>(interpreter);
  }

  /**
   * Interprets and executes a Free program purely using the Id monad.
   *
   * <p>The program is interpreted via {@code foldMap(interpreter, IdMonad.instance())}, and the
   * result is unwrapped from the Id container. No IO or side effects occur beyond what the
   * interpreter directly performs.
   *
   * @param program the Free monad program to interpret
   * @param <A> the result type
   * @return the result of interpreting the program
   * @throws NullPointerException if program is null
   */
  public <A> A run(Free<F, A> program) {
    Objects.requireNonNull(program, "program must not be null");
    Kind<IdKind.Witness, A> result = program.foldMap(interpreter, IdMonad.instance());
    Id<A> id = ID.narrow(result);
    return id.value();
  }

  /**
   * Interprets and executes a FreePath program purely using the Id monad.
   *
   * @param program the FreePath program to interpret
   * @param <A> the result type
   * @return the result of interpreting the program
   * @throws NullPointerException if program is null
   */
  public <A> A run(FreePath<F, A> program) {
    Objects.requireNonNull(program, "program must not be null");
    return run(program.toFree());
  }

  /**
   * Analyses the structure of a Free program without executing it.
   *
   * <p>Walks the Free AST counting effect instructions (Suspend nodes), error recovery points
   * (HandleError nodes), and applicative blocks (Ap nodes). Useful for asserting program structure
   * in tests.
   *
   * @param program the Free program to analyse
   * @param <A> the result type
   * @return a ProgramAnalysis describing the program's structure
   * @throws NullPointerException if program is null
   */
  public <A> ProgramAnalysis analyse(Free<F, A> program) {
    Objects.requireNonNull(program, "program must not be null");
    Set<String> effects = new HashSet<>();
    int[] counts = {0, 0, 0}; // instructions, recoveryPoints, applicativeBlocks
    walkFree(program, effects, counts);
    return new ProgramAnalysis(effects, counts[0], counts[1], counts[2]);
  }

  private <A> void walkFree(Free<F, A> node, Set<String> effects, int[] counts) {
    switch (node) {
      case Free.Pure<F, A> ignored -> {}
      case Free.Suspend<F, A> suspend -> {
        counts[0]++;
        // Extract effect type from the computation's runtime class
        Kind<F, Free<F, A>> computation = suspend.computation();
        effects.add(computation.getClass().getSimpleName());
      }
      case Free.FlatMapped<F, ?, A> fm -> walkFree(fm.sub(), effects, counts);
      case Free.HandleError<F, ?, A> he -> {
        counts[1]++;
        walkFree(he.program(), effects, counts);
      }
      case Free.Ap<F, A> ignored -> counts[2]++;
    }
  }

  /**
   * Returns the natural transformation used by this boundary.
   *
   * @return the interpreter
   */
  public Natural<F, IdKind.Witness> interpreter() {
    return interpreter;
  }
}
