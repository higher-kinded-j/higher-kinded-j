// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Represents a stack-safe computation that can be trampolined to avoid stack overflow errors in
 * deeply recursive computations.
 *
 * <p>A {@code Trampoline} is a data structure that allows tail-recursive computations to be
 * converted into iterative ones, thus avoiding {@link StackOverflowError} on deep recursion. This
 * is particularly useful for recursive algorithms that would otherwise exceed the JVM's call stack
 * limit.
 *
 * <p><b>Key Characteristics:</b>
 *
 * <ul>
 *   <li><b>Stack Safety:</b> Converts recursive calls into data structures that are processed
 *       iteratively, preventing stack overflow.
 *   <li><b>Lazy Evaluation:</b> Computations are not executed until {@link #run()} is explicitly
 *       called.
 *   <li><b>Composability:</b> Trampolined computations can be chained using {@link #map(Function)}
 *       and {@link #flatMap(Function)}.
 *   <li><b>Tail Call Optimisation:</b> Effectively provides tail-call optimisation for Java, which
 *       lacks native support for it.
 * </ul>
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Converting deeply recursive algorithms to stack-safe iterative ones
 *   <li>Implementing mutually recursive functions without stack overflow
 *   <li>Processing deeply nested data structures (e.g., trees with millions of nodes)
 *   <li>Implementing interpreters and evaluators that need to handle deep call chains
 * </ul>
 *
 * <p><b>Example - Factorial:</b>
 *
 * <pre>{@code
 * // Naive recursive factorial would overflow for large n
 * // Stack-safe trampolined version:
 * Trampoline<BigInteger> factorial(BigInteger n, BigInteger acc) {
 *     if (n.compareTo(BigInteger.ZERO) <= 0) {
 *         return Trampoline.done(acc);
 *     }
 *     return Trampoline.defer(() ->
 *         factorial(n.subtract(BigInteger.ONE), n.multiply(acc))
 *     );
 * }
 *
 * // Safe for very large numbers
 * BigInteger result = factorial(BigInteger.valueOf(100000), BigInteger.ONE).run();
 * }</pre>
 *
 * <p><b>Example - Mutual Recursion:</b>
 *
 * <pre>{@code
 * Trampoline<Boolean> isEven(int n) {
 *     if (n == 0) return Trampoline.done(true);
 *     return Trampoline.defer(() -> isOdd(n - 1));
 * }
 *
 * Trampoline<Boolean> isOdd(int n) {
 *     if (n == 0) return Trampoline.done(false);
 *     return Trampoline.defer(() -> isEven(n - 1));
 * }
 *
 * boolean result = isEven(1000000).run(); // Stack-safe!
 * }</pre>
 *
 * <p><b>Implementation Notes:</b>
 *
 * <p>This implementation uses three constructors:
 *
 * <ul>
 *   <li>{@link Done} - Represents a completed computation with a final value
 *   <li>{@link More} - Represents a suspended computation (deferred thunk)
 *   <li>{@link FlatMap} - Represents a sequenced computation (right-associated bind)
 * </ul>
 *
 * <p>The {@link #run()} method uses an iterative algorithm with an explicit continuation stack to
 * evaluate the trampoline in constant stack space, regardless of recursion depth.
 *
 * @param <A> The type of the value produced when the computation completes.
 */
public sealed interface Trampoline<A> permits Trampoline.Done, Trampoline.More, Trampoline.FlatMap {

  Class<Trampoline> TRAMPOLINE_CLASS = Trampoline.class;

  /**
   * Represents a completed computation that holds a final value.
   *
   * @param <A> The type of the value held.
   * @param value The final value of the computation. Can be {@code null} if {@code A} is a nullable
   *     type.
   */
  record Done<A>(A value) implements Trampoline<A> {
    /**
     * Compact constructor for validation.
     *
     * @param value The value to hold (may be null for nullable types).
     */
    public Done {
      // No validation needed - value can be null for nullable types
    }
  }

  /**
   * Represents a suspended computation that will be evaluated later. This is used to defer a
   * recursive call and convert it into a data structure.
   *
   * @param <A> The type of the value that will be produced.
   * @param next A supplier that produces the next {@code Trampoline} step. Must not be {@code null}
   *     and must not return {@code null}.
   */
  record More<A>(Supplier<Trampoline<A>> next) implements Trampoline<A> {
    /**
     * Compact constructor with validation.
     *
     * @param next The supplier for the next trampoline step. Must not be null.
     * @throws NullPointerException if {@code next} is {@code null}.
     */
    public More {
      Validation.coreType().requireValue(next, TRAMPOLINE_CLASS, CONSTRUCTION);
    }
  }

  /**
   * Represents a sequenced computation resulting from {@link #flatMap(Function)}. This constructor
   * is essential for stack safety as it allows right-association of bind operations.
   *
   * @param <A> The type of the intermediate value.
   * @param <B> The type of the final value.
   * @param sub The sub-computation that produces a value of type {@code A}. Must not be {@code
   *     null}.
   * @param f The function that takes the result of {@code sub} and produces the next computation.
   *     Must not be {@code null} and must not return {@code null}.
   */
  record FlatMap<A, B>(Trampoline<A> sub, Function<A, Trampoline<B>> f) implements Trampoline<B> {
    /**
     * Compact constructor with validation.
     *
     * @param sub The sub-computation. Must not be null.
     * @param f The continuation function. Must not be null.
     * @throws NullPointerException if {@code sub} or {@code f} is {@code null}.
     */
    public FlatMap {
      Validation.coreType().requireValue(sub, TRAMPOLINE_CLASS, CONSTRUCTION);
      Validation.function().requireFunction(f, "f", TRAMPOLINE_CLASS, CONSTRUCTION);
    }
  }

  /**
   * Creates a completed {@code Trampoline} with the given value.
   *
   * @param <A> The type of the value.
   * @param value The final value. Can be {@code null} if {@code A} is a nullable type.
   * @return A {@code Trampoline} that immediately completes with the given value. Never {@code
   *     null}.
   */
  static <A> Trampoline<A> done(A value) {
    return new Done<>(value);
  }

  /**
   * Creates a suspended {@code Trampoline} that defers computation. This is the primary method for
   * converting recursive calls into trampolined form.
   *
   * <p>The supplier should produce the next step of the computation, typically a recursive call
   * wrapped in another {@code Trampoline}.
   *
   * @param <A> The type of the value that will eventually be produced.
   * @param next A supplier that produces the next {@code Trampoline} step. Must not be {@code null}
   *     and must not return {@code null}.
   * @return A {@code Trampoline} representing the deferred computation. Never {@code null}.
   * @throws NullPointerException if {@code next} is {@code null}.
   */
  static <A> Trampoline<A> defer(Supplier<Trampoline<A>> next) {
    Validation.function().requireFunction(next, "next", TRAMPOLINE_CLASS, DEFER);
    return new More<>(next);
  }

  /**
   * Transforms the result of this {@code Trampoline} computation using the provided mapping
   * function, maintaining stack safety.
   *
   * <p>The mapping function is not executed until {@link #run()} is called, and the mapping itself
   * does not increase stack depth.
   *
   * @param <B> The type of the value produced by the mapping function.
   * @param f The mapping function. Must not be {@code null}.
   * @return A new {@code Trampoline} that will apply the mapping function to the result. Never
   *     {@code null}.
   * @throws NullPointerException if {@code f} is {@code null}.
   */
  default <B> Trampoline<B> map(Function<? super A, ? extends B> f) {
    Validation.function().requireMapper(f, "f", TRAMPOLINE_CLASS, MAP);
    return flatMap(a -> done(f.apply(a)));
  }

  /**
   * Sequences this {@code Trampoline} computation with another, maintaining stack safety. This is
   * the monadic bind operation for {@code Trampoline}.
   *
   * <p>The key to stack safety is that this method does not immediately evaluate anything - it
   * constructs a {@link FlatMap} data structure that will be processed iteratively by {@link
   * #run()}.
   *
   * @param <B> The type of the value produced by the next computation.
   * @param f A function that takes the result of this computation and produces the next {@code
   *     Trampoline}. Must not be {@code null} and must not return {@code null}.
   * @return A new {@code Trampoline} representing the sequenced computation. Never {@code null}.
   * @throws NullPointerException if {@code f} is {@code null}.
   */
  default <B> Trampoline<B> flatMap(Function<? super A, ? extends Trampoline<? extends B>> f) {
    Validation.function().requireFlatMapper(f, "f", TRAMPOLINE_CLASS, FLAT_MAP);
    @SuppressWarnings("unchecked")
    Function<A, Trampoline<B>> castedF =
        a -> {
          Trampoline<? extends B> result = f.apply(a);
          Validation.function()
              .requireNonNullResult(result, "f", TRAMPOLINE_CLASS, FLAT_MAP, TRAMPOLINE_CLASS);
          @SuppressWarnings("unchecked")
          Trampoline<B> casted = (Trampoline<B>) result;
          return casted;
        };
    return new FlatMap<>(this, castedF);
  }

  /**
   * Internal sealed interface for type-safe continuation handling. This encapsulates the necessary
   * type erasure in one controlled location, avoiding multiple unsafe casts throughout the {@link
   * #run()} method.
   *
   * <p>The continuation stack in {@link #run()} stores functions with heterogeneous types (e.g.,
   * {@code Function<A, Trampoline<B>>}, {@code Function<B, Trampoline<C>>}, etc.). Rather than
   * using raw types or multiple unchecked casts, this interface provides a type-safe wrapper that
   * handles the necessary Object-level casting in a single, controlled location.
   */
  sealed interface Continuation permits Continuation.FlatMapCont {
    /**
     * Applies the continuation to a value, producing the next trampoline step.
     *
     * @param value The value from the previous computation (passed as Object for stack
     *     homogeneity).
     * @return The next trampoline step.
     */
    Trampoline<?> apply(Object value);

    /**
     * Implementation of continuation for {@link FlatMap} operations. This record wraps a flatMap
     * function and handles the necessary type cast in a controlled manner.
     *
     * <p>The single {@code @SuppressWarnings("unchecked")} annotation in the {@link #apply(Object)}
     * method is safe because:
     *
     * <ol>
     *   <li>The continuation is created in the {@code FlatMap} case with the correct type
     *   <li>It's only called with the value produced by evaluating {@code flatMap.sub()}
     *   <li>That value is guaranteed to be of type {@code X} by construction
     * </ol>
     *
     * @param <X> The input type of the function (must match the result type of the
     *     sub-computation).
     * @param <Y> The output type of the trampoline produced by the function.
     * @param f The flatMap function that transforms a value of type X into the next trampoline
     *     step.
     */
    record FlatMapCont<X, Y>(Function<X, Trampoline<Y>> f) implements Continuation {
      @Override
      @SuppressWarnings("unchecked")
      public Trampoline<Y> apply(Object value) {
        // Single controlled unsafe cast: Object -> X
        // This is safe because we only call this with the value produced by the sub-computation,
        // which is guaranteed to be of type X
        return f.apply((X) value);
      }
    }
  }

  /**
   * Evaluates this {@code Trampoline} computation in a stack-safe manner, returning the final
   * result.
   *
   * <p>This method uses an iterative algorithm with an explicit continuation stack to process the
   * trampoline structure. Regardless of how deeply nested the recursive calls were, this method
   * executes in constant stack space.
   *
   * <p><b>Algorithm:</b> The implementation uses a continuation-passing style with an explicit
   * stack (a {@link Deque}) to track pending {@link FlatMap} continuations. It processes the
   * trampoline structure iteratively:
   *
   * <ol>
   *   <li>Start with the current trampoline
   *   <li>If it's {@link More}, unwrap it and continue
   *   <li>If it's {@link FlatMap}, wrap the function in a {@link Continuation.FlatMapCont}, push it
   *       onto the stack, and process the sub-computation
   *   <li>If it's {@link Done}, apply any pending continuations from the stack
   *   <li>Repeat until there are no more continuations and we have a final {@link Done} value
   * </ol>
   *
   * <p><b>Type Safety:</b> The method uses a {@link Continuation} wrapper to safely handle the
   * heterogeneous types on the continuation stack. This design confines the single necessary unsafe
   * cast to the {@link Continuation.FlatMapCont#apply(Object)} method, making the type erasure
   * explicit, documented, and verified to be safe.
   *
   * @return The final computed value. Can be {@code null} if {@code A} is a nullable type.
   */
  default A run() {
    Trampoline<?> current = this;
    Deque<Continuation> stack = new ArrayDeque<>();

    while (true) {
      switch (current) {
        case Done<?> done -> {
          if (stack.isEmpty()) {
            // Final result: safe cast because we started with Trampoline<A>
            // and all transformations preserve the final result type
            @SuppressWarnings("unchecked")
            A result = (A) done.value();
            return result;
          }
          // Apply the next continuation from the stack
          current = stack.removeLast().apply(done.value());
        }
        case More<?> more -> {
          current = more.next().get();
        }
        case FlatMap<?, ?> flatMap -> {
          // Wrap the function in a continuation and push it onto the stack
          // The continuation captures the correct types and handles casting internally
          stack.addLast(new Continuation.FlatMapCont<>(flatMap.f()));
          current = flatMap.sub();
        }
      }
    }
  }

  /**
   * Evaluates this {@code Trampoline} and returns the result. Alias for {@link #run()}.
   *
   * @return The final computed value. Can be {@code null} if {@code A} is a nullable type.
   */
  default A runT() {
    return run();
  }
}
