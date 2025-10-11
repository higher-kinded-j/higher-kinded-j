// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.writer;

import java.util.function.Function;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.writer.Writer;

/**
 * Stage 3: Configure mapping functions for Writer testing.
 *
 * <p>Progressive disclosure: Shows mapper configuration and execution options.
 *
 * @param <W> The log type
 * @param <A> The value type
 */
public final class WriterOperationsStage<W, A> {
  private final Class<?> contextClass;
  private final Writer<W, A> writerInstance;
  private final Monoid<W> monoid;

  WriterOperationsStage(Class<?> contextClass, Writer<W, A> writerInstance, Monoid<W> monoid) {
    this.contextClass = contextClass;
    this.writerInstance = writerInstance;
    this.monoid = monoid;
  }

  /**
   * Provides mapping functions for testing map and flatMap operations.
   *
   * <p>Progressive disclosure: Next steps are test selection or execution.
   *
   * @param mapper The mapping function (A -> B)
   * @param <B> The mapped type
   * @return Configuration stage with execution options
   */
  public <B> WriterTestConfigStage<W, A, B> withMappers(Function<A, B> mapper) {
    return new WriterTestConfigStage<>(contextClass, writerInstance, monoid, mapper);
  }

  /**
   * Skip mapper configuration and proceed to testing.
   *
   * <p>This is useful when you only want to test operations that don't require mappers (like run,
   * exec, value, tell).
   *
   * @return Configuration stage without mappers
   */
  public WriterTestConfigStage<W, A, String> withoutMappers() {
    return new WriterTestConfigStage<>(contextClass, writerInstance, monoid, null);
  }
}
