// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe;

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeSelective;
import org.higherkindedj.hkt.test.api.coretype.common.BaseCoreTypeTestExecutor;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for Maybe Selective tests.
 *
 * <p>This class coordinates Selective test execution.
 *
 * @param <T> The value type
 * @param <S> The result type
 */
final class MaybeSelectiveTestExecutor<T, S>
    extends BaseCoreTypeTestExecutor<T, S, MaybeSelectiveValidationStage<T, S>> {

  private static final MaybeKindHelper MAYBE = MaybeKindHelper.MAYBE;

  private final Maybe<T> justInstance;
  private final Maybe<T> nothingInstance;
  private final Maybe<Choice<T, S>> choiceLeft;
  private final Maybe<Choice<T, S>> choiceRight;
  private final Maybe<Boolean> booleanTrue;
  private final Maybe<Boolean> booleanFalse;
  private final Function<T, S> selectFunction;
  private final Function<T, S> leftHandler;
  private final Function<S, S> rightHandler;

  private final boolean includeSelect;
  private final boolean includeBranch;
  private final boolean includeWhenS;
  private final boolean includeIfS;

  private final MaybeSelective selective = MaybeSelective.INSTANCE;

  MaybeSelectiveTestExecutor(
      Class<?> contextClass,
      Maybe<T> justInstance,
      Maybe<T> nothingInstance,
      Maybe<Choice<T, S>> choiceLeft,
      Maybe<Choice<T, S>> choiceRight,
      Maybe<Boolean> booleanTrue,
      Maybe<Boolean> booleanFalse,
      Function<T, S> selectFunction,
      Function<T, S> leftHandler,
      Function<S, S> rightHandler,
      boolean includeSelect,
      boolean includeBranch,
      boolean includeWhenS,
      boolean includeIfS,
      boolean includeValidations,
      boolean includeEdgeCases) {
    this(
        contextClass,
        justInstance,
        nothingInstance,
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

  MaybeSelectiveTestExecutor(
      Class<?> contextClass,
      Maybe<T> justInstance,
      Maybe<T> nothingInstance,
      Maybe<Choice<T, S>> choiceLeft,
      Maybe<Choice<T, S>> choiceRight,
      Maybe<Boolean> booleanTrue,
      Maybe<Boolean> booleanFalse,
      Function<T, S> selectFunction,
      Function<T, S> leftHandler,
      Function<S, S> rightHandler,
      boolean includeSelect,
      boolean includeBranch,
      boolean includeWhenS,
      boolean includeIfS,
      boolean includeValidations,
      boolean includeEdgeCases,
      MaybeSelectiveValidationStage<T, S> validationStage) {

    super(contextClass, selectFunction, includeValidations, includeEdgeCases, validationStage);

    this.justInstance = justInstance;
    this.nothingInstance = nothingInstance;
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

    Kind<MaybeKind.Witness, Choice<T, S>> choiceKind = MAYBE.widen(choiceLeft);
    Kind<MaybeKind.Witness, Function<T, S>> funcKind = MAYBE.widen(Maybe.just(selectFunction));
    Kind<MaybeKind.Witness, Function<T, S>> leftHandlerKind = MAYBE.widen(Maybe.just(leftHandler));
    Kind<MaybeKind.Witness, Function<S, S>> rightHandlerKind =
        MAYBE.widen(Maybe.just(rightHandler));
    Kind<MaybeKind.Witness, Boolean> condKind = MAYBE.widen(booleanTrue);
    // ✓ Create a Unit effect for whenS validation
    Kind<MaybeKind.Witness, Unit> unitEffectKind = MAYBE.widen(Maybe.just(Unit.INSTANCE));

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
    Kind<MaybeKind.Witness, T> effectKind = MAYBE.widen(justInstance);
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
    Maybe<Choice<T, S>> choiceWithNull = Maybe.just(new Selective.SimpleChoice<>(true, null, null));
    Kind<MaybeKind.Witness, Choice<T, S>> choiceKind = MAYBE.widen(choiceWithNull);
    Kind<MaybeKind.Witness, Function<T, S>> funcKind =
        MAYBE.widen(Maybe.just(t -> t == null ? null : selectFunction.apply(t)));

    assertThatCode(() -> selective.select(choiceKind, funcKind)).doesNotThrowAnyException();

    // Test toString
    assertThat(selective.toString()).isNotNull();
  }

  private void testSelect() {
    Kind<MaybeKind.Witness, Choice<T, S>> leftChoiceKind = MAYBE.widen(choiceLeft);
    Kind<MaybeKind.Witness, Choice<T, S>> rightChoiceKind = MAYBE.widen(choiceRight);
    Kind<MaybeKind.Witness, Function<T, S>> funcKind = MAYBE.widen(Maybe.just(selectFunction));

    // Test select with Left (in Choice) - function should be applied
    Kind<MaybeKind.Witness, S> resultLeft = selective.select(leftChoiceKind, funcKind);
    Maybe<S> maybeResultLeft = MAYBE.narrow(resultLeft);
    assertThat(maybeResultLeft.isJust()).isTrue();

    // Test select with Right (in Choice) - function should NOT be applied
    Kind<MaybeKind.Witness, S> resultRight = selective.select(rightChoiceKind, funcKind);
    Maybe<S> maybeResultRight = MAYBE.narrow(resultRight);
    assertThat(maybeResultRight.isJust()).isTrue();

    // Test select with Nothing - should propagate Nothing
    Maybe<Choice<T, S>> errorChoice = Maybe.nothing();
    Kind<MaybeKind.Witness, Choice<T, S>> errorChoiceKind = MAYBE.widen(errorChoice);
    Kind<MaybeKind.Witness, S> errorResult = selective.select(errorChoiceKind, funcKind);
    Maybe<S> maybeErrorResult = MAYBE.narrow(errorResult);
    assertThat(maybeErrorResult.isNothing()).isTrue();
  }

  private void testBranch() {
    Kind<MaybeKind.Witness, Choice<T, S>> leftChoiceKind = MAYBE.widen(choiceLeft);
    Kind<MaybeKind.Witness, Choice<T, S>> rightChoiceKind = MAYBE.widen(choiceRight);
    Kind<MaybeKind.Witness, Function<T, S>> leftHandlerKind = MAYBE.widen(Maybe.just(leftHandler));
    Kind<MaybeKind.Witness, Function<S, S>> rightHandlerKind =
        MAYBE.widen(Maybe.just(rightHandler));

    // Test branch with Left - left handler should be applied
    Kind<MaybeKind.Witness, S> resultLeft =
        selective.branch(leftChoiceKind, leftHandlerKind, rightHandlerKind);
    Maybe<S> maybeResultLeft = MAYBE.narrow(resultLeft);
    assertThat(maybeResultLeft.isJust()).isTrue();

    // Test branch with Right - right handler should be applied
    Kind<MaybeKind.Witness, S> resultRight =
        selective.branch(rightChoiceKind, leftHandlerKind, rightHandlerKind);
    Maybe<S> maybeResultRight = MAYBE.narrow(resultRight);
    assertThat(maybeResultRight.isJust()).isTrue();

    // Test branch with Nothing - should propagate Nothing
    Maybe<Choice<T, S>> errorChoice = Maybe.nothing();
    Kind<MaybeKind.Witness, Choice<T, S>> errorChoiceKind = MAYBE.widen(errorChoice);
    Kind<MaybeKind.Witness, S> errorResult =
        selective.branch(errorChoiceKind, leftHandlerKind, rightHandlerKind);
    Maybe<S> maybeErrorResult = MAYBE.narrow(errorResult);
    assertThat(maybeErrorResult.isNothing()).isTrue();
  }

  private void testWhenS() {
    Kind<MaybeKind.Witness, Boolean> trueKind = MAYBE.widen(booleanTrue);
    Kind<MaybeKind.Witness, Boolean> falseKind = MAYBE.widen(booleanFalse);
    // ✓ Create a Unit effect for whenS testing
    Kind<MaybeKind.Witness, Unit> unitEffectKind = MAYBE.widen(Maybe.just(Unit.INSTANCE));

    // Test whenS with true - effect should execute
    Kind<MaybeKind.Witness, Unit> resultTrue = selective.whenS(trueKind, unitEffectKind);
    Maybe<Unit> maybeResultTrue = MAYBE.narrow(resultTrue);
    assertThat(maybeResultTrue.isJust()).isTrue();
    assertThat(maybeResultTrue.get()).isEqualTo(Unit.INSTANCE);

    // Test whenS with false - effect should not execute (returns Just(Unit.INSTANCE))
    Kind<MaybeKind.Witness, Unit> resultFalse = selective.whenS(falseKind, unitEffectKind);
    Maybe<Unit> maybeResultFalse = MAYBE.narrow(resultFalse);
    assertThat(maybeResultFalse.isJust()).isTrue();
    assertThat(maybeResultFalse.get()).isEqualTo(Unit.INSTANCE);

    // Test whenS with Nothing condition - should propagate Nothing
    Maybe<Boolean> errorCondition = Maybe.nothing();
    Kind<MaybeKind.Witness, Boolean> errorCondKind = MAYBE.widen(errorCondition);
    Kind<MaybeKind.Witness, Unit> errorResult = selective.whenS(errorCondKind, unitEffectKind);
    Maybe<Unit> maybeErrorResult = MAYBE.narrow(errorResult);
    assertThat(maybeErrorResult.isNothing()).isTrue();
  }

  private void testIfS() {
    Kind<MaybeKind.Witness, Boolean> trueKind = MAYBE.widen(booleanTrue);
    Kind<MaybeKind.Witness, Boolean> falseKind = MAYBE.widen(booleanFalse);
    Kind<MaybeKind.Witness, T> thenKind = MAYBE.widen(justInstance);
    Kind<MaybeKind.Witness, T> elseKind = MAYBE.widen(justInstance);

    // Test ifS with true - should return then branch
    Kind<MaybeKind.Witness, T> resultTrue = selective.ifS(trueKind, thenKind, elseKind);
    Maybe<T> maybeResultTrue = MAYBE.narrow(resultTrue);
    assertThat(maybeResultTrue.isJust()).isTrue();
    assertThat(resultTrue).isSameAs(thenKind);

    // Test ifS with false - should return else branch
    Kind<MaybeKind.Witness, T> resultFalse = selective.ifS(falseKind, thenKind, elseKind);
    Maybe<T> maybeResultFalse = MAYBE.narrow(resultFalse);
    assertThat(maybeResultFalse.isJust()).isTrue();
    assertThat(resultFalse).isSameAs(elseKind);

    // Test ifS with Nothing condition - should propagate Nothing
    Maybe<Boolean> errorCondition = Maybe.nothing();
    Kind<MaybeKind.Witness, Boolean> errorCondKind = MAYBE.widen(errorCondition);
    Kind<MaybeKind.Witness, T> errorResult = selective.ifS(errorCondKind, thenKind, elseKind);
    Maybe<T> maybeErrorResult = MAYBE.narrow(errorResult);
    assertThat(maybeErrorResult.isNothing()).isTrue();
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
