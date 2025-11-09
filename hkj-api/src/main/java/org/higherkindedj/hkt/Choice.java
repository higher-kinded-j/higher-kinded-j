// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.function.Function;

public interface Choice<L, R> {

  boolean isLeft();

  boolean isRight();

  L getLeft();

  R getRight();

  <T> T fold(
      Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends T> rightMapper);

  <R2> Choice<L, R2> map(Function<? super R, ? extends R2> mapper);

  <L2> Choice<L2, R> mapLeft(Function<? super L, ? extends L2> mapper);

  Choice<R, L> swap();

  <R2> Choice<L, R2> flatMap(Function<? super R, ? extends Choice<L, R2>> mapper);
}
