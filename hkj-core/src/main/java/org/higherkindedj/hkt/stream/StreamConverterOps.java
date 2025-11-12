// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.stream;

import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to Stream types and their Kind
 * representations. The methods are generic to handle the element type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 *
 * <p><b>Important:</b> Due to the single-use nature of {@link java.util.stream.Stream}, care must
 * be taken when using these conversion operations. Each terminal operation (including internal
 * operations that may occur during narrowing in some contexts) consumes the stream, making it
 * unusable for subsequent operations.
 *
 * @see StreamKind
 * @see StreamKindHelper
 */
public interface StreamConverterOps {

  /**
   * Widens a standard {@link java.util.stream.Stream} into its higher-kinded representation, {@code
   * Kind<StreamKind.Witness, A>}.
   *
   * <p>This operation does not consume the stream; it simply wraps it in the HKT representation.
   * The stream can still be used lazily through type class operations.
   *
   * @param stream The {@link Stream} to widen. Must not be null.
   * @param <A> The element type of the stream.
   * @return The higher-kinded representation of the stream. Never null.
   * @throws NullPointerException if stream is null.
   */
  <A> Kind<StreamKind.Witness, A> widen(Stream<A> stream);

  /**
   * Narrows a higher-kinded representation of a stream, {@code Kind<StreamKind.Witness, A>}, back
   * to a standard {@link java.util.stream.Stream}.
   *
   * <p><b>Warning:</b> The returned stream maintains all the characteristics of {@code
   * java.util.stream.Stream}, including single-use semantics. After performing any terminal
   * operation on the returned stream, it cannot be reused.
   *
   * @param kind The higher-kinded representation of the stream. May be null.
   * @param <A> The element type of the stream.
   * @return The underlying {@link java.util.stream.Stream}. Never null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if kind is null or not a valid
   *     StreamKind representation.
   */
  <A> Stream<A> narrow(@Nullable Kind<StreamKind.Witness, A> kind);
}
