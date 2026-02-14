// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.trampoline.Trampoline;

/**
 * A fluent path wrapper for {@link Trampoline} computations.
 *
 * <p>{@code TrampolinePath} represents stack-safe recursive computations that are trampolined to
 * avoid stack overflow. This is essential for deeply recursive algorithms or processing deeply
 * nested data structures.
 *
 * <h2>Use Cases</h2>
 *
 * <ul>
 *   <li>Deeply recursive algorithms (factorial, fibonacci)
 *   <li>Processing deeply nested trees
 *   <li>Mutual recursion without stack overflow
 *   <li>Interpreter/evaluator implementations
 * </ul>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * // Stack-safe factorial
 * TrampolinePath<BigInteger> factorial(BigInteger n, BigInteger acc) {
 *     if (n.compareTo(BigInteger.ONE) <= 0) {
 *         return TrampolinePath.done(acc);
 *     }
 *     return TrampolinePath.defer(() ->
 *         factorial(n.subtract(BigInteger.ONE), n.multiply(acc)));
 * }
 *
 * BigInteger result = factorial(BigInteger.valueOf(100000), BigInteger.ONE).run();
 * }</pre>
 *
 * @param <A> the result type
 */
public final class TrampolinePath<A> implements Chainable<A> {

  private final Trampoline<A> trampoline;

  private TrampolinePath(Trampoline<A> trampoline) {
    this.trampoline = Objects.requireNonNull(trampoline, "trampoline must not be null");
  }

  // ===== Factory Methods =====

  /**
   * Creates a TrampolinePath with an immediate value.
   *
   * @param value the completed value
   * @param <A> the value type
   * @return a TrampolinePath containing the value
   */
  public static <A> TrampolinePath<A> done(A value) {
    return new TrampolinePath<>(Trampoline.done(value));
  }

  /**
   * Creates a TrampolinePath with a deferred computation.
   *
   * <p>This is the key method for achieving stack safety. Instead of making a recursive call
   * directly, wrap the call in {@code defer}.
   *
   * @param supplier supplies the next TrampolinePath step; must not be null
   * @param <A> the value type
   * @return a TrampolinePath representing the deferred computation
   * @throws NullPointerException if supplier is null
   */
  public static <A> TrampolinePath<A> defer(Supplier<TrampolinePath<A>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return new TrampolinePath<>(Trampoline.defer(() -> supplier.get().trampoline));
  }

  /**
   * Creates a TrampolinePath from an existing Trampoline.
   *
   * @param trampoline the Trampoline to wrap; must not be null
   * @param <A> the value type
   * @return a TrampolinePath wrapping the Trampoline
   * @throws NullPointerException if trampoline is null
   */
  public static <A> TrampolinePath<A> of(Trampoline<A> trampoline) {
    return new TrampolinePath<>(trampoline);
  }

  /**
   * Creates a pure TrampolinePath containing the given value.
   *
   * <p>Alias for {@link #done(Object)} for consistency with other path types.
   *
   * @param value the value
   * @param <A> the value type
   * @return a TrampolinePath containing the value
   */
  public static <A> TrampolinePath<A> pure(A value) {
    return done(value);
  }

  // ===== Terminal Operations =====

  /**
   * Runs the trampolined computation to completion.
   *
   * <p>This is stack-safe regardless of recursion depth.
   *
   * @return the computed value
   */
  public A run() {
    return trampoline.run();
  }

  /**
   * Returns the underlying Trampoline.
   *
   * @return the wrapped Trampoline
   */
  public Trampoline<A> toTrampoline() {
    return trampoline;
  }

  // ===== Composable Implementation =====

  @Override
  public <B> TrampolinePath<B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new TrampolinePath<>(trampoline.map(mapper));
  }

  @Override
  public TrampolinePath<A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    return map(
        a -> {
          consumer.accept(a);
          return a;
        });
  }

  // ===== Combinable Implementation =====

  /**
   * Combines this TrampolinePath with another using a combining function.
   *
   * @param other the other Combinable; must be a TrampolinePath
   * @param combiner the function to combine the values; must not be null
   * @param <B> the type of the other value
   * @param <C> the type of the combined result
   * @return a TrampolinePath containing the combined result
   * @throws NullPointerException if other or combiner is null
   * @throws IllegalArgumentException if other is not a TrampolinePath
   */
  @Override
  @SuppressWarnings("unchecked")
  public <B, C> TrampolinePath<C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof TrampolinePath<?> otherTrampoline)) {
      throw new IllegalArgumentException("Cannot zipWith non-TrampolinePath: " + other.getClass());
    }

    TrampolinePath<B> typedOther = (TrampolinePath<B>) otherTrampoline;
    return via(a -> typedOther.map(b -> combiner.apply(a, b)));
  }

  // ===== Chainable Implementation =====

  @Override
  @SuppressWarnings("unchecked")
  public <B> TrampolinePath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    Trampoline<B> flatMapped =
        trampoline.flatMap(
            a -> {
              Chainable<B> result = mapper.apply(a);
              Objects.requireNonNull(result, "mapper must not return null");

              if (!(result instanceof TrampolinePath<?> tp)) {
                throw new IllegalArgumentException(
                    "TrampolinePath.via must return TrampolinePath. Got: " + result.getClass());
              }
              return ((TrampolinePath<B>) tp).trampoline;
            });
    return new TrampolinePath<>(flatMapped);
  }

  @Override
  public <B> TrampolinePath<B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return via(_ -> supplier.get());
  }

  // ===== Conversions =====

  /**
   * Converts to IOPath by running the trampoline.
   *
   * <p>The IOPath will execute the trampoline when run.
   *
   * @return an IOPath that runs this trampoline
   */
  public IOPath<A> toIOPath() {
    return new IOPath<>(IO.delay(this::run));
  }

  /**
   * Converts to LazyPath.
   *
   * <p>The LazyPath will execute the trampoline when evaluated.
   *
   * @return a LazyPath that runs this trampoline
   */
  public LazyPath<A> toLazyPath() {
    return LazyPath.defer(this::run);
  }

  // ===== Object Methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof TrampolinePath<?> other)) return false;
    return trampoline.equals(other.trampoline);
  }

  @Override
  public int hashCode() {
    return trampoline.hashCode();
  }

  @Override
  public String toString() {
    return "TrampolinePath(...)";
  }
}
