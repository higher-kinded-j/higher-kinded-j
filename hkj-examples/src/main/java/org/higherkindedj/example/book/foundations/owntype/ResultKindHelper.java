// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.owntype;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;

/** Moves a {@link Result} into the encoding and back. */
// ANCHOR: helper
public enum ResultKindHelper {
  RESULT;

  // A Result is already its own Kind, so widen returns the same object
  public <A> Kind<ResultKind.Witness, A> widen(Result<A> result) {
    return Objects.requireNonNull(result, "result");
  }

  public <A> Result<A> narrow(Kind<ResultKind.Witness, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException("Cannot narrow a null Kind to a Result");
      case Result<A> result -> result;
      default -> throw new KindUnwrapException("Not a Result: " + kind.getClass().getName());
    };
  }
}
// ANCHOR_END: helper
