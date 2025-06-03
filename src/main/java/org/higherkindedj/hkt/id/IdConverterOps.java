// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;

/**
 * Defines conversion operations (widen and narrow) specific to Id types and their Kind
 * representations. The methods are generic to handle the value type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface IdConverterOps {

  /**
   * Widens an {@link Id} to its {@link Kind} representation.
   *
   * @param id The {@link Id} instance to widen. Must not be null.
   * @param <A> The type of the value.
   * @return The {@link Kind} representation.
   */
  <A> @NonNull Kind<Id.Witness, A> widen(@NonNull Id<A> id);

  /**
   * Narrows a {@link Kind} representation to an {@link Id}.
   *
   * @param kind The {@link Kind} instance to narrow. Must not be null.
   * @param <A> The type of the value.
   * @return The {@link Id} instance.
   * @throws ClassCastException if the kind is not an Id instance of the expected type.
   */
  <A> @NonNull Id<A> narrow(@NonNull Kind<Id.Witness, A> kind);
}
