// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;

/**
 * Helper class for {@link ValidatedKind}. Provides methods to widen {@link Validated} instances to
 * {@code Kind<ValidatedKind.Witness<E>, A>}, narrow them back, and Kind-returning factory methods.
 */
public final class ValidatedKindHelper {

  private ValidatedKindHelper() {
    // Private constructor to prevent instantiation
  }

  /**
   * Widens a {@link Validated} to its {@link Kind} representation.
   *
   * @param validated The {@link Validated} instance. Must not be null.
   * @param <E> The error type.
   * @param <A> The value type.
   * @return The {@link Kind} representation.
   */
  @SuppressWarnings("unchecked") // This cast is safe due to the sealed nature of Validated
  // and its subtypes (Valid, Invalid) implementing ValidatedKind.
  public static <E, A> @NonNull Kind<ValidatedKind.Witness<E>, A> widen(
      @NonNull Validated<E, A> validated) {
    return (Kind<ValidatedKind.Witness<E>, A>) validated;
  }

  /**
   * Narrows a {@link Kind} representation to a {@link Validated}.
   *
   * @param kind The {@link Kind} instance. Must not be null. It is expected that this Kind instance
   *     is, in fact, an instance of Valid or Invalid.
   * @param <E> The error type.
   * @param <A> The value type.
   * @return The {@link Validated} instance.
   * @throws ClassCastException if the kind is not a Validated instance (Valid or Invalid).
   */
  @SuppressWarnings("unchecked")
  public static <E, A> @NonNull Validated<E, A> narrow(
      @NonNull Kind<ValidatedKind.Witness<E>, A> kind) {
    return (Validated<E, A>) kind;
  }

  /**
   * Creates a {@code Kind<ValidatedKind.Witness<E>, A>} representing a {@code Valid(value)}. This
   * is a convenience factory method.
   *
   * @param value The valid value. Must not be null.
   * @param <E> The error type (associated with the Witness).
   * @param <A> The value type.
   * @return A {@code Kind} representing a valid instance.
   */
  public static <E, A> @NonNull Kind<ValidatedKind.Witness<E>, A> valid(@NonNull A value) {
    return widen(Validated.valid(value));
  }

  /**
   * Creates a {@code Kind<ValidatedKind.Witness<E>, A>} representing an {@code Invalid(error)}.
   * This is a convenience factory method.
   *
   * @param error The error value. Must not be null.
   * @param <E> The error type.
   * @param <A> The value type (associated with the Witness).
   * @return A {@code Kind} representing an invalid instance.
   */
  public static <E, A> @NonNull Kind<ValidatedKind.Witness<E>, A> invalid(@NonNull E error) {
    return widen(Validated.invalid(error));
  }
}
