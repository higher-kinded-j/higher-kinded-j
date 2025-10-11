// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.monad;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;

public final class MonadDataStage<F, A, B> {
  private final Class<?> contextClass;
  private final Monad<F> monad;
  private final Kind<F, A> validKind;

  MonadDataStage(Class<?> contextClass, Monad<F> monad, Kind<F, A> validKind) {
    this.contextClass = contextClass;
    this.monad = monad;
    this.validKind = validKind;
  }

  public MonadOperationsStage<F, A, B> withMonadOperations(
      Kind<F, A> validKind2,
      Function<A, B> mapper,
      Function<A, Kind<F, B>> flatMapper,
      Kind<F, Function<A, B>> functionKind,
      BiFunction<A, A, B> combiningFunction) {

    return new MonadOperationsStage<>(
        contextClass,
        monad,
        validKind,
        validKind2,
        mapper,
        flatMapper,
        functionKind,
        combiningFunction);
  }
}
