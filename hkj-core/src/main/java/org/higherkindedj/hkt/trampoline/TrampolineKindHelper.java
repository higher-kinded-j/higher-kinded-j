// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link TrampolineConverterOps} for widen/narrow operations, and providing
 * additional factory instance methods for {@link Trampoline} types.
 *
 * <p>Access these operations via the singleton {@code TRAMPOLINE}. For example: {@code
 * TrampolineKindHelper.TRAMPOLINE.widen(Trampoline.done(42));} Or, with static import: {@code
 * import static org.higherkindedj.hkt.trampoline.TrampolineKindHelper.TRAMPOLINE;
 * TRAMPOLINE.widen(...);}
 */
public enum TrampolineKindHelper implements TrampolineConverterOps {
  TRAMPOLINE;

  private static final Class<Trampoline> TRAMPOLINE_CLASS = Trampoline.class;

  /**
   * Widens a concrete {@link Trampoline}&lt;A&gt; instance into its HKT representation, {@link
   * Kind}&lt;{@link TrampolineKind.Witness}, A&gt;. Implements {@link
   * TrampolineConverterOps#widen}.
   *
   * <p>Since {@code Trampoline} extends {@code TrampolineKind}, this is a cast-free upcast: the
   * validated {@code trampoline} is already a {@code Kind<TrampolineKind.Witness, A>}, so no
   * wrapper object is allocated.
   *
   * @param <A> The element type of the {@code Trampoline}.
   * @param trampoline The concrete {@link Trampoline}&lt;A&gt; instance to widen. Must be non-null.
   * @return The {@link Kind<TrampolineKind.Witness, A>} representation of the input {@code
   *     trampoline}.
   * @throws NullPointerException if {@code trampoline} is {@code null}.
   */
  @Override
  public <A> Kind<TrampolineKind.Witness, A> widen(Trampoline<A> trampoline) {
    Validation.kind().requireForWiden(trampoline, TRAMPOLINE_CLASS);
    return trampoline;
  }

  /**
   * Narrows a {@link Kind}&lt;{@link TrampolineKind.Witness}, A&gt; back to its concrete {@link
   * Trampoline}&lt;A&gt; representation. Implements {@link TrampolineConverterOps#narrow}.
   *
   * @param <A> The element type of the {@code Trampoline}.
   * @param kind The {@code Kind} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link Trampoline}&lt;A&gt; instance.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is {@code null}, or
   *     not an instance of {@code Trampoline}.
   */
  @Override
  @SuppressWarnings("unchecked") // raw Class token; runtime-checked via Class.isInstance
  public <A> Trampoline<A> narrow(@Nullable Kind<TrampolineKind.Witness, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, TRAMPOLINE_CLASS);
  }

  /**
   * A convenience factory method that creates a {@link Trampoline#done(Object)} from the given
   * value and then wraps it into a {@link Kind}&lt;{@link TrampolineKind.Witness}, A&gt;.
   *
   * @param <A> The element type. The provided {@code value} must conform to this type.
   * @param value The value to be wrapped in a {@link Trampoline.Done} and then as a {@link Kind}.
   *     Can be {@code null} if {@code A} is a nullable type.
   * @return A {@link Kind}&lt;{@link TrampolineKind.Witness}, A&gt; representing {@code
   *     Done(value)}.
   */
  public <A> Kind<TrampolineKind.Witness, A> done(A value) {
    return this.widen(Trampoline.done(value));
  }
}
