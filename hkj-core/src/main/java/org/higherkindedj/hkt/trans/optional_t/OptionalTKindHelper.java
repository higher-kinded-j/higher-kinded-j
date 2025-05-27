// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trans.optional_t;

import java.util.Objects;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class for simulating higher-kinded types (HKT) with {@link OptionalT}. It provides
 * methods to safely wrap a concrete {@link OptionalT} instance into its {@link Kind} representation
 * ({@code Kind<OptionalTKind.Witness<F>, A>}) and to unwrap it back.
 *
 * <p>This helper is essential for using {@code OptionalT} with generic HKT abstractions like {@code
 * Monad<OptionalTKind.Witness<F>, A>}. Since {@link OptionalT} now directly implements {@link
 * OptionalTKind}, this helper primarily facilitates type casting and provides runtime safety
 * checks.
 *
 * <p>This class is final and cannot be instantiated.
 */
public final class OptionalTKindHelper {

  // Error Messages
  /** Error message for attempting to unwrap a null Kind. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for OptionalT";

  /** Error message for attempting to unwrap a Kind of an unexpected type. */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an OptionalT: ";

  /**
   * Private constructor to prevent instantiation of this utility class.
   *
   * @throws UnsupportedOperationException always.
   */
  private OptionalTKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps a {@code Kind<OptionalTKind.Witness<F>, A>} back to its concrete {@link OptionalT
   * OptionalT&lt;F, A&gt;} type.
   *
   * @param <F> The witness type of the outer monad in {@code OptionalT}.
   * @param <A> The type of the value potentially held by the inner {@link Optional}.
   * @param kind The {@code Kind<OptionalTKind.Witness<F>, A>} to unwrap. Can be null.
   * @return The unwrapped, non-null {@link OptionalT OptionalT&lt;F, A&gt;} instance.
   * @throws KindUnwrapException if {@code kind} is null or not a valid {@code OptionalT} instance.
   */
  @SuppressWarnings("unchecked")
  public static <F, A> @NonNull OptionalT<F, A> unwrap(
      @Nullable Kind<OptionalTKind.Witness<F>, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(OptionalTKindHelper.INVALID_KIND_NULL_MSG);
      // Since OptionalT<F,A> implements OptionalTKind<F,A>,
      // which extends Kind<OptionalTKind.Witness<F>,A>
      case OptionalT<F, A> directOptionalT -> directOptionalT;
      default ->
          throw new KindUnwrapException(
              OptionalTKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link OptionalT OptionalT&lt;F, A&gt;} instance into its {@link Kind}
   * representation, {@code Kind<OptionalTKind.Witness<F>, A>}.
   *
   * <p>Since {@link OptionalT} directly implements {@link OptionalTKind} (which extends {@code
   * Kind<OptionalTKind.Witness<F>, A>}), this method effectively performs a safe cast.
   *
   * @param <F> The witness type of the outer monad in {@code OptionalT}.
   * @param <A> The type of the value potentially held by the inner {@link Optional}.
   * @param optionalT The concrete {@link OptionalT} instance to wrap. Must not be null.
   * @return The {@code Kind} representation of the {@code optionalT}.
   * @throws NullPointerException if {@code optionalT} is null.
   */
  public static <F, A> @NonNull Kind<OptionalTKind.Witness<F>, A> wrap(
      @NonNull OptionalT<F, A> optionalT) {
    Objects.requireNonNull(optionalT, "Input OptionalT cannot be null for wrap");
    return (Kind<OptionalTKind.Witness<F>, A>) (OptionalTKind<F, A>) optionalT;
  }
}
