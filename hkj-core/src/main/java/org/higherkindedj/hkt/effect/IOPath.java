// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.effect.capability.Effectful;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.io.IO;

/**
 * A fluent path wrapper for {@link IO} values.
 *
 * <p>{@code IOPath} provides a chainable API for composing deferred side-effecting computations. It
 * implements {@link Effectful} to provide methods for executing the deferred computation.
 *
 * <h2>Creating IOPath instances</h2>
 *
 * <p>Use the {@link Path} factory class to create instances:
 *
 * <pre>{@code
 * IOPath<String> path = Path.io(() -> Files.readString(file));
 * IOPath<Unit> action = Path.ioRunnable(() -> System.out.println("Hello"));
 * IOPath<Integer> pure = Path.ioPure(42);
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <p>IOPath operations are lazy - they describe a computation but don't execute it until {@link
 * #unsafeRun()} or {@link #runSafe()} is called.
 *
 * <pre>{@code
 * IOPath<Config> config = Path.io(() -> readConfigFile())
 *     .map(Config::parse)
 *     .via(c -> Path.io(() -> validate(c)));
 *
 * // Nothing has happened yet!
 * Config result = config.unsafeRun();  // Now the computation runs
 * }</pre>
 *
 * <h2>Executing the computation</h2>
 *
 * <pre>{@code
 * // Unsafe - exceptions propagate
 * String content = Path.io(() -> Files.readString(path)).unsafeRun();
 *
 * // Safe - exceptions are captured
 * Try<String> result = Path.io(() -> Files.readString(path)).runSafe();
 * }</pre>
 *
 * @param <A> the type of the value produced by the computation
 */
public final class IOPath<A> implements Effectful<A> {

  private final IO<A> value;

  /**
   * Creates a new IOPath wrapping the given IO.
   *
   * @param value the IO to wrap; must not be null
   */
  IOPath(IO<A> value) {
    this.value = Objects.requireNonNull(value, "value must not be null");
  }

  /**
   * Returns the underlying IO value.
   *
   * @return the wrapped IO
   */
  public IO<A> run() {
    return value;
  }

  @Override
  public A unsafeRun() {
    return value.unsafeRunSync();
  }

  // runSafe() uses the default implementation from Effectful interface

  /**
   * Converts the result of this IOPath to Unit, discarding any value.
   *
   * <p>Useful when you only care about the side effect, not the result.
   *
   * @return an IOPath that produces Unit
   */
  public IOPath<Unit> asUnit() {
    return new IOPath<>(value.asUnit());
  }

  /**
   * Converts this IOPath to a TryPath by executing it safely.
   *
   * <p><b>Note:</b> This executes the IO immediately to capture success or failure.
   *
   * @return a TryPath containing the result or exception
   */
  public TryPath<A> toTryPath() {
    return new TryPath<>(runSafe());
  }

  // ===== Composable implementation =====

  @Override
  public <B> IOPath<B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new IOPath<>(value.map(mapper));
  }

  @Override
  public IOPath<A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    return new IOPath<>(
        value.map(
            a -> {
              consumer.accept(a);
              return a;
            }));
  }

  // ===== Combinable implementation =====

  @Override
  public <B, C> IOPath<C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof IOPath<?> otherIO)) {
      throw new IllegalArgumentException("Cannot zipWith non-IOPath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    IOPath<B> typedOther = (IOPath<B>) otherIO;

    return new IOPath<>(
        IO.delay(
            () -> {
              A a = this.value.unsafeRunSync();
              B b = typedOther.value.unsafeRunSync();
              return combiner.apply(a, b);
            }));
  }

  /**
   * Combines this path with two others using a ternary function.
   *
   * @param second the second path; must not be null
   * @param third the third path; must not be null
   * @param combiner the function to combine the values; must not be null
   * @param <B> the type of the second path's value
   * @param <C> the type of the third path's value
   * @param <D> the type of the combined result
   * @return a new path containing the combined result
   */
  public <B, C, D> IOPath<D> zipWith3(
      IOPath<B> second,
      IOPath<C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    return new IOPath<>(
        IO.delay(
            () -> {
              A a = this.value.unsafeRunSync();
              B b = second.value.unsafeRunSync();
              C c = third.value.unsafeRunSync();
              return combiner.apply(a, b, c);
            }));
  }

  // ===== Chainable implementation =====

  @Override
  public <B> IOPath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    return new IOPath<>(
        IO.delay(
            () -> {
              A a = this.value.unsafeRunSync();
              Chainable<B> result = mapper.apply(a);
              Objects.requireNonNull(result, "mapper must not return null");

              if (!(result instanceof IOPath<?> ioPath)) {
                throw new IllegalArgumentException(
                    "via mapper must return IOPath, got: " + result.getClass());
              }

              @SuppressWarnings("unchecked")
              IOPath<B> typedResult = (IOPath<B>) ioPath;
              return typedResult.unsafeRun();
            }));
  }

  @Override
  public <B> IOPath<B> flatMap(Function<? super A, ? extends Chainable<B>> mapper) {
    return via(mapper);
  }

  @Override
  public <B> IOPath<B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    return new IOPath<>(
        IO.delay(
            () -> {
              // Execute this IO for its side effects
              this.value.unsafeRunSync();

              Chainable<B> result = supplier.get();
              Objects.requireNonNull(result, "supplier must not return null");

              if (!(result instanceof IOPath<?> ioPath)) {
                throw new IllegalArgumentException(
                    "then supplier must return IOPath, got: " + result.getClass());
              }

              @SuppressWarnings("unchecked")
              IOPath<B> typedResult = (IOPath<B>) ioPath;
              return typedResult.unsafeRun();
            }));
  }

  /**
   * Handles exceptions that occur during execution.
   *
   * <p>If an exception is thrown during execution, the recovery function is applied to produce an
   * alternative value.
   *
   * @param recovery the function to apply if an exception occurs; must not be null
   * @return an IOPath that will recover from exceptions
   * @throws NullPointerException if recovery is null
   */
  public IOPath<A> handleError(Function<? super Throwable, ? extends A> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    return new IOPath<>(
        IO.delay(
            () -> {
              try {
                return this.value.unsafeRunSync();
              } catch (Throwable t) {
                return recovery.apply(t);
              }
            }));
  }

  /**
   * Handles exceptions that occur during execution with a recovery IO.
   *
   * <p>If an exception is thrown during execution, the recovery function is applied to produce an
   * alternative IOPath.
   *
   * @param recovery the function to apply if an exception occurs; must not be null
   * @return an IOPath that will recover from exceptions
   * @throws NullPointerException if recovery is null
   */
  public IOPath<A> handleErrorWith(Function<? super Throwable, ? extends IOPath<A>> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    return new IOPath<>(
        IO.delay(
            () -> {
              try {
                return this.value.unsafeRunSync();
              } catch (Throwable t) {
                IOPath<A> fallback = recovery.apply(t);
                Objects.requireNonNull(fallback, "recovery must not return null");
                return fallback.unsafeRun();
              }
            }));
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    // IO equality is based on reference since IO represents a computation
    return this == obj;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String toString() {
    return "IOPath(<deferred>)";
  }
}
