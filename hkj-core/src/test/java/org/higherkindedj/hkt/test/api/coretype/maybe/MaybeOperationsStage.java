// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * Stage 3: Configure mapping function for Maybe testing.
 *
 * <p>Progressive disclosure: Shows mapper configuration and execution options.
 *
 * @param <T> The value type
 */
public final class MaybeOperationsStage<T> {
  private final Class<?> contextClass;
  private final Maybe<T> justInstance;
  private final Maybe<T> nothingInstance;

  MaybeOperationsStage(Class<?> contextClass, Maybe<T> justInstance, Maybe<T> nothingInstance) {
    this.contextClass = contextClass;
    this.justInstance = justInstance;
    this.nothingInstance = nothingInstance;
  }

  /**
   * Provides mapping function for testing map and flatMap operations.
   *
   * <p>Progressive disclosure: Next steps are test selection or execution.
   *
   * @param mapper The mapping function (T -> S)
   * @param <S> The mapped type
   * @return Configuration stage with execution options
   */
  public <S> MaybeTestConfigStage<T, S> withMapper(Function<T, S> mapper) {
    return new MaybeTestConfigStage<>(contextClass, justInstance, nothingInstance, mapper);
  }

  /**
   * Skip mapper configuration and proceed to testing.
   *
   * <p>This is useful when you only want to test operations that don't require mappers (like get,
   * orElse, orElseGet, isJust, isNothing).
   *
   * @return Configuration stage without mapper
   */
  public MaybeTestConfigStage<T, String> withoutMapper() {
    return new MaybeTestConfigStage<>(contextClass, justInstance, nothingInstance, null);
  }

  /**
   * Configures Selective-specific test operations for Maybe.
   *
   * <p>Progressive disclosure: Next step is {@code .withHandlers(...)}
   *
   * @param choiceLeft Maybe containing Choice with Left value
   * @param choiceRight Maybe containing Choice with Right value
   * @param booleanTrue Maybe containing true
   * @param booleanFalse Maybe containing false
   * @param <S> The result type for Selective operations
   * @return Stage for configuring Selective handlers
   */
  public <S> MaybeSelectiveStage<T, S> withSelectiveOperations(
      Maybe<Choice<T, S>> choiceLeft,
      Maybe<Choice<T, S>> choiceRight,
      Maybe<Boolean> booleanTrue,
      Maybe<Boolean> booleanFalse) {

    return new MaybeSelectiveStage<>(
        contextClass,
        justInstance,
        nothingInstance,
        choiceLeft,
        choiceRight,
        booleanTrue,
        booleanFalse);
  }
}
