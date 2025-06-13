// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;

/**
 * Defines conversion operations (widen and narrow) specific to Validated types and their Kind
 * representations. The methods are generic to handle the error (E) and value (A) types.
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface ValidatedConverterOps {

  /**
   * Widens a {@link Validated} to its {@link Kind} representation.
   *
   * @param validated The {@link Validated} instance. Must not be null.
   * @param <E> The error type.
   * @param <A> The value type.
   * @return The {@link Kind} representation.
   */
  <E, A> @NonNull Kind<ValidatedKind.Witness<E>, A> widen(@NonNull Validated<E, A> validated);

  /**
   * Narrows a {@link Kind} representation to a {@link Validated}.
   *
   * @param kind The {@link Kind} instance. Must not be null. It is expected that this Kind instance
   *     is, in fact, an instance of Valid or Invalid.
   * @param <E> The error type.
   * @param <A> The value type.
   * @return The {@link Validated} instance.
   * @throws ClassCastException if the kind is not a Validated instance of the expected types.
   */
  <E, A> @NonNull Validated<E, A> narrow(@NonNull Kind<ValidatedKind.Witness<E>, A> kind);
}
