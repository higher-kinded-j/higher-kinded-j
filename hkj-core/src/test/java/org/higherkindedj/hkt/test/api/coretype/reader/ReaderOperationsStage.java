// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.reader;

import java.util.function.Function;
import org.higherkindedj.hkt.reader.Reader;

/**
 * Stage 3: Configure mapping functions for Reader testing.
 *
 * <p>Progressive disclosure: Shows mapper configuration and execution options.
 *
 * @param <R> The environment type
 * @param <A> The value type
 */
public final class ReaderOperationsStage<R, A> {
  private final Class<?> contextClass;
  private final Reader<R, A> readerInstance;
  private final R environment;

  ReaderOperationsStage(Class<?> contextClass, Reader<R, A> readerInstance, R environment) {
    this.contextClass = contextClass;
    this.readerInstance = readerInstance;
    this.environment = environment;
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
  public <B> ReaderTestConfigStage<R, A, B> withMappers(Function<A, B> mapper) {
    return new ReaderTestConfigStage<>(contextClass, readerInstance, environment, mapper);
  }

  /**
   * Skip mapper configuration and proceed to testing.
   *
   * <p>This is useful when you only want to test operations that don't require mappers (like run,
   * ask, constant).
   *
   * @return Configuration stage without mappers
   */
  public ReaderTestConfigStage<R, A, String> withoutMappers() {
    return new ReaderTestConfigStage<>(contextClass, readerInstance, environment, null);
  }

  /**
   * Configures Selective-specific test operations for Reader.
   *
   * <p>Progressive disclosure: Next step is {@code .withHandlers(...)}
   *
   * @param choiceLeft Reader containing Choice with Left value
   * @param choiceRight Reader containing Choice with Right value
   * @param booleanTrue Reader containing true
   * @param booleanFalse Reader containing false
   * @param <B> The result type for Selective operations
   * @return Stage for configuring Selective handlers
   */
  public <B> ReaderSelectiveStage<R, A, B> withSelectiveOperations(
      Reader<R, org.higherkindedj.hkt.Choice<A, B>> choiceLeft,
      Reader<R, org.higherkindedj.hkt.Choice<A, B>> choiceRight,
      Reader<R, Boolean> booleanTrue,
      Reader<R, Boolean> booleanFalse) {
    return new ReaderSelectiveStage<>(
        contextClass,
        readerInstance,
        environment,
        choiceLeft,
        choiceRight,
        booleanTrue,
        booleanFalse);
  }
}
