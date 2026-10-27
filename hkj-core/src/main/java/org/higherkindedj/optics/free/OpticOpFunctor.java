// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * Functor instance for {@link OpticOp}.
 *
 * <p>This enables OpticOp to be used with the Free monad. Mapping composes the function onto the
 * operation's continuation through {@link OpticOp#mapK(Function)}, so the instruction still
 * describes the same optic operation and its result is passed through the function. As with {@link
 * org.higherkindedj.hkt.free.Free#map(Function)}, the function must not return null.
 */
@NullMarked
public enum OpticOpFunctor implements Functor<OpticOpKind.Witness> {
  /** Singleton instance. */
  INSTANCE;

  @Override
  public <A, B> Kind<OpticOpKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<OpticOpKind.Witness, A> fa) {
    Validation.function().validateMap(f, fa);
    // A null result fails where Free.map's does, when the operation runs, with the same message
    return OpticOpKindHelper.OP
        .narrow(fa)
        .mapK(a -> Objects.requireNonNull(f.apply(a), "f must not return null"));
  }
}
