// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.writer;

import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.writer.Writer;

/**
 * Stage 2: Configure monoid for Writer testing.
 *
 * <p>Progressive disclosure: Shows monoid configuration.
 *
 * @param <W> The log type
 * @param <A> The value type
 */
public final class WriterInstanceStage<W, A> {
  private final Class<?> contextClass;
  private final Writer<W, A> writerInstance;

  WriterInstanceStage(Class<?> contextClass, Writer<W, A> writerInstance) {
    this.contextClass = contextClass;
    this.writerInstance = writerInstance;
  }

  /**
   * Provides the monoid for log combining.
   *
   * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
   *
   * @param monoid The monoid for combining logs
   * @return Next stage for configuring mappers
   */
  public WriterOperationsStage<W, A> withMonoid(Monoid<W> monoid) {
    return new WriterOperationsStage<>(contextClass, writerInstance, monoid);
  }
}
