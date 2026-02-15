// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either;

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherKindHelper;
import org.higherkindedj.hkt.either.EitherSelective;
import org.higherkindedj.hkt.test.api.coretype.common.BaseCoreTypeTestExecutor;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for Either Selective tests.
 *
 * <p>This class coordinates Selective test execution.
 *
 * @param <L> The Left type
 * @param <R> The Right type
 * @param <S> The result type
 */
final class EitherSelectiveTestExecutor<L, R, S>
    extends BaseCoreTypeTestExecutor<R, S, EitherSelectiveValidationStage<L, R, S>> {

  private static final EitherKindHelper EITHER = EitherKindHelper.EITHER;

  private final Either<L, R> leftInstance;
  private final Either<L, R> rightInstance;
  private final Either<L, Choice<R, S>> choiceLeft;
  private final Either<L, Choice<R, S>> choiceRight;
  private final Either<L, Boolean> booleanTrue;
  private final Either<L, Boolean> booleanFalse;
  private final Function<R, S> selectFunction;
  private final Function<R, S> leftHandler;
  private final Function<S, S> rightHandler;

  private final boolean includeSelect;
  private final boolean includeBranch;
  private final boolean includeWhenS;
  private final boolean includeIfS;

  private final EitherSelective<L> selective = EitherSelective.instance();

  EitherSelectiveTestExecutor(
      Class<?> contextClass,
      Either<L, R> leftInstance,
      Either<L, R> rightInstance,
      Either<L, Choice<R, S>> choiceLeft,
      Either<L, Choice<R, S>> choiceRight,
      Either<L, Boolean> booleanTrue,
      Either<L, Boolean> booleanFalse,
      Function<R, S> selectFunction,
      Function<R, S> leftHandler,
      Function<S, S> rightHandler,
      boolean includeSelect,
      boolean includeBranch,
      boolean includeWhenS,
      boolean includeIfS,
      boolean includeValidations,
      boolean includeEdgeCases) {
    this(
        contextClass,
        leftInstance,
        rightInstance,
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

  EitherSelectiveTestExecutor(
      Class<?> contextClass,
      Either<L, R> leftInstance,
      Either<L, R> rightInstance,
      Either<L, Choice<R, S>> choiceLeft,
      Either<L, Choice<R, S>> choiceRight,
      Either<L, Boolean> booleanTrue,
      Either<L, Boolean> booleanFalse,
      Function<R, S> selectFunction,
      Function<R, S> leftHandler,
      Function<S, S> rightHandler,
      boolean includeSelect,
      boolean includeBranch,
      boolean includeWhenS,
      boolean includeIfS,
      boolean includeValidations,
      boolean includeEdgeCases,
      EitherSelectiveValidationStage<L, R, S> validationStage) {

    super(contextClass, selectFunction, includeValidations, includeEdgeCases, validationStage);

    this.leftInstance = leftInstance;
    this.rightInstance = rightInstance;
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

    Kind<EitherKind.Witness<L>, Choice<R, S>> choiceKind = EITHER.widen(choiceLeft);
    Kind<EitherKind.Witness<L>, Function<R, S>> funcKind =
        EITHER.widen(Either.right(selectFunction));
    Kind<EitherKind.Witness<L>, Function<R, S>> leftHandlerKind =
        EITHER.widen(Either.right(leftHandler));
    Kind<EitherKind.Witness<L>, Function<S, S>> rightHandlerKind =
        EITHER.widen(Either.right(rightHandler));
    Kind<EitherKind.Witness<L>, Boolean> condKind = EITHER.widen(booleanTrue);
    // ✓ Create a Unit effect for whenS validation
    Kind<EitherKind.Witness<L>, Unit> unitEffectKind = EITHER.widen(Either.right(Unit.INSTANCE));

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
    Kind<EitherKind.Witness<L>, R> effectKind = EITHER.widen(rightInstance);
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
    Either<L, Choice<R, S>> choiceWithNull = Either.right(Selective.left(null));
    Kind<EitherKind.Witness<L>, Choice<R, S>> choiceKind = EITHER.widen(choiceWithNull);
    Kind<EitherKind.Witness<L>, Function<R, S>> funcKind =
        EITHER.widen(Either.right(r -> r == null ? null : selectFunction.apply(r)));

    assertThatCode(() -> selective.select(choiceKind, funcKind)).doesNotThrowAnyException();

    // Test toString
    assertThat(selective.toString()).isNotNull();
  }

  private void testSelect() {
    Kind<EitherKind.Witness<L>, Choice<R, S>> leftChoiceKind = EITHER.widen(choiceLeft);
    Kind<EitherKind.Witness<L>, Choice<R, S>> rightChoiceKind = EITHER.widen(choiceRight);
    Kind<EitherKind.Witness<L>, Function<R, S>> funcKind =
        EITHER.widen(Either.right(selectFunction));

    // Test select with Left (in Choice) - function should be applied
    Kind<EitherKind.Witness<L>, S> resultLeft = selective.select(leftChoiceKind, funcKind);
    Either<L, S> eitherResultLeft = EITHER.narrow(resultLeft);
    assertThat(eitherResultLeft.isRight()).isTrue();

    // Test select with Right (in Choice) - function should NOT be applied
    Kind<EitherKind.Witness<L>, S> resultRight = selective.select(rightChoiceKind, funcKind);
    Either<L, S> eitherResultRight = EITHER.narrow(resultRight);
    assertThat(eitherResultRight.isRight()).isTrue();

    // Test select with Either.Left - should propagate error
    Either<L, Choice<R, S>> errorChoice = Either.left(leftInstance.getLeft());
    Kind<EitherKind.Witness<L>, Choice<R, S>> errorChoiceKind = EITHER.widen(errorChoice);
    Kind<EitherKind.Witness<L>, S> errorResult = selective.select(errorChoiceKind, funcKind);
    Either<L, S> eitherErrorResult = EITHER.narrow(errorResult);
    assertThat(eitherErrorResult.isLeft()).isTrue();
  }

  private void testBranch() {
    Kind<EitherKind.Witness<L>, Choice<R, S>> leftChoiceKind = EITHER.widen(choiceLeft);
    Kind<EitherKind.Witness<L>, Choice<R, S>> rightChoiceKind = EITHER.widen(choiceRight);
    Kind<EitherKind.Witness<L>, Function<R, S>> leftHandlerKind =
        EITHER.widen(Either.right(leftHandler));
    Kind<EitherKind.Witness<L>, Function<S, S>> rightHandlerKind =
        EITHER.widen(Either.right(rightHandler));

    // Test branch with Left - left handler should be applied
    Kind<EitherKind.Witness<L>, S> resultLeft =
        selective.branch(leftChoiceKind, leftHandlerKind, rightHandlerKind);
    Either<L, S> eitherResultLeft = EITHER.narrow(resultLeft);
    assertThat(eitherResultLeft.isRight()).isTrue();

    // Test branch with Right - right handler should be applied
    Kind<EitherKind.Witness<L>, S> resultRight =
        selective.branch(rightChoiceKind, leftHandlerKind, rightHandlerKind);
    Either<L, S> eitherResultRight = EITHER.narrow(resultRight);
    assertThat(eitherResultRight.isRight()).isTrue();

    // Test branch with Either.Left - should propagate error
    Either<L, Choice<R, S>> errorChoice = Either.left(leftInstance.getLeft());
    Kind<EitherKind.Witness<L>, Choice<R, S>> errorChoiceKind = EITHER.widen(errorChoice);
    Kind<EitherKind.Witness<L>, S> errorResult =
        selective.branch(errorChoiceKind, leftHandlerKind, rightHandlerKind);
    Either<L, S> eitherErrorResult = EITHER.narrow(errorResult);
    assertThat(eitherErrorResult.isLeft()).isTrue();
  }

  private void testWhenS() {
    Kind<EitherKind.Witness<L>, Boolean> trueKind = EITHER.widen(booleanTrue);
    Kind<EitherKind.Witness<L>, Boolean> falseKind = EITHER.widen(booleanFalse);
    // ✓ Create a Unit effect for whenS testing
    Kind<EitherKind.Witness<L>, Unit> unitEffectKind = EITHER.widen(Either.right(Unit.INSTANCE));

    // Test whenS with true - effect should execute
    Kind<EitherKind.Witness<L>, Unit> resultTrue = selective.whenS(trueKind, unitEffectKind);
    Either<L, Unit> eitherResultTrue = EITHER.narrow(resultTrue);
    assertThat(eitherResultTrue.isRight()).isTrue();
    assertThat(eitherResultTrue.getRight()).isEqualTo(Unit.INSTANCE);

    // Test whenS with false - effect should not execute
    Kind<EitherKind.Witness<L>, Unit> resultFalse = selective.whenS(falseKind, unitEffectKind);
    Either<L, Unit> eitherResultFalse = EITHER.narrow(resultFalse);
    assertThat(eitherResultFalse.isRight()).isTrue();
    assertThat(eitherResultFalse.getRight()).isEqualTo(Unit.INSTANCE);

    // Test whenS with Either.Left condition - should propagate error
    Either<L, Boolean> errorCondition = Either.left(leftInstance.getLeft());
    Kind<EitherKind.Witness<L>, Boolean> errorCondKind = EITHER.widen(errorCondition);
    Kind<EitherKind.Witness<L>, Unit> errorResult = selective.whenS(errorCondKind, unitEffectKind);
    Either<L, Unit> eitherErrorResult = EITHER.narrow(errorResult);
    assertThat(eitherErrorResult.isLeft()).isTrue();
  }

  private void testIfS() {
    Kind<EitherKind.Witness<L>, Boolean> trueKind = EITHER.widen(booleanTrue);
    Kind<EitherKind.Witness<L>, Boolean> falseKind = EITHER.widen(booleanFalse);
    Kind<EitherKind.Witness<L>, R> thenKind = EITHER.widen(rightInstance);
    Kind<EitherKind.Witness<L>, R> elseKind = EITHER.widen(rightInstance);

    // Test ifS with true - should return then branch
    Kind<EitherKind.Witness<L>, R> resultTrue = selective.ifS(trueKind, thenKind, elseKind);
    Either<L, R> eitherResultTrue = EITHER.narrow(resultTrue);
    assertThat(eitherResultTrue.isRight()).isTrue();
    assertThat(eitherResultTrue).isSameAs(rightInstance);

    // Test ifS with false - should return else branch
    Kind<EitherKind.Witness<L>, R> resultFalse = selective.ifS(falseKind, thenKind, elseKind);
    Either<L, R> eitherResultFalse = EITHER.narrow(resultFalse);
    assertThat(eitherResultFalse.isRight()).isTrue();
    assertThat(eitherResultFalse).isSameAs(rightInstance);

    // Test ifS with Either.Left condition - should propagate error
    Either<L, Boolean> errorCondition = Either.left(leftInstance.getLeft());
    Kind<EitherKind.Witness<L>, Boolean> errorCondKind = EITHER.widen(errorCondition);
    Kind<EitherKind.Witness<L>, R> errorResult = selective.ifS(errorCondKind, thenKind, elseKind);
    Either<L, R> eitherErrorResult = EITHER.narrow(errorResult);
    assertThat(eitherErrorResult.isLeft()).isTrue();
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
