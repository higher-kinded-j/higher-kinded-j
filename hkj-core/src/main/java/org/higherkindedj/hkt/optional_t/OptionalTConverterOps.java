// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional_t;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to OptionalT types and their Kind
 * representations. The methods are generic to handle the outer monad (F) and value type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface OptionalTConverterOps {

  /**
   * Widens a concrete {@link OptionalT OptionalT&lt;F, A&gt;} instance into its higher-kinded
   * representation, {@code Kind<OptionalTKind.Witness<F>, A>}.
   *
   * @param <F> The witness type of the outer monad in {@code OptionalT}.
   * @param <A> The type of the value potentially held by the inner {@link java.util.Optional}.
   * @param optionalT The concrete {@link OptionalT} instance to widen. Must not be null.
   * @return The {@code Kind} representation.
   * @throws NullPointerException if {@code optionalT} is {@code null}.
   */
  <F, A> Kind<OptionalTKind.Witness<F>, A> widen(OptionalT<F, A> optionalT);

  /**
   * Narrows a {@code Kind<OptionalTKind.Witness<F>, A>} back to its concrete {@link OptionalT
   * OptionalT&lt;F, A&gt;} type.
   *
   * @param <F> The witness type of the outer monad in {@code OptionalT}.
   * @param <A> The type of the value potentially held by the inner {@link java.util.Optional}.
   * @param kind The {@code Kind<OptionalTKind.Witness<F>, A>} to narrow. Can be null.
   * @return The unwrapped, non-null {@link OptionalT OptionalT&lt;F, A&gt;} instance.
   * @throws KindUnwrapException if {@code kind} is null or not a valid {@link OptionalT} instance.
   */
  <F, A> OptionalT<F, A> narrow(@Nullable Kind<OptionalTKind.Witness<F>, A> kind);
}
