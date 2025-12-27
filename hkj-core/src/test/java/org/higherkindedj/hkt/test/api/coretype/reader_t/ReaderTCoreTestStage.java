// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.reader_t;

import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.reader_t.ReaderT;

/**
 * Stage 1: Configure ReaderT test instances.
 *
 * <p>Entry point for ReaderT core type testing with progressive disclosure.
 *
 * @param <F> The outer monad witness type
 * @param <R> The environment type
 * @param <A> The value type
 */
public final class ReaderTCoreTestStage<F extends WitnessArity<TypeArity.Unary>, R, A> {
  private final Class<?> contextClass;
  private final Monad<F> outerMonad;

  public ReaderTCoreTestStage(Class<?> contextClass, Monad<F> outerMonad) {
    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
  }

  /**
   * Provides a ReaderT instance for testing.
   *
   * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
   *
   * @param readerTInstance A ReaderT instance
   * @return Next stage for configuring operations
   */
  public ReaderTOperationsStage<F, R, A> withInstance(ReaderT<F, R, A> readerTInstance) {
    return new ReaderTOperationsStage<>(contextClass, outerMonad, readerTInstance);
  }
}
