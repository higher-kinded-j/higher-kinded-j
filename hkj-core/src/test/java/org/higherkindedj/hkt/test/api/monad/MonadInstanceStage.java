// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.monad;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;

public final class MonadInstanceStage<F, A> {
  private final Class<?> contextClass;
  private final Monad<F> monad;

  MonadInstanceStage(Class<?> contextClass, Monad<F> monad) {
    this.contextClass = contextClass;
    this.monad = monad;
  }

  public <B> MonadDataStage<F, A, B> withKind(Kind<F, A> validKind) {
    return new MonadDataStage<>(contextClass, monad, validKind);
  }
}
