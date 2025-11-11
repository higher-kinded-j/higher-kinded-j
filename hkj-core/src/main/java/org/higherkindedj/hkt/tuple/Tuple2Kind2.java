// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import org.higherkindedj.hkt.Kind2;
import org.jspecify.annotations.NullMarked;

/**
 * Wrapper for {@link Tuple2} to work with the {@link Kind2} system for bifunctor operations.
 *
 * <p>This representation treats {@link Tuple2} as a type constructor with two type parameters,
 * both of which can vary. This enables bifunctor operations where both the first and second
 * elements can be transformed independently.
 *
 * @param <A> The type of the first element
 * @param <B> The type of the second element
 * @see Tuple2
 * @see org.higherkindedj.hkt.Bifunctor
 */
@NullMarked
public final class Tuple2Kind2<A, B> implements Kind2<Tuple2Kind2.Witness, A, B> {

  /** Witness type for the Tuple2 type constructor when used as a bifunctor. */
  public static final class Witness {}

  private final Tuple2<A, B> tuple;

  Tuple2Kind2(Tuple2<A, B> tuple) {
    this.tuple = tuple;
  }

  Tuple2<A, B> getTuple() {
    return tuple;
  }
}
