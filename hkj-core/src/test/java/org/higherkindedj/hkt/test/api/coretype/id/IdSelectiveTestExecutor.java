// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.id;

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdSelective;
import org.higherkindedj.hkt.test.api.coretype.common.BaseCoreTypeTestExecutor;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for Id Selective tests.
 *
 * <p>This class coordinates Selective test execution.
 *
 * @param <A> The value type
 * @param <B> The result type
 */
final class IdSelectiveTestExecutor<A, B>
    extends BaseCoreTypeTestExecutor<A, B, IdSelectiveValidationStage<A, B>> {

  private static final IdKindHelper ID = IdKindHelper.ID;

  private final Id<A> instance;
  private final Id<Choice<A, B>> choiceLeft;
  private final Id<Choice<A, B>> choiceRight;
  private final Id<Boolean> booleanTrue;
  private final Id<Boolean> booleanFalse;
  private final Function<A, B> selectFunction;
  private final Function<A, B> leftHandler;
  private final Function<B, B> rightHandler;

  private final boolean includeSelect;
  private final boolean includeBranch;
  private final boolean includeWhenS;
  private final boolean includeIfS;

  private final IdSelective selective = IdSelective.instance();

  IdSelectiveTestExecutor(
      Class<?> contextClass,
      Id<A> instance,
      Id<Choice<A, B>> choiceLeft,
      Id<Choice<A, B>> choiceRight,
      Id<Boolean> booleanTrue,
      Id<Boolean> booleanFalse,
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
        instance,
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

  IdSelectiveTestExecutor(
      Class<?> contextClass,
      Id<A> instance,
      Id<Choice<A, B>> choiceLeft,
      Id<Choice<A, B>> choiceRight,
      Id<Boolean> booleanTrue,
      Id<Boolean> booleanFalse,
      Function<A, B> selectFunction,
      Function<A, B> leftHandler,
      Function<B, B> rightHandler,
      boolean includeSelect,
      boolean includeBranch,
      boolean includeWhenS,
      boolean includeIfS,
      boolean includeValidations,
      boolean includeEdgeCases,
      IdSelectiveValidationStage<A, B> validationStage) {

    super(contextClass, selectFunction, includeValidations, includeEdgeCases, validationStage);

    this.instance = instance;
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

    Kind<Id.Witness, Choice<A, B>> choiceKind = ID.widen(choiceLeft);
    Kind<Id.Witness, Function<A, B>> funcKind = ID.widen(Id.of(selectFunction));
    Kind<Id.Witness, Function<A, B>> leftHandlerKind = ID.widen(Id.of(leftHandler));
    Kind<Id.Witness, Function<B, B>> rightHandlerKind = ID.widen(Id.of(rightHandler));
    Kind<Id.Witness, Boolean> condKind = ID.widen(booleanTrue);
    Kind<Id.Witness, Unit> unitEffectKind = ID.widen(Id.of(Unit.INSTANCE));

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

    // WhenS validations
    Class<?> whenSCtx = getWhenSContext();
    builder.assertKindNull(
        () -> selective.whenS(null, unitEffectKind), whenSCtx, Operation.WHEN_S, "condition");
    builder.assertKindNull(
        () -> selective.whenS(condKind, null), whenSCtx, Operation.WHEN_S, "effect");

    // IfS validations
    Kind<Id.Witness, A> effectKind = ID.widen(instance);
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
    // Test with null values in Choice - now causes NPE as expected
    Id<Choice<A, B>> choiceWithNull = Id.of(null);
    Kind<Id.Witness, Choice<A, B>> choiceKind = ID.widen(choiceWithNull);
    Kind<Id.Witness, Function<A, B>> funcKind = ID.widen(Id.of(selectFunction));

    // Should throw NPE for null choice value (validated by Validation.function().requireFunction)
    assertThatThrownBy(() -> selective.select(choiceKind, funcKind))
        .isInstanceOf(NullPointerException.class);

    // Test with null function value
    Id<Function<A, B>> nullFunc = Id.of(null);
    Kind<Id.Witness, Function<A, B>> nullFuncKind = ID.widen(nullFunc);

    assertThatThrownBy(() -> selective.select(choiceKind, nullFuncKind))
        .isInstanceOf(NullPointerException.class);

    // Test toString
    assertThat(selective.toString()).isEqualTo("IdSelective");
  }

  private void testSelect() {
    Kind<Id.Witness, Choice<A, B>> leftChoiceKind = ID.widen(choiceLeft);
    Kind<Id.Witness, Choice<A, B>> rightChoiceKind = ID.widen(choiceRight);
    Kind<Id.Witness, Function<A, B>> funcKind = ID.widen(Id.of(selectFunction));

    // Test select with Left (in Choice) - function should be applied
    Kind<Id.Witness, B> resultLeft = selective.select(leftChoiceKind, funcKind);
    Id<B> idResultLeft = ID.narrow(resultLeft);
    assertThat(idResultLeft).isNotNull();
    assertThat(idResultLeft.value()).isNotNull();

    // Test select with Right (in Choice) - function should NOT be applied
    Kind<Id.Witness, B> resultRight = selective.select(rightChoiceKind, funcKind);
    Id<B> idResultRight = ID.narrow(resultRight);
    assertThat(idResultRight).isNotNull();
    assertThat(idResultRight.value()).isNotNull();
  }

  private void testBranch() {
    Kind<Id.Witness, Choice<A, B>> leftChoiceKind = ID.widen(choiceLeft);
    Kind<Id.Witness, Choice<A, B>> rightChoiceKind = ID.widen(choiceRight);
    Kind<Id.Witness, Function<A, B>> leftHandlerKind = ID.widen(Id.of(leftHandler));
    Kind<Id.Witness, Function<B, B>> rightHandlerKind = ID.widen(Id.of(rightHandler));

    // Test branch with Left - left handler should be applied
    Kind<Id.Witness, B> resultLeft =
        selective.branch(leftChoiceKind, leftHandlerKind, rightHandlerKind);
    Id<B> idResultLeft = ID.narrow(resultLeft);
    assertThat(idResultLeft).isNotNull();
    assertThat(idResultLeft.value()).isNotNull();

    // Test branch with Right - right handler should be applied
    Kind<Id.Witness, B> resultRight =
        selective.branch(rightChoiceKind, leftHandlerKind, rightHandlerKind);
    Id<B> idResultRight = ID.narrow(resultRight);
    assertThat(idResultRight).isNotNull();
    assertThat(idResultRight.value()).isNotNull();
  }

  private void testWhenS() {
    Kind<Id.Witness, Boolean> trueKind = ID.widen(booleanTrue);
    Kind<Id.Witness, Boolean> falseKind = ID.widen(booleanFalse);
    Kind<Id.Witness, Unit> unitEffectKind = ID.widen(Id.of(Unit.INSTANCE));

    // Test whenS with true - effect should execute
    Kind<Id.Witness, Unit> resultTrue = selective.whenS(trueKind, unitEffectKind);
    Id<Unit> idResultTrue = ID.narrow(resultTrue);
    assertThat(idResultTrue).isNotNull();
    assertThat(idResultTrue.value()).isEqualTo(Unit.INSTANCE);

    // Test whenS with false - effect should not execute
    Kind<Id.Witness, Unit> resultFalse = selective.whenS(falseKind, unitEffectKind);
    Id<Unit> idResultFalse = ID.narrow(resultFalse);
    assertThat(idResultFalse).isNotNull();
    assertThat(idResultFalse.value()).isEqualTo(Unit.INSTANCE);
  }

  private void testIfS() {
    Kind<Id.Witness, Boolean> trueKind = ID.widen(booleanTrue);
    Kind<Id.Witness, Boolean> falseKind = ID.widen(booleanFalse);
    Kind<Id.Witness, A> thenKind = ID.widen(instance);
    Kind<Id.Witness, A> elseKind = ID.widen(instance);

    // Test ifS with true - should return then branch
    Kind<Id.Witness, A> resultTrue = selective.ifS(trueKind, thenKind, elseKind);
    Id<A> idResultTrue = ID.narrow(resultTrue);
    assertThat(idResultTrue).isNotNull();
    assertThat(idResultTrue).isSameAs(instance);

    // Test ifS with false - should return else branch
    Kind<Id.Witness, A> resultFalse = selective.ifS(falseKind, thenKind, elseKind);
    Id<A> idResultFalse = ID.narrow(resultFalse);
    assertThat(idResultFalse).isNotNull();
    assertThat(idResultFalse).isSameAs(instance);
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
