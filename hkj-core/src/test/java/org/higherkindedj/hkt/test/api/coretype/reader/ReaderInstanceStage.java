// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.reader;

import org.higherkindedj.hkt.reader.Reader;

/**
 * Stage 2: Configure environment for Reader testing.
 *
 * <p>Progressive disclosure: Shows environment configuration.
 *
 * @param <R> The environment type
 * @param <A> The value type
 */
public final class ReaderInstanceStage<R, A> {
  private final Class<?> contextClass;
  private final Reader<R, A> readerInstance;

  ReaderInstanceStage(Class<?> contextClass, Reader<R, A> readerInstance) {
    this.contextClass = contextClass;
    this.readerInstance = readerInstance;
  }

  /**
   * Provides the environment for testing.
   *
   * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
   *
   * @param environment The environment to provide to the Reader
   * @return Next stage for configuring mappers
   */
  public ReaderOperationsStage<R, A> withEnvironment(R environment) {
    return new ReaderOperationsStage<>(contextClass, readerInstance, environment);
  }
}
