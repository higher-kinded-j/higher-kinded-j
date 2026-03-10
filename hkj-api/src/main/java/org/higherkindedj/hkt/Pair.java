// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * An immutable pair of two values, used as a general-purpose product type throughout the HKT
 * framework.
 *
 * <p>This type is used by {@link MonadWriter} for operations like {@code listen()} which return a
 * value paired with accumulated output, and by {@code WriterT} for its internal representation.
 *
 * <p>As a {@link java.lang.Record}, it automatically provides a canonical constructor, accessors
 * ({@code first()}, {@code second()}), and implementations for {@code equals()}, {@code
 * hashCode()}, and {@code toString()}.
 *
 * @param <A> The type of the first element.
 * @param <B> The type of the second element.
 * @param first The first element of the pair.
 * @param second The second element of the pair.
 */
@NullMarked
public record Pair<A, B>(A first, B second) {

  /**
   * Creates a pair from two values.
   *
   * @param first The first element.
   * @param second The second element.
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @return A new pair.
   */
  public static <A, B> Pair<A, B> of(A first, B second) {
    return new Pair<>(first, second);
  }

  /**
   * Transforms the first element of this pair using the provided function.
   *
   * @param f The function to apply to the first element.
   * @param <C> The type of the first element in the resulting pair.
   * @return A new pair with the first element transformed.
   */
  public <C> Pair<C, B> mapFirst(Function<? super A, ? extends C> f) {
    return new Pair<>(f.apply(first), second);
  }

  /**
   * Transforms the second element of this pair using the provided function.
   *
   * @param f The function to apply to the second element.
   * @param <C> The type of the second element in the resulting pair.
   * @return A new pair with the second element transformed.
   */
  public <C> Pair<A, C> mapSecond(Function<? super B, ? extends C> f) {
    return new Pair<>(first, f.apply(second));
  }

  /**
   * Transforms both elements of this pair using the provided functions.
   *
   * @param f The function to apply to the first element.
   * @param g The function to apply to the second element.
   * @param <C> The type of the first element in the resulting pair.
   * @param <D> The type of the second element in the resulting pair.
   * @return A new pair with both elements transformed.
   */
  public <C, D> Pair<C, D> bimap(
      Function<? super A, ? extends C> f, Function<? super B, ? extends D> g) {
    return new Pair<>(f.apply(first), g.apply(second));
  }
}
