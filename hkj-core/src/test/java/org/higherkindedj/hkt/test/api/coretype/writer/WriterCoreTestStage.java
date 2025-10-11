// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.writer;

import org.higherkindedj.hkt.writer.Writer;

/**
 * Stage 1: Configure Writer test instances.
 *
 * <p>Entry point for Writer core type testing with progressive disclosure.
 *
 * @param <W> The log type
 * @param <A> The value type
 */
public final class WriterCoreTestStage<W, A> {
  private final Class<?> contextClass;

  public WriterCoreTestStage(Class<?> contextClass) {
    this.contextClass = contextClass;
  }

  /**
   * Provides a Writer instance for testing.
   *
   * <p>Progressive disclosure: Next step is {@code .withMonoid(...)}
   *
   * @param writerInstance A Writer instance
   * @return Next stage for configuring monoid
   */
  public WriterInstanceStage<W, A> withWriter(Writer<W, A> writerInstance) {
    return new WriterInstanceStage<>(contextClass, writerInstance);
  }
}
