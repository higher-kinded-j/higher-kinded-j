// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import org.higherkindedj.hkt.Kind2;
import org.jspecify.annotations.NullMarked;

/**
 * Wrapper for {@link Writer} to work with the {@link Kind2} system for bifunctor operations.
 *
 * <p>This representation treats {@link Writer} as a type constructor with two type parameters,
 * both of which can vary. This enables bifunctor operations where both the log (written output)
 * and value types can be transformed independently.
 *
 * <p>This is distinct from {@link WriterKind}, which fixes the log type parameter for use with
 * {@link org.higherkindedj.hkt.Functor} and {@link org.higherkindedj.hkt.Monad} instances.
 *
 * @param <W> The type of the log (written output)
 * @param <A> The type of the computed value
 * @see Writer
 * @see WriterKind
 * @see org.higherkindedj.hkt.Bifunctor
 */
@NullMarked
public final class WriterKind2<W, A> implements Kind2<WriterKind2.Witness, W, A> {

  /** Witness type for the Writer type constructor when used as a bifunctor. */
  public static final class Witness {}

  private final Writer<W, A> writer;

  WriterKind2(Writer<W, A> writer) {
    this.writer = writer;
  }

  Writer<W, A> getWriter() {
    return writer;
  }
}
