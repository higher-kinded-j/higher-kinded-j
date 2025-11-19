// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.constant;

import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.jspecify.annotations.Nullable;

/**
 * The {@link Applicative} instance for {@link Const} with a monoidal first parameter.
 *
 * <p>This applicative accumulates values of type {@code M} using a {@link Monoid}, whilst ignoring
 * the phantom second parameter {@code A}. This makes it perfect for implementing efficient folds
 * over traversable structures without creating intermediate lists.
 *
 * <p>The {@code Const} applicative is widely used in functional programming for:
 *
 * <ul>
 *   <li>Implementing {@code foldMap} operations over complex structures
 *   <li>Converting traversals to folds efficiently
 *   <li>Aggregating values whilst traversing data structures
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Sum all integers in a structure
 * Monoid<Integer> sumMonoid = Monoid.of(0, Integer::sum);
 * ConstApplicative<Integer> constApp = new ConstApplicative<>(sumMonoid);
 *
 * // Accumulate values
 * Const<Integer, String> c1 = new Const<>(5);
 * Const<Integer, String> c2 = new Const<>(10);
 *
 * Kind<ConstKind2.Witness<Integer>, String> result =
 *     constApp.map2(
 *         CONST.widen2(c1),
 *         CONST.widen2(c2),
 *         (a, b) -> "ignored"  // The phantom type is never used
 *     );
 *
 * // Extract accumulated result
 * Const<Integer, String> finalConst = CONST.narrow2(result);
 * int sum = finalConst.value();  // 15
 * }</pre>
 *
 * @param <M> The monoidal type being accumulated
 */
public final class ConstApplicative<M> implements Applicative<ConstKind2.Witness<M>> {

  private final Monoid<M> monoid;

  /**
   * Creates a new {@code ConstApplicative} with the given {@link Monoid}.
   *
   * @param monoid The monoid used to combine accumulated values. Must not be null.
   * @throws NullPointerException if {@code monoid} is null.
   */
  public ConstApplicative(Monoid<M> monoid) {
    if (monoid == null) {
      throw new NullPointerException("Monoid cannot be null");
    }
    this.monoid = monoid;
  }

  /**
   * Lifts a phantom value into a {@code Const} containing the monoid's empty element.
   *
   * <p>Since {@code Const} ignores its second type parameter, the input value {@code a} is
   * discarded, and the result contains only the monoid's identity element.
   *
   * @param a The phantom value (ignored). Can be null.
   * @param <A> The phantom type parameter.
   * @return A {@code Const<M, A>} containing the monoid's empty element. Never null.
   */
  @Override
  public <A> Kind<ConstKind2.Witness<M>, A> of(@Nullable A a) {
    return CONST.widen2(new Const<>(monoid.empty()));
  }

  /**
   * Maps a function over a {@code Const}, which has no effect on the accumulated value.
   *
   * <p>Since {@code Const} ignores its second type parameter, the mapping function is never
   * applied. The accumulated monoidal value passes through unchanged.
   *
   * @param f The mapping function (never applied). Must not be null.
   * @param fa The {@code Const<M, A>} to map over. Must not be null.
   * @param <A> The input phantom type.
   * @param <B> The output phantom type.
   * @return A {@code Const<M, B>} with the same accumulated value. Never null.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <A, B> Kind<ConstKind2.Witness<M>, B> map(
      Function<? super A, ? extends B> f, Kind<ConstKind2.Witness<M>, A> fa) {
    if (f == null) {
      throw new NullPointerException("Function cannot be null");
    }
    if (fa == null) {
      throw new NullPointerException("Kind cannot be null");
    }

    // Since A is phantom in Const<M, A>, we can safely change the type parameter
    // The actual value (type M) remains unchanged
    Const<M, A> constA = CONST.narrow2(fa);
    return (Kind<ConstKind2.Witness<M>, B>) CONST.widen2(new Const<>(constA.value()));
  }

  /**
   * Combines two {@code Const} values using the monoid's combine operation.
   *
   * <p>This is the key operation that makes {@code Const} useful for folds. The accumulated
   * monoidal values are combined using {@code monoid.combine()}, whilst the phantom type parameters
   * and the combining function {@code f} are ignored.
   *
   * @param fa The first {@code Const<M, A>}. Must not be null.
   * @param fb The second {@code Const<M, B>}. Must not be null.
   * @param f The combining function (ignored, since both type parameters are phantom). Must not be
   *     null.
   * @param <A> The first phantom type.
   * @param <B> The second phantom type.
   * @param <C> The result phantom type.
   * @return A {@code Const<M, C>} containing the combined monoidal values. Never null.
   * @throws NullPointerException if any parameter is null.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <A, B, C> Kind<ConstKind2.Witness<M>, C> map2(
      Kind<ConstKind2.Witness<M>, A> fa,
      Kind<ConstKind2.Witness<M>, B> fb,
      java.util.function.BiFunction<? super A, ? super B, ? extends C> f) {
    if (fa == null) {
      throw new NullPointerException("First Kind cannot be null");
    }
    if (fb == null) {
      throw new NullPointerException("Second Kind cannot be null");
    }
    if (f == null) {
      throw new NullPointerException("Function cannot be null");
    }

    Const<M, A> constA = CONST.narrow2(fa);
    Const<M, B> constB = CONST.narrow2(fb);

    // Combine the accumulated monoidal values
    M combined = monoid.combine(constA.value(), constB.value());

    return (Kind<ConstKind2.Witness<M>, C>) CONST.widen2(new Const<>(combined));
  }
}
