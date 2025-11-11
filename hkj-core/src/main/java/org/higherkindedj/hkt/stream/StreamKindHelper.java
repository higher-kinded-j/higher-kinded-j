// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.stream;

import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link StreamConverterOps} for widen/narrow operations, and providing
 * additional utility instance methods for {@link Stream} types in an HKT context.
 *
 * <p>Access these operations via the singleton {@code STREAM}. For example: {@code
 * StreamKindHelper.STREAM.widen(Stream.of(1, 2, 3));} Or, with static import: {@code import static
 * org.higherkindedj.hkt.stream.StreamKindHelper.STREAM; STREAM.widen(...);}
 *
 * <p><b>Stream Single-Use Semantics:</b> Operations provided by this helper maintain the lazy
 * evaluation characteristics of {@code java.util.stream.Stream}. The {@link #widen} operation
 * simply wraps a stream without consuming it. However, {@link #narrow} extracts the stream, which
 * can then be used once for a terminal operation. Subsequent attempts to use the same stream
 * instance will result in {@code IllegalStateException}.
 *
 * @see StreamKind
 * @see StreamConverterOps
 */
public enum StreamKindHelper implements StreamConverterOps {
  STREAM;

  private static final Class<Stream> STREAM_CLASS = Stream.class;

  /**
   * Concrete implementation of {@link StreamKind<A>}. This record wraps a {@code
   * java.util.stream.Stream<A>} to make it a {@code StreamKind<A>}.
   *
   * <p>The wrapped stream maintains its lazy evaluation characteristics and single-use semantics.
   *
   * @param <A> The element type of the stream.
   * @param stream The stream. Must not be null when holder is created.
   */
  record StreamHolder<A>(Stream<A> stream) implements StreamKind<A> {}

  /**
   * Widens a standard {@link java.util.stream.Stream} into its higher-kinded representation, {@code
   * Kind<StreamKind.Witness, A>}.
   *
   * <p>This is a pure wrapping operation that does not evaluate or consume the stream. The stream
   * remains lazy and can be used through type class operations.
   *
   * @param stream The {@link Stream} to widen. Must not be null.
   * @param <A> The element type of the stream.
   * @return The higher-kinded representation of the stream. Never null.
   * @throws NullPointerException if stream is null.
   */
  @Override
  public <A> Kind<StreamKind.Witness, A> widen(Stream<A> stream) {
    Validation.kind().requireForWiden(stream, STREAM_CLASS);
    return of(stream);
  }

  /**
   * Narrows a higher-kinded representation of a stream, {@code Kind<StreamKind.Witness, A>}, back
   * to a standard {@link java.util.stream.Stream}.
   *
   * <p>This implementation uses a holder-based approach with modern switch expressions for
   * consistent pattern matching.
   *
   * <p><b>Important:</b> The returned stream retains all the characteristics of {@code
   * java.util.stream.Stream}, including:
   *
   * <ul>
   *   <li>Single-use semantics - can only be consumed once
   *   <li>Lazy evaluation - operations are not performed until a terminal operation is invoked
   *   <li>Sequential or parallel processing mode (depending on the original stream)
   * </ul>
   *
   * @param kind The higher-kinded representation of the stream. May be null.
   * @param <A> The element type of the stream.
   * @return The underlying {@link java.util.stream.Stream}. Never null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if kind is null or not a valid
   *     StreamKind representation.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <A> Stream<A> narrow(@Nullable Kind<StreamKind.Witness, A> kind) {
    return Validation.kind()
        .narrowWithPattern(
            kind, STREAM_CLASS, StreamHolder.class, holder -> ((StreamHolder<A>) holder).stream());
  }

  /**
   * Narrows a higher-kinded representation of a stream with a default value if the kind is null.
   *
   * <p><b>Note on default stream:</b> If you provide a default stream and it gets returned, be
   * aware that using this stream will consume it. If you need to reuse a default, consider
   * providing a supplier of streams instead of using this method multiple times with the same
   * default stream instance.
   *
   * @param kind The higher-kinded representation of the stream. May be null.
   * @param defaultValue The stream to return if {@code kind} is null. Must not be null.
   * @param <A> The element type of the stream.
   * @return The unwrapped stream, or {@code defaultValue} if {@code kind} is null. Never null.
   * @throws NullPointerException if defaultValue is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if kind is not a valid StreamKind
   *     representation.
   */
  public <A> Stream<A> narrowOr(
      @Nullable Kind<StreamKind.Witness, A> kind, Stream<A> defaultValue) {
    Validation.kind().requireForWiden(defaultValue, STREAM_CLASS);
    if (kind == null) {
      return defaultValue;
    }
    return narrow(kind);
  }

  /**
   * Factory method to create a {@code StreamKind<A>} (specifically a {@code StreamHolder<A>}) from
   * a standard {@link java.util.stream.Stream}.
   *
   * <p>This is a convenience method equivalent to calling {@link #widen}, but returns the concrete
   * {@code StreamKind} type rather than the {@code Kind} interface.
   *
   * @param stream The stream to wrap.
   * @param <A> The element type.
   * @return A new {@code StreamKind<A>} instance wrapping the provided stream.
   */
  public <A> StreamKind<A> of(Stream<A> stream) {
    return new StreamHolder<>(stream);
  }
}
