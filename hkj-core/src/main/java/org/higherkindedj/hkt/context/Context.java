// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;

/**
 * Represents a computation that reads a value of type {@code R} from a {@link ScopedValue
 * ScopedValue<R>} and produces a result of type {@code A}.
 *
 * <p>{@code Context<R, A>} is the functional interface for working with Java's {@link ScopedValue}
 * API in a composable way. It enables context propagation patterns that work correctly with virtual
 * threads and structured concurrency, where child threads automatically inherit scoped value
 * bindings from their parent.
 *
 * <p><b>Key Characteristics:</b>
 *
 * <ul>
 *   <li><b>Scoped Value Access:</b> Reads from thread-scoped {@link ScopedValue} containers rather
 *       than requiring explicit parameter passing.
 *   <li><b>Thread Inheritance:</b> Values bound in a parent thread are automatically visible to
 *       child virtual threads forked within the same scope.
 *   <li><b>Composability:</b> Supports {@link #map(Function)}, {@link #flatMap(Function)}, and
 *       other functional composition methods.
 *   <li><b>HKT Integration:</b> Implements {@link ContextKind} for use with Higher-Kinded-J type
 *       classes like {@link org.higherkindedj.hkt.Functor Functor} and {@link
 *       org.higherkindedj.hkt.Monad Monad}.
 * </ul>
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * // Define a scoped value
 * static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();
 *
 * // Create a context that reads it
 * Context<String, String> getTraceId = Context.ask(TRACE_ID);
 *
 * // Transform the result
 * Context<String, String> formatTrace = getTraceId.map(id -> "[" + id + "]");
 *
 * // Run within a scoped binding
 * String result = ScopedValue
 *     .where(TRACE_ID, "abc-123")
 *     .call(() -> formatTrace.run());
 * // result = "[abc-123]"
 * }</pre>
 *
 * @param <R> The type of the value read from the {@link ScopedValue}.
 * @param <A> The type of the result produced by this context computation.
 * @see ScopedValue
 * @see ContextKind
 * @see ContextMonad
 */
public sealed interface Context<R, A> extends ContextKind<R, A>
    permits Context.Ask,
        Context.Pure,
        Context.FlatMapped,
        Context.Failed,
        Context.Recovered,
        Context.RecoveredWith,
        Context.ErrorMapped {

  /**
   * Executes this context computation, reading from any required {@link ScopedValue} bindings and
   * producing a result.
   *
   * <p>This method should be called within a scope where all required {@link ScopedValue}s are
   * bound. If a required scoped value is not bound, a {@link NoSuchElementException} will be
   * thrown.
   *
   * @return The result of the computation.
   * @throws NoSuchElementException if a required {@link ScopedValue} is not bound.
   * @throws RuntimeException if the computation fails.
   */
  @Nullable A run();

  // ===== FACTORY METHODS =====

  /**
   * Creates a {@code Context} that reads from the specified {@link ScopedValue} and returns the
   * value unchanged.
   *
   * <p>This is the fundamental operation for accessing scoped values. The returned context, when
   * run, will call {@link ScopedValue#get()} on the provided key.
   *
   * @param key The {@link ScopedValue} to read from. Must not be null.
   * @param <R> The type of the scoped value.
   * @return A {@code Context<R, R>} that reads and returns the scoped value.
   * @throws NullPointerException if {@code key} is null.
   */
  static <R> Context<R, R> ask(ScopedValue<R> key) {
    Objects.requireNonNull(key, "key cannot be null");
    return new Ask<>(key, Function.identity());
  }

  /**
   * Creates a {@code Context} that reads from the specified {@link ScopedValue} and transforms the
   * value using the provided function.
   *
   * <p>This is a convenience method combining {@link #ask(ScopedValue)} and {@link #map(Function)}.
   *
   * @param key The {@link ScopedValue} to read from. Must not be null.
   * @param f The function to apply to the scoped value. Must not be null.
   * @param <R> The type of the scoped value.
   * @param <A> The type of the transformed result.
   * @return A {@code Context<R, A>} that reads the scoped value and applies the function.
   * @throws NullPointerException if {@code key} or {@code f} is null.
   */
  static <R, A> Context<R, A> asks(ScopedValue<R> key, Function<? super R, ? extends A> f) {
    Objects.requireNonNull(key, "key cannot be null");
    Objects.requireNonNull(f, "function cannot be null");
    return new Ask<>(key, f);
  }

  /**
   * Creates a {@code Context} that succeeds immediately with the given value, without reading from
   * any {@link ScopedValue}.
   *
   * <p>This is the "pure" or "return" operation for the Context monad.
   *
   * @param value The value to wrap. May be null.
   * @param <R> The phantom type parameter for the scoped value (not used).
   * @param <A> The type of the value.
   * @return A {@code Context<R, A>} that succeeds with the given value.
   */
  static <R, A> Context<R, A> succeed(@Nullable A value) {
    return new Pure<>(value);
  }

  /**
   * Creates a {@code Context} that fails immediately with the given error when run.
   *
   * @param error The error to fail with. Must not be null.
   * @param <R> The phantom type parameter for the scoped value.
   * @param <A> The phantom type parameter for the result.
   * @return A {@code Context<R, A>} that fails with the given error.
   * @throws NullPointerException if {@code error} is null.
   */
  static <R, A> Context<R, A> fail(Throwable error) {
    Objects.requireNonNull(error, "error cannot be null");
    return new Failed<>(error);
  }

  // ===== COMBINATOR METHODS =====

  /**
   * Combines two {@code Context} values using a combining function.
   *
   * <p>This is a convenience method that avoids nested {@code flatMap} calls when combining two
   * context computations.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Context<String, Integer> getAge = Context.asks(USER_KEY, User::age);
   * Context<String, String> getName = Context.asks(USER_KEY, User::name);
   *
   * Context<String, String> greeting = Context.map2(
   *     getAge,
   *     getName,
   *     (age, name) -> name + " is " + age + " years old"
   * );
   * }</pre>
   *
   * @param ca The first context. Must not be null.
   * @param cb The second context. Must not be null.
   * @param f The combining function. Must not be null.
   * @param <R> The type of the scoped value.
   * @param <A> The type of the first context's result.
   * @param <B> The type of the second context's result.
   * @param <C> The type of the combined result.
   * @return A {@code Context<R, C>} that combines both results.
   * @throws NullPointerException if any argument is null.
   */
  static <R, A, B, C> Context<R, C> map2(
      Context<R, A> ca, Context<R, B> cb, BiFunction<? super A, ? super B, ? extends C> f) {
    Objects.requireNonNull(ca, "ca cannot be null");
    Objects.requireNonNull(cb, "cb cannot be null");
    Objects.requireNonNull(f, "f cannot be null");
    return ca.flatMap(a -> cb.map(b -> f.apply(a, b)));
  }

  /**
   * Combines three {@code Context} values using a combining function.
   *
   * <p>This is a convenience method that avoids deeply nested {@code flatMap} calls when combining
   * three context computations.
   *
   * @param ca The first context. Must not be null.
   * @param cb The second context. Must not be null.
   * @param cc The third context. Must not be null.
   * @param f The combining function. Must not be null.
   * @param <R> The type of the scoped value.
   * @param <A> The type of the first context's result.
   * @param <B> The type of the second context's result.
   * @param <C> The type of the third context's result.
   * @param <D> The type of the combined result.
   * @return A {@code Context<R, D>} that combines all results.
   * @throws NullPointerException if any argument is null.
   */
  static <R, A, B, C, D> Context<R, D> map3(
      Context<R, A> ca, Context<R, B> cb, Context<R, C> cc, Function3<? super A, ? super B, ? super C, ? extends D> f) {
    Objects.requireNonNull(ca, "ca cannot be null");
    Objects.requireNonNull(cb, "cb cannot be null");
    Objects.requireNonNull(cc, "cc cannot be null");
    Objects.requireNonNull(f, "f cannot be null");
    return ca.flatMap(a -> cb.flatMap(b -> cc.map(c -> f.apply(a, b, c))));
  }

  // ===== COMPOSITION METHODS =====

  /**
   * Transforms the result of this context using the provided function.
   *
   * <p>This is the Functor {@code map} operation for Context.
   *
   * @param f The function to apply to the result. Must not be null.
   * @param <B> The type of the transformed result.
   * @return A new {@code Context<R, B>} with the transformed result.
   * @throws NullPointerException if {@code f} is null.
   */
  default <B> Context<R, B> map(Function<? super A, ? extends B> f) {
    Objects.requireNonNull(f, "function cannot be null");
    return flatMap(a -> succeed(f.apply(a)));
  }

  /**
   * Composes this context with a function that produces another context.
   *
   * <p>This is the Monad {@code flatMap} (or {@code bind}) operation for Context. It allows
   * sequencing context computations where each step can depend on the result of the previous.
   *
   * @param f A function that takes the result and returns a new context. Must not be null.
   * @param <B> The type of the result produced by the new context.
   * @return A new {@code Context<R, B>} representing the composed computation.
   * @throws NullPointerException if {@code f} is null.
   */
  default <B> Context<R, B> flatMap(Function<? super A, ? extends Context<R, ? extends B>> f) {
    Objects.requireNonNull(f, "function cannot be null");
    return new FlatMapped<>(this, f);
  }

  /**
   * Recovers from a failure by applying the given function to produce a recovery value.
   *
   * @param recoveryFunction A function that takes the error and returns a recovery value. Must not
   *     be null.
   * @return A new {@code Context<R, A>} that recovers from failures.
   * @throws NullPointerException if {@code recoveryFunction} is null.
   */
  default Context<R, A> recover(Function<? super Throwable, ? extends A> recoveryFunction) {
    Objects.requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
    return new Recovered<>(this, recoveryFunction);
  }

  /**
   * Recovers from a failure by applying the given function to produce a recovery context.
   *
   * @param recoveryFunction A function that takes the error and returns a recovery context. Must
   *     not be null.
   * @return A new {@code Context<R, A>} that recovers from failures.
   * @throws NullPointerException if {@code recoveryFunction} is null.
   */
  default Context<R, A> recoverWith(
      Function<? super Throwable, ? extends Context<R, ? extends A>> recoveryFunction) {
    Objects.requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
    return new RecoveredWith<>(this, recoveryFunction);
  }

  /**
   * Transforms the error if this context fails.
   *
   * @param f A function that transforms the error. Must not be null.
   * @return A new {@code Context<R, A>} with the transformed error.
   * @throws NullPointerException if {@code f} is null.
   */
  default Context<R, A> mapError(Function<? super Throwable, ? extends Throwable> f) {
    Objects.requireNonNull(f, "function cannot be null");
    return new ErrorMapped<>(this, f);
  }

  // ===== CONVERSION METHODS =====

  /**
   * Converts this context to a {@link VTask} that reads from the currently bound scoped values.
   *
   * <p>The returned VTask, when executed, will run this context computation. The scoped values must
   * be bound in the scope where the VTask is executed.
   *
   * @return A {@link VTask} that executes this context computation.
   */
  default VTask<A> toVTask() {
    return VTask.delay(this::run);
  }

  /**
   * Converts this context to a {@link Maybe}, returning {@link Maybe#nothing()} if the computation
   * fails.
   *
   * @return A {@link Maybe} containing the result or nothing if failed.
   */
  default Maybe<A> toMaybe() {
    try {
      A result = run();
      return result != null ? Maybe.just(result) : Maybe.nothing();
    } catch (Throwable t) {
      return Maybe.nothing();
    }
  }

  /**
   * Discards the result, returning {@link Unit}.
   *
   * @return A {@code Context<R, Unit>} that performs the same computation but returns Unit.
   */
  default Context<R, Unit> asUnit() {
    return map(_ -> Unit.INSTANCE);
  }

  // ===== IMPLEMENTATION CLASSES =====

  /**
   * Implementation that reads from a {@link ScopedValue} and applies a transformation.
   *
   * @param <R> The type of the scoped value.
   * @param <A> The type of the result after transformation.
   */
  record Ask<R, A>(ScopedValue<R> key, Function<? super R, ? extends A> transform)
      implements Context<R, A> {

    /** Compact constructor for validation. */
    public Ask {
      Objects.requireNonNull(key, "key cannot be null");
      Objects.requireNonNull(transform, "transform cannot be null");
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable A run() {
      R value = key.get();
      return (A) transform.apply(value);
    }
  }

  /**
   * Implementation that succeeds with a pure value.
   *
   * @param <R> The phantom type parameter for the scoped value.
   * @param <A> The type of the value.
   */
  record Pure<R, A>(@Nullable A value) implements Context<R, A> {

    @Override
    public @Nullable A run() {
      return value;
    }
  }

  /**
   * Implementation that composes two context computations.
   *
   * @param <R> The type of the scoped value.
   * @param <A> The intermediate type.
   * @param <B> The final result type.
   */
  record FlatMapped<R, A, B>(
      Context<R, A> source, Function<? super A, ? extends Context<R, ? extends B>> f)
      implements Context<R, B> {

    /** Compact constructor for validation. */
    public FlatMapped {
      Objects.requireNonNull(source, "source cannot be null");
      Objects.requireNonNull(f, "function cannot be null");
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable B run() {
      A a = source.run();
      Context<R, ? extends B> next = f.apply(a);
      Objects.requireNonNull(next, "flatMap function returned null context");
      return (B) next.run();
    }
  }

  /**
   * Implementation that fails with an error.
   *
   * @param <R> The phantom type parameter for the scoped value.
   * @param <A> The phantom type parameter for the result.
   */
  record Failed<R, A>(Throwable error) implements Context<R, A> {

    /** Compact constructor for validation. */
    public Failed {
      Objects.requireNonNull(error, "error cannot be null");
    }

    @Override
    public @Nullable A run() {
      throw sneakyThrow(error);
    }
  }

  /**
   * Implementation that recovers from failures with a recovery value.
   *
   * @param <R> The type of the scoped value.
   * @param <A> The type of the result.
   */
  record Recovered<R, A>(
      Context<R, A> source, Function<? super Throwable, ? extends A> recoveryFunction)
      implements Context<R, A> {

    /** Compact constructor for validation. */
    public Recovered {
      Objects.requireNonNull(source, "source cannot be null");
      Objects.requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable A run() {
      try {
        return source.run();
      } catch (Throwable t) {
        return (A) recoveryFunction.apply(t);
      }
    }
  }

  /**
   * Implementation that recovers from failures with a recovery context.
   *
   * @param <R> The type of the scoped value.
   * @param <A> The type of the result.
   */
  record RecoveredWith<R, A>(
      Context<R, A> source,
      Function<? super Throwable, ? extends Context<R, ? extends A>> recoveryFunction)
      implements Context<R, A> {

    /** Compact constructor for validation. */
    public RecoveredWith {
      Objects.requireNonNull(source, "source cannot be null");
      Objects.requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable A run() {
      try {
        return source.run();
      } catch (Throwable t) {
        Context<R, ? extends A> recovery = recoveryFunction.apply(t);
        Objects.requireNonNull(recovery, "recovery context cannot be null");
        return (A) recovery.run();
      }
    }
  }

  /**
   * Implementation that transforms errors.
   *
   * @param <R> The type of the scoped value.
   * @param <A> The type of the result.
   */
  record ErrorMapped<R, A>(
      Context<R, A> source, Function<? super Throwable, ? extends Throwable> errorMapper)
      implements Context<R, A> {

    /** Compact constructor for validation. */
    public ErrorMapped {
      Objects.requireNonNull(source, "source cannot be null");
      Objects.requireNonNull(errorMapper, "errorMapper cannot be null");
    }

    @Override
    public @Nullable A run() {
      try {
        return source.run();
      } catch (Throwable t) {
        throw sneakyThrow(errorMapper.apply(t));
      }
    }
  }

  // ===== UTILITY METHODS =====

  /**
   * Utility method to throw checked exceptions without declaring them.
   *
   * @param t The throwable to throw.
   * @return Never returns; always throws.
   */
  @SuppressWarnings("unchecked")
  private static <T extends Throwable> RuntimeException sneakyThrow(Throwable t) throws T {
    throw (T) t;
  }
}
