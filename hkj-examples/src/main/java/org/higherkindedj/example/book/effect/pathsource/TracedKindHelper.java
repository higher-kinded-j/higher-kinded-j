// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.effect.pathsource;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;

/**
 * Converts between {@link Traced} and its {@code Kind}, as each of the library's own types does.
 */
public enum TracedKindHelper {
  TRACED;

  /** A {@code Traced} is already its own {@code Kind}, so widening returns it unchanged. */
  public <A> Kind<TracedKind.Witness, A> widen(Traced<A> traced) {
    return Objects.requireNonNull(traced, "traced must not be null");
  }

  /**
   * The {@code Traced} a {@code Kind} of its witness stands for.
   *
   * @throws KindUnwrapException if the {@code Kind} is null, or is not a {@code Traced}
   */
  public <A> Traced<A> narrow(Kind<TracedKind.Witness, A> kind) {
    if (kind instanceof Traced<A> traced) {
      return traced;
    }
    throw new KindUnwrapException("Not a Traced: " + kind);
  }
}
