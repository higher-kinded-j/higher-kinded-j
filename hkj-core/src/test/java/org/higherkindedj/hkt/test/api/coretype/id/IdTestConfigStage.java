// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.id;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTestConfigStage;

/**
 * Stage 3: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class IdTestConfigStage<A, B> extends BaseTestConfigStage<IdTestConfigStage<A, B>> {

  private final Class<?> contextClass;
  private final Id<A> instance;
  private final Function<A, B> mapper;

  // Type-specific test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeGetters = true;
  private boolean includeMap = true;
  private boolean includeFlatMap = true;

  IdTestConfigStage(Class<?> contextClass, Id<A> instance, Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.instance = instance;
    this.mapper = mapper;
  }

  // =============================================================================
  // BaseTestConfigStage Implementation
  // =============================================================================

  @Override
  protected IdTestConfigStage<A, B> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public IdValidationStage<A, B> configureValidation() {
    return new IdValidationStage<>(this);
  }

  @Override
  public IdTestConfigStage<A, B> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  @Override
  public IdTestConfigStage<A, B> onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return this;
  }

  @Override
  protected void disableAll() {
    super.disableAll();
    includeFactoryMethods = false;
    includeGetters = false;
    includeMap = false;
    includeFlatMap = false;
  }

  // =============================================================================
  // Type-Specific Test Selection Methods
  // =============================================================================

  public IdTestConfigStage<A, B> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public IdTestConfigStage<A, B> skipGetters() {
    this.includeGetters = false;
    return this;
  }

  public IdTestConfigStage<A, B> skipMap() {
    this.includeMap = false;
    return this;
  }

  public IdTestConfigStage<A, B> skipFlatMap() {
    this.includeFlatMap = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public IdTestConfigStage<A, B> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public IdTestConfigStage<A, B> onlyGetters() {
    disableAll();
    this.includeGetters = true;
    return this;
  }

  public IdTestConfigStage<A, B> onlyMap() {
    disableAll();
    this.includeMap = true;
    return this;
  }

  public IdTestConfigStage<A, B> onlyFlatMap() {
    disableAll();
    this.includeFlatMap = true;
    return this;
  }

  /**
   * Configures Selective-specific test operations for Id.
   *
   * <p>Progressive disclosure: Next step is {@code .withHandlers(...)}
   *
   * @param choiceLeft Id containing Choice with Left value
   * @param choiceRight Id containing Choice with Right value
   * @param booleanTrue Id containing true
   * @param booleanFalse Id containing false
   * @param <S> The result type for Selective operations
   * @return Stage for configuring Selective handlers
   */
  public <S> IdSelectiveStage<A, S> withSelectiveOperations(
      Id<Choice<A, S>> choiceLeft,
      Id<Choice<A, S>> choiceRight,
      Id<Boolean> booleanTrue,
      Id<Boolean> booleanFalse) {

    return new IdSelectiveStage<>(
        contextClass, instance, choiceLeft, choiceRight, booleanTrue, booleanFalse);
  }

  private IdTestExecutor<A, B> buildExecutor() {
    return new IdTestExecutor<>(
        contextClass,
        instance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases);
  }

  IdTestExecutor<A, B> buildExecutorWithValidation(IdValidationStage<A, B> validationStage) {
    return new IdTestExecutor<>(
        contextClass,
        instance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
