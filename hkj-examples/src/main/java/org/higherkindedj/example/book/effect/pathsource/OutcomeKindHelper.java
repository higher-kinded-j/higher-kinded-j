// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.effect.pathsource;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;

/**
 * Converts between {@link Outcome} and its {@code Kind}, as each of the library's own types does.
 */
public enum OutcomeKindHelper {
  OUTCOME;

  /** A {@code Outcome} is already its own {@code Kind}, so widening returns it unchanged. */
  public <A> Kind<OutcomeKind.Witness, A> widen(Outcome<A> outcome) {
    return Objects.requireNonNull(outcome, "outcome must not be null");
  }

  /**
   * The {@code Outcome} a {@code Kind} of its witness stands for.
   *
   * @throws KindUnwrapException if the {@code Kind} is null, or is not a {@code Outcome}
   */
  public <A> Outcome<A> narrow(Kind<OutcomeKind.Witness, A> kind) {
    if (kind instanceof Outcome<A> outcome) {
      return outcome;
    }
    throw new KindUnwrapException("Not a Outcome: " + kind);
  }
}
