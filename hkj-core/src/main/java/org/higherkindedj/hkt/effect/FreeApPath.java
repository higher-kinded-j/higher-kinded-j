// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.effect.capability.Composable;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.function.Function3;

/**
 * A fluent path wrapper for {@link FreeAp} applicative computations.
 *
 * <p>{@code FreeApPath} represents applicative computations that can be statically analysed before
 * interpretation. Unlike {@link FreePath}, FreeApPath does not support monadic bind (via), only
 * applicative operations (map, zipWith).
 *
 * <h2>Use Cases</h2>
 *
 * <ul>
 *   <li>Form validation with all errors collected
 *   <li>Query builders with static analysis
 *   <li>Dependency graphs
 *   <li>Parallel-by-default execution
 * </ul>
 *
 * <h2>Key Differences from FreePath</h2>
 *
 * <p>FreeApPath is limited to applicative operations, which means:
 *
 * <ul>
 *   <li>All operations are independent - no operation depends on another's result
 *   <li>The structure can be fully analyzed before interpretation
 *   <li>Interpreters can safely parallelize independent operations
 *   <li>Multiple validation errors can be collected rather than failing on the first
 * </ul>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * // Validation DSL - all validations run, all errors collected
 * FreeApPath<ValidationF.Witness, User> validateUser =
 *     FreeApPath.liftF(validateName(input.name()), validationFunctor)
 *         .zipWith3(
 *             FreeApPath.liftF(validateEmail(input.email()), validationFunctor),
 *             FreeApPath.liftF(validateAge(input.age()), validationFunctor),
 *             User::new
 *         );
 *
 * // Interpret - collects ALL validation errors
 * Kind<Validated.Witness<List<Error>>, User> result =
 *     validateUser.foldMapKind(interpreter, validationApplicative);
 * }</pre>
 *
 * @param <F> the functor witness type
 * @param <A> the result type
 */
public final class FreeApPath<F extends WitnessArity<TypeArity.Unary>, A>
    implements Composable<A>, Combinable<A> {

  private final FreeAp<F, A> freeAp;
  private final Functor<F> functor;

  private FreeApPath(FreeAp<F, A> freeAp, Functor<F> functor) {
    this.freeAp = Objects.requireNonNull(freeAp, "freeAp must not be null");
    this.functor = Objects.requireNonNull(functor, "functor must not be null");
  }

  // ===== Factory Methods =====

  /**
   * Creates a FreeApPath containing a pure value.
   *
   * @param value the value to wrap
   * @param functor the Functor instance for F; must not be null
   * @param <F> the functor witness type
   * @param <A> the value type
   * @return a FreeApPath containing the value
   * @throws NullPointerException if functor is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> FreeApPath<F, A> pure(
      A value, Functor<F> functor) {
    Objects.requireNonNull(functor, "functor must not be null");
    return new FreeApPath<>(FreeAp.pure(value), functor);
  }

  /**
   * Lifts a functor value into FreeApPath.
   *
   * <p>This is the primary way to create DSL instructions. The functor value represents a single
   * independent operation in your DSL.
   *
   * @param fa the functor value to lift; must not be null
   * @param functor the Functor instance for F; must not be null
   * @param <F> the functor witness type
   * @param <A> the result type
   * @return a FreeApPath containing the lifted instruction
   * @throws NullPointerException if fa or functor is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> FreeApPath<F, A> liftF(
      Kind<F, A> fa, Functor<F> functor) {
    Objects.requireNonNull(fa, "fa must not be null");
    Objects.requireNonNull(functor, "functor must not be null");
    return new FreeApPath<>(FreeAp.lift(fa), functor);
  }

  /**
   * Creates a FreeApPath from an existing FreeAp.
   *
   * @param freeAp the FreeAp to wrap; must not be null
   * @param functor the Functor instance for F; must not be null
   * @param <F> the functor witness type
   * @param <A> the value type
   * @return a FreeApPath wrapping the FreeAp
   * @throws NullPointerException if freeAp or functor is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> FreeApPath<F, A> of(
      FreeAp<F, A> freeAp, Functor<F> functor) {
    return new FreeApPath<>(freeAp, functor);
  }

  // ===== Interpretation =====

  /**
   * Interprets this FreeApPath into a target monad using a natural transformation.
   *
   * <p>This method returns a GenericPath, which requires a Monad instance. If you only have an
   * Applicative, use {@link #foldMapKind(Natural, Applicative)} instead.
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
    Kind<G, A> result = freeAp.foldMap(interpreter, targetMonad);
    return GenericPath.of(result, targetMonad);
  }

  /**
   * Interprets this FreeApPath into a target applicative, returning raw Kind.
   *
   * <p>Use this method when the target type is only Applicative (not Monad), or when you don't need
   * the GenericPath wrapper.
   *
   * @param interpreter natural transformation from F to G; must not be null
   * @param targetApplicative the target applicative instance; must not be null
   * @param <G> the target applicative witness type
   * @return the interpreted result as a Kind
   * @throws NullPointerException if interpreter or targetApplicative is null
   */
  public <G extends WitnessArity<TypeArity.Unary>> Kind<G, A> foldMapKind(
      Natural<F, G> interpreter, Applicative<G> targetApplicative) {
    Objects.requireNonNull(interpreter, "interpreter must not be null");
    Objects.requireNonNull(targetApplicative, "targetApplicative must not be null");
    return freeAp.foldMap(interpreter, targetApplicative);
  }

  /**
   * Interprets using the effect package's NaturalTransformation.
   *
   * @param interpreter natural transformation from F to G; must not be null
   * @param targetApplicative the target applicative instance; must not be null
   * @param <G> the target applicative witness type
   * @return the interpreted result as a Kind
   * @throws NullPointerException if interpreter or targetApplicative is null
   */
  public <G extends WitnessArity<TypeArity.Unary>> Kind<G, A> foldMapWith(
      NaturalTransformation<F, G> interpreter, Applicative<G> targetApplicative) {
    Objects.requireNonNull(interpreter, "interpreter must not be null");
    Objects.requireNonNull(targetApplicative, "targetApplicative must not be null");

    // Adapt NaturalTransformation to Natural
    Natural<F, G> adapted = interpreter::apply;
    return freeAp.foldMap(adapted, targetApplicative);
  }

  /**
   * Returns the underlying FreeAp.
   *
   * @return the wrapped FreeAp
   */
  public FreeAp<F, A> toFreeAp() {
    return freeAp;
  }

  /**
   * Returns the Functor instance for this FreeApPath.
   *
   * @return the Functor instance
   */
  public Functor<F> functor() {
    return functor;
  }

  // ===== Composable Implementation =====

  @Override
  public <B> FreeApPath<F, B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new FreeApPath<>(freeAp.map(mapper), functor);
  }

  @Override
  public FreeApPath<F, A> peek(Consumer<? super A> consumer) {
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
  public <B, C> FreeApPath<F, C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof FreeApPath<?, ?> otherFreeAp)) {
      throw new IllegalArgumentException(
          "FreeApPath.zipWith requires FreeApPath. Got: " + other.getClass());
    }

    FreeApPath<F, B> typedOther = (FreeApPath<F, B>) otherFreeAp;
    FreeAp<F, C> combined = freeAp.map2(typedOther.freeAp, combiner);
    return new FreeApPath<>(combined, functor);
  }

  /**
   * Combines three FreeApPaths using a ternary function.
   *
   * <p>All three computations are independent and can be executed in parallel.
   *
   * @param second the second FreeApPath; must not be null
   * @param third the third FreeApPath; must not be null
   * @param combiner the function to combine the three values; must not be null
   * @param <B> the type of the second value
   * @param <C> the type of the third value
   * @param <D> the type of the combined result
   * @return a FreeApPath containing the combined result
   * @throws NullPointerException if any argument is null
   */
  public <B, C, D> FreeApPath<F, D> zipWith3(
      FreeApPath<F, B> second,
      FreeApPath<F, C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    return this.zipWith(second, (a, b) -> (Function<C, D>) c -> combiner.apply(a, b, c))
        .zipWith(third, Function::apply);
  }

  // ===== Object Methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof FreeApPath<?, ?> other)) return false;
    return freeAp.equals(other.freeAp);
  }

  @Override
  public int hashCode() {
    return freeAp.hashCode();
  }

  @Override
  public String toString() {
    return "FreeApPath(" + freeAp + ")";
  }
}
