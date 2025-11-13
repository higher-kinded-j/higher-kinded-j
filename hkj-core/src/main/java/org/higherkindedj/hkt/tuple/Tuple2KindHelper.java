// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Enum providing widen2/narrow2 operations for {@link Tuple2} types with the Kind2 system.
 *
 * <p>Access these operations via the singleton {@code TUPLE2}. For example: {@code
 * Tuple2KindHelper.TUPLE2.widen2(new Tuple2<>("a", 1));} Or, with static import: {@code import
 * static org.higherkindedj.hkt.tuple.Tuple2KindHelper.TUPLE2; TUPLE2.widen2(...);}
 */
public enum Tuple2KindHelper {
  TUPLE2;

  private static final Class<Tuple2> TUPLE2_CLASS = Tuple2.class;

  /**
   * Internal record implementing {@link Tuple2Kind2} to hold the concrete {@link Tuple2} instance
   * for bifunctor operations.
   *
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @param tuple The non-null, actual {@link Tuple2} instance.
   */
  record Tuple2Kind2Holder<A, B>(Tuple2<A, B> tuple) implements Tuple2Kind2<A, B> {

    public Tuple2Kind2Holder {
      Validation.kind().requireForWiden(tuple, TUPLE2_CLASS);
    }
  }

  /**
   * Widens a concrete {@code Tuple2<A, B>} instance into its Kind2 representation, {@code
   * Kind2<Tuple2Kind2.Witness, A, B>}.
   *
   * @param <A> The type of the first element of the {@code Tuple2}.
   * @param <B> The type of the second element of the {@code Tuple2}.
   * @param tuple The concrete {@code Tuple2<A, B>} instance to widen. Must not be null.
   * @return A {@code Kind2<Tuple2Kind2.Witness, A, B>} representing the wrapped {@code Tuple2}.
   *     Never null.
   * @throws NullPointerException if {@code tuple} is {@code null}.
   */
  public <A, B> Kind2<Tuple2Kind2.Witness, A, B> widen2(Tuple2<A, B> tuple) {
    return new Tuple2Kind2Holder<>(tuple);
  }

  /**
   * Narrows a {@code Kind2<Tuple2Kind2.Witness, A, B>} back to its concrete {@code Tuple2<A, B>}
   * type.
   *
   * @param <A> The type of the first element of the target {@code Tuple2}.
   * @param <B> The type of the second element of the target {@code Tuple2}.
   * @param kind The {@code Kind2<Tuple2Kind2.Witness, A, B>} instance to narrow. May be {@code
   *     null}.
   * @return The underlying {@code Tuple2<A, B>} instance. Never null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is {@code
   *     null} or not a representation of a {@code Tuple2<A,B>}.
   */
  @SuppressWarnings("unchecked")
  public <A, B> Tuple2<A, B> narrow2(@Nullable Kind2<Tuple2Kind2.Witness, A, B> kind) {
    if (kind == null) {
      throw new org.higherkindedj.hkt.exception.KindUnwrapException(
          "Cannot narrow null Kind2 for Tuple2");
    }
    if (!(kind instanceof Tuple2Kind2Holder<?, ?>)) {
      throw new org.higherkindedj.hkt.exception.KindUnwrapException(
          "Kind2 instance cannot be narrowed to Tuple2 (received: "
              + kind.getClass().getSimpleName()
              + ")");
    }
    // Safe cast due to type erasure and holder validation
    return ((Tuple2Kind2Holder<A, B>) kind).tuple();
  }
}
