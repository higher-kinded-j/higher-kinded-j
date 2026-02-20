// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link VStreamConverterOps} for widen/narrow operations on {@link VStream}
 * types.
 *
 * <p>Access these operations via the singleton {@code VSTREAM}. For example: {@code
 * VStreamKindHelper.VSTREAM.widen(myVStream);} Or, with static import: {@code import static
 * org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM; VSTREAM.widen(...);}
 *
 * @see VStream
 * @see VStreamKind
 * @see VStreamConverterOps
 */
public enum VStreamKindHelper implements VStreamConverterOps {
  /** The singleton instance for VStream operations. */
  VSTREAM;

  private static final Class<VStream> VSTREAM_CLASS = VStream.class;

  /**
   * Widens a concrete {@link VStream} instance into its higher-kinded representation, {@code
   * Kind<VStreamKind.Witness, A>}. Implements {@link VStreamConverterOps#widen}.
   *
   * <p>Since {@code VStream} extends {@code VStreamKind}, this method performs a simple type-safe
   * cast without requiring a wrapper object.
   *
   * @param <A> The element type of the {@code VStream}.
   * @param vstream The non-null, concrete {@link VStream} instance to widen.
   * @return A non-null {@link Kind} representing the {@code VStream}.
   * @throws NullPointerException if {@code vstream} is {@code null}.
   */
  @Override
  public <A> Kind<VStreamKind.Witness, A> widen(VStream<A> vstream) {
    Validation.kind().requireForWiden(vstream, VSTREAM_CLASS);
    return vstream;
  }

  /**
   * Narrows a {@code Kind<VStreamKind.Witness, A>} back to its concrete {@link VStream} type.
   * Implements {@link VStreamConverterOps#narrow}.
   *
   * <p>Since {@code VStream} extends {@code VStreamKind}, this method performs a direct type check
   * and cast without needing to unwrap from a holder.
   *
   * @param <A> The element type of the {@code VStream}.
   * @param kind The {@code Kind<VStreamKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link VStream} instance.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is {@code
   *     null}, or not an instance of {@code VStream}.
   */
  @Override
  public <A> VStream<A> narrow(@Nullable Kind<VStreamKind.Witness, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, VSTREAM_CLASS);
  }
}
