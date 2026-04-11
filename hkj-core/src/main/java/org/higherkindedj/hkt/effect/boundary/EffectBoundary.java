// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.boundary;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.trymonad.Try;
import org.jspecify.annotations.NullMarked;

/**
 * Encapsulates the interpret-and-execute pattern for Free monad programs.
 *
 * <p>{@code EffectBoundary} is an immutable, thread-safe wrapper around a natural transformation
 * ({@code Natural<F, IOKind.Witness>}) and the IO monad instance. It provides a clean API surface
 * for interpreting Free monad programs, hiding the {@code foldMap()} + {@code narrow()} + {@code
 * unsafeRunSync()} ceremony behind intent-revealing method names.
 *
 * <p>This class always targets the IO monad for production use. For pure, deterministic testing,
 * use {@link TestBoundary} which targets the Id monad.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // Create a boundary with a composed interpreter
 * EffectBoundary<PaymentEffects> boundary = EffectBoundary.of(
 *     Interpreters.combine(gatewayInterp, fraudInterp, ledgerInterp, notifInterp));
 *
 * // Execute a program
 * PaymentResult result = boundary.run(service.processPayment(customer, amount, method));
 *
 * // Or return deferred IO for Spring controllers
 * IOPath<PaymentResult> io = boundary.runIO(service.processPayment(customer, amount, method));
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>This class is immutable and thread-safe. It is safe to use as a Spring singleton bean.
 *
 * @param <F> the composed effect witness type (e.g., {@code EitherFKind.Witness<A,
 *     EitherFKind.Witness<B, C>>})
 * @see TestBoundary
 * @see Free#foldMap(Natural, Monad)
 * @see Natural
 */
@NullMarked
public final class EffectBoundary<F extends WitnessArity<TypeArity.Unary>> {

  private static final ExecutorService VIRTUAL_EXECUTOR =
      Executors.newVirtualThreadPerTaskExecutor();

  private final Natural<F, IOKind.Witness> interpreter;
  private final Monad<IOKind.Witness> monad;

  private EffectBoundary(Natural<F, IOKind.Witness> interpreter) {
    this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
    this.monad = IOMonad.INSTANCE;
  }

  /**
   * Creates a new EffectBoundary with the given interpreter.
   *
   * @param interpreter the natural transformation from effect algebra F to IO
   * @param <F> the effect witness type
   * @return a new EffectBoundary instance
   * @throws NullPointerException if interpreter is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>> EffectBoundary<F> of(
      Natural<F, IOKind.Witness> interpreter) {
    return new EffectBoundary<>(interpreter);
  }

  /**
   * Interprets and executes a Free program synchronously.
   *
   * <p>Calls {@code foldMap(interpreter, monad)} on the program, narrows the result to {@code IO},
   * and calls {@code unsafeRunSync()}. Blocks the calling thread until execution completes.
   * Propagates interpreter exceptions directly.
   *
   * @param program the Free monad program to interpret and execute
   * @param <A> the result type
   * @return the result of executing the program
   * @throws NullPointerException if program is null
   * @throws RuntimeException if the interpreter throws during execution
   */
  public <A> A run(Free<F, A> program) {
    Objects.requireNonNull(program, "program must not be null");
    Kind<IOKind.Witness, A> result = program.foldMap(interpreter, monad);
    IO<A> io = IO_OP.narrow(result);
    return io.unsafeRunSync();
  }

  /**
   * Interprets and executes a FreePath program synchronously.
   *
   * <p>Extracts the underlying {@code Free} via {@code toFree()} and delegates to {@link
   * #run(Free)}.
   *
   * @param program the FreePath program to interpret and execute
   * @param <A> the result type
   * @return the result of executing the program
   * @throws NullPointerException if program is null
   */
  public <A> A run(FreePath<F, A> program) {
    Objects.requireNonNull(program, "program must not be null");
    return run(program.toFree());
  }

  /**
   * Interprets and executes a Free program, capturing any exception as a {@code Try.Failure}.
   *
   * <p>Never throws. Returns {@code Try.success(result)} on success or {@code
   * Try.failure(exception)} if the interpreter throws.
   *
   * @param program the Free monad program to interpret and execute
   * @param <A> the result type
   * @return a Try containing either the result or the exception
   * @throws NullPointerException if program is null
   */
  public <A> Try<A> runSafe(Free<F, A> program) {
    Objects.requireNonNull(program, "program must not be null");
    return Try.of(() -> run(program));
  }

  /**
   * Interprets and executes a Free program asynchronously on a virtual thread.
   *
   * <p>Returns a {@code CompletableFuture} that completes on a virtual thread. Uses {@code
   * Thread.ofVirtual()} for execution, consistent with {@code VTaskPathReturnValueHandler}.
   *
   * @param program the Free monad program to interpret and execute
   * @param <A> the result type
   * @return a CompletableFuture that will contain the result
   * @throws NullPointerException if program is null
   */
  public <A> CompletableFuture<A> runAsync(Free<F, A> program) {
    Objects.requireNonNull(program, "program must not be null");
    return CompletableFuture.supplyAsync(() -> run(program), VIRTUAL_EXECUTOR);
  }

  /**
   * Interprets a Free program into a deferred {@code IOPath}.
   *
   * <p>The program is <b>not</b> executed until {@code IOPath.unsafeRun()} is called or the IOPath
   * is consumed by a return value handler. This is the recommended method for Spring controllers
   * because {@code IOPathReturnValueHandler} manages execution and error mapping.
   *
   * @param program the Free monad program to wrap
   * @param <A> the result type
   * @return an IOPath that will interpret and execute the program when consumed
   * @throws NullPointerException if program is null
   */
  public <A> IOPath<A> runIO(Free<F, A> program) {
    Objects.requireNonNull(program, "program must not be null");
    return Path.io(() -> run(program));
  }

  /**
   * Interprets a FreePath program into a deferred {@code IOPath}.
   *
   * @param program the FreePath program to wrap
   * @param <A> the result type
   * @return an IOPath that will interpret and execute the program when consumed
   * @throws NullPointerException if program is null
   */
  public <A> IOPath<A> runIO(FreePath<F, A> program) {
    Objects.requireNonNull(program, "program must not be null");
    return runIO(program.toFree());
  }

  /**
   * Lifts an IO action into the Free monad as a pure value.
   *
   * <p>Executes the IO action immediately and wraps the result in {@code Free.pure()}. This is
   * useful for interleaving raw IO effects within a Free program built using {@code flatMap}.
   *
   * @param io the IO action to execute and embed
   * @param <A> the result type
   * @return a Free program containing the IO action's result
   * @throws NullPointerException if io is null
   */
  public <A> Free<F, A> embed(IO<A> io) {
    Objects.requireNonNull(io, "io must not be null");
    return Free.pure(io.unsafeRunSync());
  }

  /**
   * Lifts an IO action into a FreePath as a pure value.
   *
   * <p>Executes the IO action immediately and wraps the result in {@code FreePath.pure()}. Requires
   * a Functor instance for the effect algebra.
   *
   * @param io the IO action to execute and embed
   * @param functor the Functor instance for the effect algebra F
   * @param <A> the result type
   * @return a FreePath containing the IO action's result
   * @throws NullPointerException if io or functor is null
   */
  public <A> FreePath<F, A> embedPath(IO<A> io, Functor<F> functor) {
    Objects.requireNonNull(io, "io must not be null");
    Objects.requireNonNull(functor, "functor must not be null");
    return FreePath.pure(io.unsafeRunSync(), functor);
  }

  /**
   * Returns the natural transformation used by this boundary.
   *
   * @return the interpreter
   */
  public Natural<F, IOKind.Witness> interpreter() {
    return interpreter;
  }
}
