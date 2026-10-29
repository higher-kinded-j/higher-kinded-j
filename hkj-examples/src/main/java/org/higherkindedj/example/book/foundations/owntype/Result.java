// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.owntype;

import java.util.Objects;

/**
 * A result type of the kind many codebases write for themselves: a value, or the reason there is
 * none. The book's Adding Your Own Type page makes it work with Higher-Kinded-J's type classes.
 */
// ANCHOR: result
public sealed interface Result<A> extends ResultKind<A> {

  record Ok<A>(A value) implements Result<A> {
    public Ok {
      Objects.requireNonNull(value, "value");
    }
  }

  record Failed<A>(String reason) implements Result<A> {
    public Failed {
      Objects.requireNonNull(reason, "reason");
    }
  }

  static <A> Result<A> ok(A value) {
    return new Ok<>(value);
  }

  static <A> Result<A> failed(String reason) {
    return new Failed<>(reason);
  }
}
// ANCHOR_END: result
