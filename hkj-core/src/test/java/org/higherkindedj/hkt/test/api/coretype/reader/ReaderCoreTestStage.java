// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.reader;

import org.higherkindedj.hkt.reader.Reader;

/**
 * Stage 1: Configure Reader test instances.
 *
 * <p>Entry point for Reader core type testing with progressive disclosure.
 *
 * @param <R> The environment type
 * @param <A> The value type
 */
public final class ReaderCoreTestStage<R, A> {
  private final Class<?> contextClass;

  public ReaderCoreTestStage(Class<?> contextClass) {
    this.contextClass = contextClass;
  }

  /**
   * Provides a Reader instance for testing.
   *
   * <p>Progressive disclosure: Next step is {@code .withEnvironment(...)}
   *
   * @param readerInstance A Reader instance
   * @return Next stage for configuring environment
   */
  public ReaderInstanceStage<R, A> withReader(Reader<R, A> readerInstance) {
    return new ReaderInstanceStage<>(contextClass, readerInstance);
  }
}
