// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to Optional types and their Kind
 * representations. The methods are generic to handle the value type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface OptionalConverterOps {

  /**
   * Widens a concrete {@link Optional}{@code <A>} instance into its higher-kinded representation,
   * {@code Kind<OptionalKind.Witness, A>}.
   *
   * @param <A> The type of the value potentially held by the {@code Optional}.
   * @param optional The concrete {@link Optional}{@code <A>} instance to widen. Must not be {@code
   *     null} (though it can be {@code Optional.empty()}).
   * @return A non-null {@link Kind<OptionalKind.Witness, A>} representing the wrapped {@code
   *     Optional}.
   * @throws NullPointerException if {@code optional} is {@code null}.
   */
  <A> Kind<OptionalKind.Witness, A> widen(Optional<A> optional);

  /**
   * Narrows a {@code Kind<OptionalKind.Witness, A>} back to its concrete {@link Optional}{@code
   * <A>} type.
   *
   * @param <A> The type of the value potentially held by the {@code Optional}.
   * @param kind The {@code Kind<OptionalKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link Optional}{@code <A>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, or not an instance of
   *     the expected underlying holder type for Optional.
   */
  <A> Optional<A> narrow(@Nullable Kind<OptionalKind.Witness, A> kind);
}
