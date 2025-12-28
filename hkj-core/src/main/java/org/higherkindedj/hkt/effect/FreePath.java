// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.free.Free;

/**
 * A fluent path wrapper for {@link Free} monad computations.
 *
 * <p>{@code FreePath} represents computations built from a functor {@code F} that can be
 * interpreted into any monad. This is the foundation for building domain-specific languages (DSLs)
 * with deferred interpretation.
 *
 * <h2>Use Cases</h2>
 *
 * <ul>
 *   <li>Building embedded DSLs
 *   <li>Separating program description from execution
 *   <li>Testing with mock interpreters
 *   <li>Multiple interpretation strategies
 * </ul>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * // Define a simple Console DSL
 * sealed interface ConsoleF<A> {
 *     record PrintLine<A>(String line, A next) implements ConsoleF<A> {}
 *     record ReadLine<A>(Function<String, A> cont) implements ConsoleF<A> {}
 * }
 *
 * // Build a program
 * FreePath<ConsoleF.Witness, String> program =
 *     FreePath.liftF(new PrintLine<>("Enter name:", ()), consoleFunctor)
 *         .then(() -> FreePath.liftF(new ReadLine<>(name -> name), consoleFunctor))
 *         .via(name -> FreePath.liftF(new PrintLine<>("Hello " + name, name), consoleFunctor));
 *
 * // Interpret to IO using a natural transformation
 * GenericPath<IO.Witness, String> result = program.foldMap(consoleInterpreter, IOMonad.INSTANCE);
 * }</pre>
 *
 * @param <F> the functor witness type for the DSL
 * @param <A> the result type
 */
public final class FreePath<F extends WitnessArity<TypeArity.Unary>, A> implements Chainable<A> {

  private final Free<F, A> free;
  private final Functor<F> functor;

  private FreePath(Free<F, A> free, Functor<F> functor) {
    this.free = Objects.requireNonNull(free, "free must not be null");
    this.functor = Objects.requireNonNull(functor, "functor must not be null");
  }

  // ===== Factory Methods =====

  /**
   * Creates a FreePath containing a pure value.
   *
   * @param value the value to wrap
   * @param functor the Functor instance for F; must not be null
   * @param <F> the functor witness type
   * @param <A> the value type
   * @return a FreePath containing the value
   * @throws NullPointerException if functor is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> FreePath<F, A> pure(
      A value, Functor<F> functor) {
    Objects.requireNonNull(functor, "functor must not be null");
    return new FreePath<>(Free.pure(value), functor);
  }

  /**
   * Lifts a functor value into FreePath.
   *
   * <p>This is the primary way to create DSL instructions. The functor value represents a single
   * operation in your DSL.
   *
   * @param fa the functor value to lift; must not be null
   * @param functor the Functor instance for F; must not be null
   * @param <F> the functor witness type
   * @param <A> the result type
   * @return a FreePath containing the lifted instruction
   * @throws NullPointerException if fa or functor is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> FreePath<F, A> liftF(
      Kind<F, A> fa, Functor<F> functor) {
    Objects.requireNonNull(fa, "fa must not be null");
    Objects.requireNonNull(functor, "functor must not be null");
    return new FreePath<>(Free.liftF(fa, functor), functor);
  }

  /**
   * Creates a FreePath from an existing Free monad.
   *
   * @param free the Free monad to wrap; must not be null
   * @param functor the Functor instance for F; must not be null
   * @param <F> the functor witness type
   * @param <A> the value type
   * @return a FreePath wrapping the Free monad
   * @throws NullPointerException if free or functor is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> FreePath<F, A> of(
      Free<F, A> free, Functor<F> functor) {
    return new FreePath<>(free, functor);
  }

  // ===== Interpretation =====

  /**
   * Interprets this FreePath into a target monad using a natural transformation.
   *
   * <p>The natural transformation converts each instruction in F to the target monad G. This is
   * stack-safe and uses trampolining internally.
   *
   * @param interpreter natural transformation from F to G; must not be null
   * @param targetMonad the target monad instance; must not be null
   * @param <G> the target monad witness type
   * @return a GenericPath containing the interpreted result
   * @throws NullPointerException if interpreter or targetMonad is null
   */
  public <G extends WitnessArity<TypeArity.Unary>> GenericPath<G, A> foldMap(
      Natural<F, G> interpreter, Monad<G> targetMonad) {
    Objects.requireNonNull(interpreter, "interpreter must not be null");
    Objects.requireNonNull(targetMonad, "targetMonad must not be null");
    Kind<G, A> result = free.foldMap(interpreter, targetMonad);
    return GenericPath.of(result, targetMonad);
  }

  /**
   * Interprets this FreePath using the effect package's NaturalTransformation.
   *
   * <p>This method adapts the effect package's NaturalTransformation to work with Free's foldMap.
   *
   * @param interpreter natural transformation from F to G; must not be null
   * @param targetMonad the target monad instance; must not be null
   * @param <G> the target monad witness type
   * @return a GenericPath containing the interpreted result
   * @throws NullPointerException if interpreter or targetMonad is null
   */
  public <G extends WitnessArity<TypeArity.Unary>> GenericPath<G, A> foldMapWith(
      NaturalTransformation<F, G> interpreter, Monad<G> targetMonad) {
    Objects.requireNonNull(interpreter, "interpreter must not be null");
    Objects.requireNonNull(targetMonad, "targetMonad must not be null");

    // Adapt NaturalTransformation to Natural
    Natural<F, G> adapted = interpreter::apply;
    Kind<G, A> result = free.foldMap(adapted, targetMonad);
    return GenericPath.of(result, targetMonad);
  }

  /**
   * Returns the underlying Free monad.
   *
   * @return the wrapped Free monad
   */
  public Free<F, A> toFree() {
    return free;
  }

  /**
   * Returns the Functor instance for this FreePath.
   *
   * @return the Functor instance
   */
  public Functor<F> functor() {
    return functor;
  }

  // ===== Composable Implementation =====

  @Override
  public <B> FreePath<F, B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new FreePath<>(free.map(mapper), functor);
  }

  @Override
  public FreePath<F, A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    return map(
        a -> {
          consumer.accept(a);
          return a;
        });
  }

  // ===== Combinable Implementation =====

  @Override
  @SuppressWarnings("unchecked")
  public <B, C> FreePath<F, C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof FreePath<?, ?> otherFree)) {
      throw new IllegalArgumentException("Cannot zipWith non-FreePath: " + other.getClass());
    }

    FreePath<F, B> typedOther = (FreePath<F, B>) otherFree;
    return via(a -> typedOther.map(b -> combiner.apply(a, b)));
  }

  // ===== Chainable Implementation =====

  @Override
  @SuppressWarnings("unchecked")
  public <B> FreePath<F, B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    Free<F, B> flatMapped =
        free.flatMap(
            a -> {
              Chainable<B> result = mapper.apply(a);
              Objects.requireNonNull(result, "mapper must not return null");

              if (!(result instanceof FreePath<?, ?> fp)) {
                throw new IllegalArgumentException(
                    "FreePath.via must return FreePath. Got: " + result.getClass());
              }
              return ((FreePath<F, B>) fp).free;
            });
    return new FreePath<>(flatMapped, functor);
  }

  @Override
  public <B> FreePath<F, B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return via(_ -> supplier.get());
  }

  // ===== Object Methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof FreePath<?, ?> other)) return false;
    return free.equals(other.free);
  }

  @Override
  public int hashCode() {
    return free.hashCode();
  }

  @Override
  public String toString() {
    return "FreePath(" + free + ")";
  }
}
