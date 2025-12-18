// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.lazy.ThrowableSupplier;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;

/**
 * A fluent path wrapper for {@link Lazy} computations.
 *
 * <p>{@code LazyPath} represents deferred computations that are evaluated at most once. Once
 * evaluated, the result is cached and reused on subsequent accesses.
 *
 * <h2>Use Cases</h2>
 *
 * <ul>
 *   <li>Expensive computations that may not be needed
 *   <li>Breaking circular dependencies
 *   <li>Infinite data structures
 *   <li>Memoisation
 * </ul>
 *
 * <h2>Creating LazyPath instances</h2>
 *
 * <pre>{@code
 * // Deferred computation
 * LazyPath<BigInteger> expensiveCalc = LazyPath.defer(() -> computeFibonacci(1000));
 *
 * // Already-evaluated value
 * LazyPath<String> eager = LazyPath.now("hello");
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <pre>{@code
 * LazyPath<BigInteger> fibonacci1000 = LazyPath.defer(() -> computeFib(1000));
 *
 * // Transformations are also lazy
 * LazyPath<String> asString = fibonacci1000.map(BigInteger::toString);
 *
 * // Not computed yet
 * System.out.println("About to force...");
 *
 * // Now it's computed (and cached)
 * String result = asString.get();
 *
 * // Second call returns cached value (no recomputation)
 * String result2 = asString.get();
 * }</pre>
 *
 * <h2>Exception Handling</h2>
 *
 * <p>Unlike most path types, {@code LazyPath} can throw exceptions when evaluated. The {@link
 * #get()} method wraps checked exceptions in {@link RuntimeException}, while {@link #force()}
 * throws them directly.
 *
 * @param <A> the type of the computed value
 */
public final class LazyPath<A> implements Chainable<A> {

  private final Lazy<A> lazy;

  /**
   * Creates a new LazyPath wrapping the given Lazy.
   *
   * @param lazy the Lazy to wrap; must not be null
   */
  LazyPath(Lazy<A> lazy) {
    this.lazy = Objects.requireNonNull(lazy, "lazy must not be null");
  }

  // ===== Factory Methods =====

  /**
   * Creates a LazyPath from a Lazy.
   *
   * @param lazy the Lazy to wrap; must not be null
   * @param <A> the value type
   * @return a LazyPath wrapping the given Lazy
   * @throws NullPointerException if lazy is null
   */
  public static <A> LazyPath<A> of(Lazy<A> lazy) {
    return new LazyPath<>(lazy);
  }

  /**
   * Creates an already-evaluated LazyPath.
   *
   * <p>The value is immediately available without any computation.
   *
   * @param value the already-computed value
   * @param <A> the value type
   * @return a LazyPath holding the pre-computed value
   */
  public static <A> LazyPath<A> now(A value) {
    return new LazyPath<>(Lazy.now(value));
  }

  /**
   * Creates a LazyPath that defers computation until first access.
   *
   * <p>The supplier will be called at most once when the value is first requested.
   *
   * @param supplier the supplier for the value; must not be null
   * @param <A> the value type
   * @return a LazyPath that defers computation
   * @throws NullPointerException if supplier is null
   */
  public static <A> LazyPath<A> defer(Supplier<? extends A> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return new LazyPath<>(Lazy.defer(() -> supplier.get()));
  }

  /**
   * Creates a LazyPath that defers computation which may throw.
   *
   * <p>The supplier will be called at most once when the value is first requested.
   *
   * @param supplier the supplier for the value; must not be null
   * @param <A> the value type
   * @return a LazyPath that defers computation
   * @throws NullPointerException if supplier is null
   */
  public static <A> LazyPath<A> deferThrowable(ThrowableSupplier<? extends A> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return new LazyPath<>(Lazy.defer(supplier));
  }

  // ===== Terminal Operations =====

  /**
   * Forces evaluation and returns the result.
   *
   * <p>Subsequent calls return the cached value without recomputation. If the computation throws an
   * exception, it will be cached and re-thrown on subsequent calls.
   *
   * @return the computed value
   * @throws RuntimeException wrapping any exception thrown by the computation
   */
  public A get() {
    try {
      return lazy.force();
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new RuntimeException("LazyPath computation failed", t);
    }
  }

  /**
   * Forces evaluation and returns the result, allowing checked exceptions.
   *
   * <p>This method provides direct access to exceptions thrown by the computation.
   *
   * @return the computed value
   * @throws Throwable if the computation throws
   */
  public A force() throws Throwable {
    return lazy.force();
  }

  /**
   * Returns whether this lazy value has been evaluated.
   *
   * @return true if already evaluated, false if still deferred
   */
  public boolean isEvaluated() {
    return lazy.isEvaluated();
  }

  /**
   * Returns the underlying Lazy.
   *
   * @return the wrapped Lazy
   */
  public Lazy<A> toLazy() {
    return lazy;
  }

  // ===== Composable implementation =====

  @Override
  public <B> LazyPath<B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new LazyPath<>(lazy.map(mapper));
  }

  @Override
  public LazyPath<A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    return new LazyPath<>(
        lazy.map(
            a -> {
              consumer.accept(a);
              return a;
            }));
  }

  // ===== Combinable implementation =====

  @Override
  public <B, C> LazyPath<C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof LazyPath<?> otherLazy)) {
      throw new IllegalArgumentException("Cannot zipWith non-LazyPath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    LazyPath<B> typedOther = (LazyPath<B>) otherLazy;

    return new LazyPath<>(
        Lazy.defer(
            () -> {
              A a = this.lazy.force();
              B b = typedOther.lazy.force();
              return combiner.apply(a, b);
            }));
  }

  /**
   * Combines this path with two others using a ternary function.
   *
   * <p>All three lazy values are evaluated when the result is forced.
   *
   * @param second the second path; must not be null
   * @param third the third path; must not be null
   * @param combiner the function to combine the values; must not be null
   * @param <B> the type of the second path's value
   * @param <C> the type of the third path's value
   * @param <D> the type of the combined result
   * @return a new lazy path containing the combined result
   */
  public <B, C, D> LazyPath<D> zipWith3(
      LazyPath<B> second,
      LazyPath<C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    return new LazyPath<>(
        Lazy.defer(
            () -> {
              A a = this.lazy.force();
              B b = second.lazy.force();
              C c = third.lazy.force();
              return combiner.apply(a, b, c);
            }));
  }

  // ===== Chainable implementation =====

  @Override
  public <B> LazyPath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    return new LazyPath<>(
        lazy.flatMap(
            a -> {
              Chainable<B> result = mapper.apply(a);
              Objects.requireNonNull(result, "mapper must not return null");

              if (!(result instanceof LazyPath<?> lazyPath)) {
                throw new IllegalArgumentException(
                    "via mapper must return LazyPath, got: " + result.getClass());
              }

              @SuppressWarnings("unchecked")
              LazyPath<B> typedResult = (LazyPath<B>) lazyPath;
              return typedResult.lazy;
            }));
  }

  @Override
  public <B> LazyPath<B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    return new LazyPath<>(
        Lazy.defer(
            () -> {
              // Force this lazy for sequencing
              this.lazy.force();

              Chainable<B> result = supplier.get();
              Objects.requireNonNull(result, "supplier must not return null");

              if (!(result instanceof LazyPath<?> lazyPath)) {
                throw new IllegalArgumentException(
                    "then supplier must return LazyPath, got: " + result.getClass());
              }

              @SuppressWarnings("unchecked")
              LazyPath<B> typedResult = (LazyPath<B>) lazyPath;
              return typedResult.lazy.force();
            }));
  }

  // ===== Conversions =====

  /**
   * Converts to an IOPath.
   *
   * <p>The IO will force evaluation when run.
   *
   * @return an IOPath that produces the same value
   */
  public IOPath<A> toIOPath() {
    return new IOPath<>(this::get);
  }

  /**
   * Converts to a MaybePath.
   *
   * <p>Forces evaluation. If the value is null, returns an empty MaybePath.
   *
   * @return a MaybePath containing the value if non-null
   */
  public MaybePath<A> toMaybePath() {
    A val = get();
    return val != null ? new MaybePath<>(Maybe.just(val)) : new MaybePath<>(Maybe.nothing());
  }

  /**
   * Converts to a TryPath.
   *
   * <p>Forces evaluation. If the computation throws, the exception is captured in the TryPath.
   *
   * @return a TryPath containing Success if computation succeeds, Failure otherwise
   */
  public TryPath<A> toTryPath() {
    try {
      return new TryPath<>(Try.success(lazy.force()));
    } catch (Throwable t) {
      return new TryPath<>(Try.failure(t));
    }
  }

  /**
   * Converts to an IdPath.
   *
   * <p>Forces evaluation.
   *
   * @return an IdPath containing the value
   */
  public IdPath<A> toIdPath() {
    return new IdPath<>(Id.of(get()));
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof LazyPath<?> other)) return false;
    return lazy.equals(other.lazy);
  }

  @Override
  public int hashCode() {
    return lazy.hashCode();
  }

  @Override
  public String toString() {
    return "LazyPath(" + lazy + ")";
  }
}
