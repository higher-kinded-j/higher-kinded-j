// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.coyoneda;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Helper for converting between Coyoneda and Kind representations.
 *
 * <p>This enum provides utilities for:
 *
 * <ul>
 *   <li>Widening concrete Coyoneda types to their Kind representation
 *   <li>Narrowing Kind representations back to concrete Coyoneda types
 * </ul>
 *
 * <p>Access these operations via the singleton {@code COYONEDA}. For example:
 *
 * <pre>{@code
 * CoyonedaKindHelper.COYONEDA.widen(Coyoneda.lift(someKind));
 * }</pre>
 *
 * <p>Or, with static import:
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.coyoneda.CoyonedaKindHelper.COYONEDA;
 * COYONEDA.widen(...);
 * }</pre>
 */
public enum CoyonedaKindHelper {
  /** Singleton instance of the CoyonedaKindHelper. */
  COYONEDA;

  /**
   * Holder record that wraps a Coyoneda instance and implements CoyonedaKind.
   *
   * <p>This enables the concrete Coyoneda type to be represented as a Kind.
   *
   * @param <F> The underlying type constructor
   * @param <A> The result type
   */
  record CoyonedaHolder<F, A>(Coyoneda<F, A> coyoneda) implements CoyonedaKind<F, A> {}

  /**
   * Widens a concrete Coyoneda type to its Kind representation.
   *
   * <p>This allows Coyoneda to be used with type classes that operate on Kind types.
   *
   * @param coyoneda The Coyoneda instance to widen. Must not be null.
   * @param <F> The underlying type constructor
   * @param <A> The result type
   * @return The Kind representation of the Coyoneda instance
   * @throws NullPointerException if coyoneda is null
   */
  public <F, A> Kind<CoyonedaKind.Witness<F>, A> widen(Coyoneda<F, A> coyoneda) {
    if (coyoneda == null) {
      throw new NullPointerException("Coyoneda to widen cannot be null");
    }
    return new CoyonedaHolder<>(coyoneda);
  }

  /**
   * Narrows a Kind representation back to a concrete Coyoneda type.
   *
   * <p>This is the inverse of {@link #widen(Coyoneda)}.
   *
   * @param kind The Kind representation to narrow. May be null.
   * @param <F> The underlying type constructor
   * @param <A> The result type
   * @return The concrete Coyoneda instance
   * @throws KindUnwrapException if kind is null or not a valid CoyonedaKind representation
   */
  @SuppressWarnings("unchecked")
  public <F, A> Coyoneda<F, A> narrow(@Nullable Kind<CoyonedaKind.Witness<F>, A> kind) {
    if (kind == null) {
      throw new KindUnwrapException("Cannot narrow null Kind to Coyoneda");
    }
    if (kind instanceof CoyonedaHolder<?, ?> holder) {
      return (Coyoneda<F, A>) holder.coyoneda();
    }
    throw new KindUnwrapException(
        "Cannot narrow Kind to Coyoneda: expected CoyonedaHolder but got " + kind.getClass());
  }

  /**
   * Convenience factory that lifts a Kind into Coyoneda and wraps it as a Kind.
   *
   * <p>This combines {@link Coyoneda#lift(Kind)} and {@link #widen(Coyoneda)} in a single
   * operation.
   *
   * @param fa The Kind to lift and widen. Must not be null.
   * @param <F> The type constructor
   * @param <A> The value type
   * @return A Kind representing Coyoneda containing the lifted value
   * @throws NullPointerException if fa is null
   */
  public <F, A> Kind<CoyonedaKind.Witness<F>, A> lift(Kind<F, A> fa) {
    return widen(Coyoneda.lift(fa));
  }
}
