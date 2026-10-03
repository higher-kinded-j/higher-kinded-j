// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.effect.pathsource;

import static org.higherkindedj.example.book.effect.pathsource.OutcomeKindHelper.OUTCOME;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.jspecify.annotations.Nullable;

/**
 * The {@code MonadError} an {@code OutcomePath} composes and recovers with: a {@link
 * Outcome.Failed} stops a chain, and {@code handleErrorWith} replaces it.
 */
public enum OutcomeMonad implements MonadError<OutcomeKind.Witness, Problem> {
  INSTANCE;

  @Override
  public <A> Kind<OutcomeKind.Witness, A> of(@Nullable A value) {
    return new Outcome.Ok<>(value);
  }

  @Override
  public <A, B> Kind<OutcomeKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<OutcomeKind.Witness, A> fa) {
    return flatMap(a -> of(f.apply(a)), fa);
  }

  @Override
  public <A, B> Kind<OutcomeKind.Witness, B> ap(
      Kind<OutcomeKind.Witness, ? extends Function<A, B>> ff, Kind<OutcomeKind.Witness, A> fa) {
    return flatMap(f -> map(f, fa), ff);
  }

  @Override
  public <A, B> Kind<OutcomeKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<OutcomeKind.Witness, B>> f,
      Kind<OutcomeKind.Witness, A> ma) {
    return switch (OUTCOME.narrow(ma)) {
      case Outcome.Ok<A>(var value) -> f.apply(value);
      case Outcome.Failed<A>(var problem) -> new Outcome.Failed<>(problem);
    };
  }

  @Override
  public <A> Kind<OutcomeKind.Witness, A> raiseError(Problem error) {
    return new Outcome.Failed<>(Objects.requireNonNull(error, "error must not be null"));
  }

  @Override
  public <A> Kind<OutcomeKind.Witness, A> handleErrorWith(
      Kind<OutcomeKind.Witness, A> ma,
      Function<? super Problem, ? extends Kind<OutcomeKind.Witness, A>> handler) {
    return switch (OUTCOME.narrow(ma)) {
      case Outcome.Ok<A> ok -> ok;
      case Outcome.Failed<A>(var problem) -> handler.apply(problem);
    };
  }
}
