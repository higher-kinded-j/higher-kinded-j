// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.owntype;

import java.util.Objects;
import java.util.Set;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;

/** Moves a JDK {@link Set}, which cannot implement {@link SetKind}, into a holder and back. */
// ANCHOR: set_helper
public enum SetKindHelper {
  SET;

  // The holder is the Kind; the Set inside it is untouched
  private record SetHolder<A>(Set<A> set) implements SetKind<A> {
    SetHolder {
      Objects.requireNonNull(set, "set");
    }
  }

  public <A> Kind<SetKind.Witness, A> widen(Set<A> set) {
    return new SetHolder<>(set);
  }

  public <A> Set<A> narrow(Kind<SetKind.Witness, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException("Cannot narrow a null Kind to a Set");
      case SetHolder<A>(var set) -> set;
      default -> throw new KindUnwrapException("Not a Set: " + kind.getClass().getName());
    };
  }
}
// ANCHOR_END: set_helper
