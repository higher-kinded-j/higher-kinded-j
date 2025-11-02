// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.validated;

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.test.api.coretype.common.BaseCoreTypeTestExecutor;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedKindHelper;
import org.higherkindedj.hkt.validated.ValidatedSelective;

/**
 * Internal executor for Validated Selective tests.
 *
 * <p>This class coordinates Selective test execution with error accumulation.
 *
 * @param <E> The error type
 * @param <A> The value type
 * @param <B> The result type
 */
final class ValidatedSelectiveTestExecutor<E, A, B>
    extends BaseCoreTypeTestExecutor<A, B, ValidatedSelectiveValidationStage<E, A, B>> {

  private static final ValidatedKindHelper VALIDATED = ValidatedKindHelper.VALIDATED;

  private final Validated<E, A> invalidInstance;
  private final Validated<E, A> validInstance;
  private final Validated<E, Choice<A, B>> choiceLeft;
  private final Validated<E, Choice<A, B>> choiceRight;
  private final Validated<E, Boolean> booleanTrue;
  private final Validated<E, Boolean> booleanFalse;
  private final Function<A, B> selectFunction;
  private final Function<A, B> leftHandler;
  private final Function<B, B> rightHandler;

  private final boolean includeSelect;
  private final boolean includeBranch;
  private final boolean includeWhenS;
  private final boolean includeIfS;

  private final ValidatedSelective<E> selective;

  ValidatedSelectiveTestExecutor(
      Class<?> contextClass,
      Validated<E, A> invalidInstance,
      Validated<E, A> validInstance,
      Validated<E, Choice<A, B>> choiceLeft,
      Validated<E, Choice<A, B>> choiceRight,
      Validated<E, Boolean> booleanTrue,
      Validated<E, Boolean> booleanFalse,
      Function<A, B> selectFunction,
      Function<A, B> leftHandler,
      Function<B, B> rightHandler,
      boolean includeSelect,
      boolean includeBranch,
      boolean includeWhenS,
      boolean includeIfS,
      boolean includeValidations,
      boolean includeEdgeCases) {
    this(
        contextClass,
        invalidInstance,
        validInstance,
        choiceLeft,
        choiceRight,
        booleanTrue,
        booleanFalse,
        selectFunction,
        leftHandler,
        rightHandler,
        includeSelect,
        includeBranch,
        includeWhenS,
        includeIfS,
        includeValidations,
        includeEdgeCases,
        null);
  }

  @SuppressWarnings("unchecked")
  ValidatedSelectiveTestExecutor(
      Class<?> contextClass,
      Validated<E, A> invalidInstance,
      Validated<E, A> validInstance,
      Validated<E, Choice<A, B>> choiceLeft,
      Validated<E, Choice<A, B>> choiceRight,
      Validated<E, Boolean> booleanTrue,
      Validated<E, Boolean> booleanFalse,
      Function<A, B> selectFunction,
      Function<A, B> leftHandler,
      Function<B, B> rightHandler,
      boolean includeSelect,
      boolean includeBranch,
      boolean includeWhenS,
      boolean includeIfS,
      boolean includeValidations,
      boolean includeEdgeCases,
      ValidatedSelectiveValidationStage<E, A, B> validationStage) {

    super(contextClass, selectFunction, includeValidations, includeEdgeCases, validationStage);

    this.invalidInstance = invalidInstance;
    this.validInstance = validInstance;
    this.choiceLeft = choiceLeft;
    this.choiceRight = choiceRight;
    this.booleanTrue = booleanTrue;
    this.booleanFalse = booleanFalse;
    this.selectFunction = selectFunction;
    this.leftHandler = leftHandler;
    this.rightHandler = rightHandler;
    this.includeSelect = includeSelect;
    this.includeBranch = includeBranch;
    this.includeWhenS = includeWhenS;
    this.includeIfS = includeIfS;

    // Use list semigroup for error accumulation in tests
    // We'll use string concatenation with ", " as in the Either tests
    this.selective = ValidatedSelective.instance((Semigroup<E>) Semigroups.string(", "));
  }

  @Override
  protected void executeOperationTests() {
    if (includeSelect) testSelect();
    if (includeBranch) testBranch();
    if (includeWhenS) testWhenS();
    if (includeIfS) testIfS();
  }

  @Override
  protected void executeValidationTests() {
    ValidationTestBuilder builder = ValidationTestBuilder.create();

    Kind<ValidatedKind.Witness<E>, Choice<A, B>> choiceKind = VALIDATED.widen(choiceLeft);
    Kind<ValidatedKind.Witness<E>, Function<A, B>> funcKind =
        VALIDATED.widen(Validated.valid(selectFunction));
    Kind<ValidatedKind.Witness<E>, Function<A, B>> leftHandlerKind =
        VALIDATED.widen(Validated.valid(leftHandler));
    Kind<ValidatedKind.Witness<E>, Function<B, B>> rightHandlerKind =
        VALIDATED.widen(Validated.valid(rightHandler));
    Kind<ValidatedKind.Witness<E>, Boolean> condKind = VALIDATED.widen(booleanTrue);
    // ✓ Create a Unit effect for whenS validation
    Kind<ValidatedKind.Witness<E>, Unit> unitEffectKind =
        VALIDATED.widen(Validated.valid(Unit.INSTANCE));

    // Select validations
    Class<?> selectCtx = getSelectContext();
    builder.assertKindNull(
        () -> selective.select(null, funcKind), selectCtx, Operation.SELECT, "choice");
    builder.assertKindNull(
        () -> selective.select(choiceKind, null), selectCtx, Operation.SELECT, "function");

    // Branch validations
    Class<?> branchCtx = getBranchContext();
    builder.assertKindNull(
        () -> selective.branch(null, leftHandlerKind, rightHandlerKind),
        branchCtx,
        Operation.BRANCH,
        "choice");
    builder.assertKindNull(
        () -> selective.branch(choiceKind, null, rightHandlerKind),
        branchCtx,
        Operation.BRANCH,
        "leftHandler");
    builder.assertKindNull(
        () -> selective.branch(choiceKind, leftHandlerKind, null),
        branchCtx,
        Operation.BRANCH,
        "rightHandler");

    // WhenS validations - now using Unit effect
    Class<?> whenSCtx = getWhenSContext();
    builder.assertKindNull(
        () -> selective.whenS(null, unitEffectKind), whenSCtx, Operation.WHEN_S, "condition");
    builder.assertKindNull(
        () -> selective.whenS(condKind, null), whenSCtx, Operation.WHEN_S, "effect");

    // IfS validations
    Kind<ValidatedKind.Witness<E>, A> effectKind = VALIDATED.widen(validInstance);
    Class<?> ifSCtx = getIfSContext();
    builder.assertKindNull(
        () -> selective.ifS(null, effectKind, effectKind), ifSCtx, Operation.IF_S, "condition");
    builder.assertKindNull(
        () -> selective.ifS(condKind, null, effectKind), ifSCtx, Operation.IF_S, "thenBranch");
    builder.assertKindNull(
        () -> selective.ifS(condKind, effectKind, null), ifSCtx, Operation.IF_S, "elseBranch");

    builder.execute();
  }

  @Override
  protected void executeEdgeCaseTests() {
    // Test with null values in Choice
    Validated<E, Choice<A, B>> choiceWithNull =
        Validated.valid(new org.higherkindedj.hkt.Selective.SimpleChoice<>(true, null, null));
    Kind<ValidatedKind.Witness<E>, Choice<A, B>> choiceKind = VALIDATED.widen(choiceWithNull);
    Kind<ValidatedKind.Witness<E>, Function<A, B>> funcKind =
        VALIDATED.widen(Validated.valid(a -> a == null ? null : selectFunction.apply(a)));

    assertThatCode(() -> selective.select(choiceKind, funcKind)).doesNotThrowAnyException();

    // Test toString
    assertThat(selective.toString()).isNotNull();
  }

  private void testSelect() {
    Kind<ValidatedKind.Witness<E>, Choice<A, B>> leftChoiceKind = VALIDATED.widen(choiceLeft);
    Kind<ValidatedKind.Witness<E>, Choice<A, B>> rightChoiceKind = VALIDATED.widen(choiceRight);
    Kind<ValidatedKind.Witness<E>, Function<A, B>> funcKind =
        VALIDATED.widen(Validated.valid(selectFunction));

    // Test select with Left (in Choice) - function should be applied
    Kind<ValidatedKind.Witness<E>, B> resultLeft = selective.select(leftChoiceKind, funcKind);
    Validated<E, B> validatedResultLeft = VALIDATED.narrow(resultLeft);
    assertThat(validatedResultLeft.isValid()).isTrue();

    // Test select with Right (in Choice) - function should NOT be applied
    Kind<ValidatedKind.Witness<E>, B> resultRight = selective.select(rightChoiceKind, funcKind);
    Validated<E, B> validatedResultRight = VALIDATED.narrow(resultRight);
    assertThat(validatedResultRight.isValid()).isTrue();

    // Test select with Validated.Invalid - should propagate error
    Validated<E, Choice<A, B>> errorChoice = Validated.invalid(invalidInstance.getError());
    Kind<ValidatedKind.Witness<E>, Choice<A, B>> errorChoiceKind = VALIDATED.widen(errorChoice);
    Kind<ValidatedKind.Witness<E>, B> errorResult = selective.select(errorChoiceKind, funcKind);
    Validated<E, B> validatedErrorResult = VALIDATED.narrow(errorResult);
    assertThat(validatedErrorResult.isInvalid()).isTrue();

    // Test select with both Invalid - should accumulate errors
    Validated<E, Function<A, B>> errorFunc = Validated.invalid(invalidInstance.getError());
    Kind<ValidatedKind.Witness<E>, Function<A, B>> errorFuncKind = VALIDATED.widen(errorFunc);
    Kind<ValidatedKind.Witness<E>, B> accumulatedResult =
        selective.select(errorChoiceKind, errorFuncKind);
    Validated<E, B> validatedAccumulatedResult = VALIDATED.narrow(accumulatedResult);
    assertThat(validatedAccumulatedResult.isInvalid()).isTrue();
  }

  private void testBranch() {
    Kind<ValidatedKind.Witness<E>, Choice<A, B>> leftChoiceKind = VALIDATED.widen(choiceLeft);
    Kind<ValidatedKind.Witness<E>, Choice<A, B>> rightChoiceKind = VALIDATED.widen(choiceRight);
    Kind<ValidatedKind.Witness<E>, Function<A, B>> leftHandlerKind =
        VALIDATED.widen(Validated.valid(leftHandler));
    Kind<ValidatedKind.Witness<E>, Function<B, B>> rightHandlerKind =
        VALIDATED.widen(Validated.valid(rightHandler));

    // Test branch with Left - left handler should be applied
    Kind<ValidatedKind.Witness<E>, B> resultLeft =
        selective.branch(leftChoiceKind, leftHandlerKind, rightHandlerKind);
    Validated<E, B> validatedResultLeft = VALIDATED.narrow(resultLeft);
    assertThat(validatedResultLeft.isValid()).isTrue();

    // Test branch with Right - right handler should be applied
    Kind<ValidatedKind.Witness<E>, B> resultRight =
        selective.branch(rightChoiceKind, leftHandlerKind, rightHandlerKind);
    Validated<E, B> validatedResultRight = VALIDATED.narrow(resultRight);
    assertThat(validatedResultRight.isValid()).isTrue();

    // Test branch with Validated.Invalid - should propagate error
    Validated<E, Choice<A, B>> errorChoice = Validated.invalid(invalidInstance.getError());
    Kind<ValidatedKind.Witness<E>, Choice<A, B>> errorChoiceKind = VALIDATED.widen(errorChoice);
    Kind<ValidatedKind.Witness<E>, B> errorResult =
        selective.branch(errorChoiceKind, leftHandlerKind, rightHandlerKind);
    Validated<E, B> validatedErrorResult = VALIDATED.narrow(errorResult);
    assertThat(validatedErrorResult.isInvalid()).isTrue();

    // Test branch with multiple Invalid - should accumulate all errors
    Validated<E, Function<A, B>> errorLeftHandler = Validated.invalid(invalidInstance.getError());
    Validated<E, Function<B, B>> errorRightHandler = Validated.invalid(invalidInstance.getError());
    Kind<ValidatedKind.Witness<E>, Function<A, B>> errorLeftHandlerKind =
        VALIDATED.widen(errorLeftHandler);
    Kind<ValidatedKind.Witness<E>, Function<B, B>> errorRightHandlerKind =
        VALIDATED.widen(errorRightHandler);
    Kind<ValidatedKind.Witness<E>, B> accumulatedResult =
        selective.branch(errorChoiceKind, errorLeftHandlerKind, errorRightHandlerKind);
    Validated<E, B> validatedAccumulatedResult = VALIDATED.narrow(accumulatedResult);
    assertThat(validatedAccumulatedResult.isInvalid()).isTrue();
  }

  private void testWhenS() {
    Kind<ValidatedKind.Witness<E>, Boolean> trueKind = VALIDATED.widen(booleanTrue);
    Kind<ValidatedKind.Witness<E>, Boolean> falseKind = VALIDATED.widen(booleanFalse);
    // ✓ Create a Unit effect for whenS testing
    Kind<ValidatedKind.Witness<E>, Unit> unitEffectKind =
        VALIDATED.widen(Validated.valid(Unit.INSTANCE));

    // Test whenS with true - effect should execute
    Kind<ValidatedKind.Witness<E>, Unit> resultTrue = selective.whenS(trueKind, unitEffectKind);
    Validated<E, Unit> validatedResultTrue = VALIDATED.narrow(resultTrue);
    assertThat(validatedResultTrue.isValid()).isTrue();
    assertThat(validatedResultTrue.get()).isEqualTo(Unit.INSTANCE);

    // Test whenS with false - effect should not execute, returns Valid(Unit.INSTANCE)
    Kind<ValidatedKind.Witness<E>, Unit> resultFalse = selective.whenS(falseKind, unitEffectKind);
    Validated<E, Unit> validatedResultFalse = VALIDATED.narrow(resultFalse);
    assertThat(validatedResultFalse.isValid()).isTrue();
    assertThat(validatedResultFalse.get()).isEqualTo(Unit.INSTANCE);

    // Test whenS with Validated.Invalid condition - should propagate error
    Validated<E, Boolean> errorCondition = Validated.invalid(invalidInstance.getError());
    Kind<ValidatedKind.Witness<E>, Boolean> errorCondKind = VALIDATED.widen(errorCondition);
    Kind<ValidatedKind.Witness<E>, Unit> errorResult =
        selective.whenS(errorCondKind, unitEffectKind);
    Validated<E, Unit> validatedErrorResult = VALIDATED.narrow(errorResult);
    assertThat(validatedErrorResult.isInvalid()).isTrue();

    // Test whenS with both Invalid - should accumulate errors
    Validated<E, Unit> errorEffect = Validated.invalid(invalidInstance.getError());
    Kind<ValidatedKind.Witness<E>, Unit> errorEffectKind = VALIDATED.widen(errorEffect);
    Kind<ValidatedKind.Witness<E>, Unit> accumulatedResult =
        selective.whenS(errorCondKind, errorEffectKind);
    Validated<E, Unit> validatedAccumulatedResult = VALIDATED.narrow(accumulatedResult);
    assertThat(validatedAccumulatedResult.isInvalid()).isTrue();
  }

  private void testIfS() {
    Kind<ValidatedKind.Witness<E>, Boolean> trueKind = VALIDATED.widen(booleanTrue);
    Kind<ValidatedKind.Witness<E>, Boolean> falseKind = VALIDATED.widen(booleanFalse);
    Kind<ValidatedKind.Witness<E>, A> thenKind = VALIDATED.widen(validInstance);
    Kind<ValidatedKind.Witness<E>, A> elseKind = VALIDATED.widen(validInstance);

    // Test ifS with true - should return then branch
    Kind<ValidatedKind.Witness<E>, A> resultTrue = selective.ifS(trueKind, thenKind, elseKind);
    Validated<E, A> validatedResultTrue = VALIDATED.narrow(resultTrue);
    assertThat(validatedResultTrue.isValid()).isTrue();
    assertThat(validatedResultTrue).isSameAs(validInstance);

    // Test ifS with false - should return else branch
    Kind<ValidatedKind.Witness<E>, A> resultFalse = selective.ifS(falseKind, thenKind, elseKind);
    Validated<E, A> validatedResultFalse = VALIDATED.narrow(resultFalse);
    assertThat(validatedResultFalse.isValid()).isTrue();
    assertThat(validatedResultFalse).isSameAs(validInstance);

    // Test ifS with Validated.Invalid condition - should propagate error
    Validated<E, Boolean> errorCondition = Validated.invalid(invalidInstance.getError());
    Kind<ValidatedKind.Witness<E>, Boolean> errorCondKind = VALIDATED.widen(errorCondition);
    Kind<ValidatedKind.Witness<E>, A> errorResult =
        selective.ifS(errorCondKind, thenKind, elseKind);
    Validated<E, A> validatedErrorResult = VALIDATED.narrow(errorResult);
    assertThat(validatedErrorResult.isInvalid()).isTrue();

    // Test ifS with multiple Invalid - should accumulate all errors
    Validated<E, A> errorThen = Validated.invalid(invalidInstance.getError());
    Validated<E, A> errorElse = Validated.invalid(invalidInstance.getError());
    Kind<ValidatedKind.Witness<E>, A> errorThenKind = VALIDATED.widen(errorThen);
    Kind<ValidatedKind.Witness<E>, A> errorElseKind = VALIDATED.widen(errorElse);
    Kind<ValidatedKind.Witness<E>, A> accumulatedResult =
        selective.ifS(errorCondKind, errorThenKind, errorElseKind);
    Validated<E, A> validatedAccumulatedResult = VALIDATED.narrow(accumulatedResult);
    assertThat(validatedAccumulatedResult.isInvalid()).isTrue();
  }

  private Class<?> getSelectContext() {
    return (validationStage != null && validationStage.getSelectContext() != null)
        ? validationStage.getSelectContext()
        : contextClass;
  }

  private Class<?> getBranchContext() {
    return (validationStage != null && validationStage.getBranchContext() != null)
        ? validationStage.getBranchContext()
        : contextClass;
  }

  private Class<?> getWhenSContext() {
    return (validationStage != null && validationStage.getWhenSContext() != null)
        ? validationStage.getWhenSContext()
        : contextClass;
  }

  private Class<?> getIfSContext() {
    return (validationStage != null && validationStage.getIfSContext() != null)
        ? validationStage.getIfSContext()
        : contextClass;
  }
}
