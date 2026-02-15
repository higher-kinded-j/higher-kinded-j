// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.future;

import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to CompletableFuture types and their
 * Kind representations. The methods are generic to handle the value type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface CompletableFutureConverterOps {

  /**
   * Widens a concrete {@link CompletableFuture<A>} instance into its higher-kinded representation,
   * {@code Kind<CompletableFutureKind.Witness, A>}.
   *
   * @param <A> The result type of the {@code CompletableFuture}.
   * @param future The non-null, concrete {@link CompletableFuture<A>} instance to widen.
   * @return A non-null {@link Kind<CompletableFutureKind.Witness, A>} representing the wrapped
   *     future.
   * @throws NullPointerException if {@code future} is {@code null}.
   */
  <A> Kind<CompletableFutureKind.Witness, A> widen(CompletableFuture<A> future);

  /**
   * Narrows a {@code Kind<CompletableFutureKind.Witness, A>} back to its concrete {@link
   * CompletableFuture<A>} type.
   *
   * @param <A> The result type of the {@code CompletableFuture}.
   * @param kind The {@code Kind<CompletableFutureKind.Witness, A>} instance to narrow. May be
   *     {@code null}.
   * @return The underlying, non-null {@link CompletableFuture<A>}.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, not an instance of the
   *     expected underlying holder type, or if the holder internally contains a {@code null}
   *     future.
   */
  <A> CompletableFuture<A> narrow(@Nullable Kind<CompletableFutureKind.Witness, A> kind);
}
