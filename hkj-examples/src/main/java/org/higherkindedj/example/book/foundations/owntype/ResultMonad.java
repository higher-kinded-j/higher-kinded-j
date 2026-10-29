// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.owntype;

import static org.higherkindedj.example.book.foundations.owntype.ResultKindHelper.RESULT;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;

/** The {@link Monad} for {@link Result}: a Failed passes through every step untouched. */
// ANCHOR: monad
public enum ResultMonad implements Monad<ResultKind.Witness> {
  INSTANCE;

  @Override
  public <A> Kind<ResultKind.Witness, A> of(A value) {
    return RESULT.widen(Result.ok(value));
  }

  @Override
  public <A, B> Kind<ResultKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<ResultKind.Witness, A> fa) {
    return RESULT.widen(
        switch (RESULT.narrow(fa)) {
          case Result.Ok<A>(var value) -> Result.ok(f.apply(value));
          case Result.Failed<A>(var reason) -> Result.failed(reason);
        });
  }

  @Override
  public <A, B> Kind<ResultKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<ResultKind.Witness, B>> f,
      Kind<ResultKind.Witness, A> ma) {
    return switch (RESULT.narrow(ma)) {
      // narrow refuses a step that returned null, as the library's own instances do
      case Result.Ok<A>(var value) -> RESULT.widen(RESULT.narrow(f.apply(value)));
      case Result.Failed<A>(var reason) -> RESULT.widen(Result.failed(reason));
    };
  }

  @Override
  public <A, B> Kind<ResultKind.Witness, B> ap(
      Kind<ResultKind.Witness, ? extends Function<A, B>> ff, Kind<ResultKind.Witness, A> fa) {
    Objects.requireNonNull(fa, "fa");
    return flatMap(f -> map(f, fa), ff);
  }
}
// ANCHOR_END: monad
