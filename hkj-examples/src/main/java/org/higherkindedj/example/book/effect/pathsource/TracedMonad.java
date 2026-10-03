// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.effect.pathsource;

import static org.higherkindedj.example.book.effect.pathsource.TracedKindHelper.TRACED;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.Nullable;

/**
 * The {@code Monad} a {@code TracedPath} composes with: {@code map} keeps the log, and {@code
 * flatMap} appends the next step's log to this one's.
 */
// ANCHOR: traced_monad
public enum TracedMonad implements Monad<TracedKind.Witness> {
  INSTANCE;

  @Override
  public <A> Kind<TracedKind.Witness, A> of(@Nullable A value) {
    return new Traced<>(value, List.of());
  }

  @Override
  public <A, B> Kind<TracedKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<TracedKind.Witness, A> fa) {
    Traced<A> traced = TRACED.narrow(fa);
    return new Traced<>(f.apply(traced.value()), traced.log());
  }

  @Override
  public <A, B> Kind<TracedKind.Witness, B> ap(
      Kind<TracedKind.Witness, ? extends Function<A, B>> ff, Kind<TracedKind.Witness, A> fa) {
    return flatMap(f -> map(f, fa), ff);
  }

  @Override
  public <A, B> Kind<TracedKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<TracedKind.Witness, B>> f,
      Kind<TracedKind.Witness, A> ma) {
    Traced<A> first = TRACED.narrow(ma);
    Traced<B> next = TRACED.narrow(f.apply(first.value()));
    return new Traced<>(
        next.value(), Stream.concat(first.log().stream(), next.log().stream()).toList());
  }
}
// ANCHOR_END: traced_monad
