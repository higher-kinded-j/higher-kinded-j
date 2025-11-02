// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.io;

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.io.IOSelective;
import org.higherkindedj.hkt.test.api.coretype.common.BaseCoreTypeTestExecutor;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for IO Selective tests.
 *
 * <p>This class coordinates Selective test execution.
 *
 * @param <A> The input type
 * @param <B> The result type
 */
final class IOSelectiveTestExecutor<A, B>
    extends BaseCoreTypeTestExecutor<A, B, IOSelectiveValidationStage<A, B>> {

  private static final IOKindHelper IO_OP = IOKindHelper.IO_OP;

  private final IO<A> ioInstance;
  private final IO<Choice<A, B>> choiceLeft;
  private final IO<Choice<A, B>> choiceRight;
  private final IO<Boolean> booleanTrue;
  private final IO<Boolean> booleanFalse;
  private final Function<A, B> selectFunction;
  private final Function<A, B> leftHandler;
  private final Function<B, B> rightHandler;

  private final boolean includeSelect;
  private final boolean includeBranch;
  private final boolean includeWhenS;
  private final boolean includeIfS;

  private final IOSelective selective = IOSelective.INSTANCE;

  IOSelectiveTestExecutor(
      Class<?> contextClass,
      IO<A> ioInstance,
      IO<Choice<A, B>> choiceLeft,
      IO<Choice<A, B>> choiceRight,
      IO<Boolean> booleanTrue,
      IO<Boolean> booleanFalse,
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
        ioInstance,
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

  IOSelectiveTestExecutor(
      Class<?> contextClass,
      IO<A> ioInstance,
      IO<Choice<A, B>> choiceLeft,
      IO<Choice<A, B>> choiceRight,
      IO<Boolean> booleanTrue,
      IO<Boolean> booleanFalse,
      Function<A, B> selectFunction,
      Function<A, B> leftHandler,
      Function<B, B> rightHandler,
      boolean includeSelect,
      boolean includeBranch,
      boolean includeWhenS,
      boolean includeIfS,
      boolean includeValidations,
      boolean includeEdgeCases,
      IOSelectiveValidationStage<A, B> validationStage) {

    super(contextClass, selectFunction, includeValidations, includeEdgeCases, validationStage);

    this.ioInstance = ioInstance;
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

    Kind<IOKind.Witness, Choice<A, B>> choiceKind = IO_OP.widen(choiceLeft);
    Kind<IOKind.Witness, Function<A, B>> funcKind = IO_OP.widen(IO.delay(() -> selectFunction));
    Kind<IOKind.Witness, Function<A, B>> leftHandlerKind = IO_OP.widen(IO.delay(() -> leftHandler));
    Kind<IOKind.Witness, Function<B, B>> rightHandlerKind =
        IO_OP.widen(IO.delay(() -> rightHandler));
    Kind<IOKind.Witness, Boolean> condKind = IO_OP.widen(booleanTrue);
    Kind<IOKind.Witness, Unit> unitEffectKind = IO_OP.widen(IO.delay(() -> Unit.INSTANCE));

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
    Kind<IOKind.Witness, A> effectKind = IO_OP.widen(ioInstance);
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
    IO<Choice<A, B>> choiceWithNull = IO.delay(() -> org.higherkindedj.hkt.Selective.left(null));
    Kind<IOKind.Witness, Choice<A, B>> choiceKind = IO_OP.widen(choiceWithNull);
    Kind<IOKind.Witness, Function<A, B>> funcKind =
        IO_OP.widen(IO.delay(() -> a -> a == null ? null : selectFunction.apply(a)));

    assertThatCode(() -> selective.select(choiceKind, funcKind)).doesNotThrowAnyException();

    // Test toString
    assertThat(selective.toString()).isNotNull();
  }

  private void testSelect() {
    Kind<IOKind.Witness, Choice<A, B>> leftChoiceKind = IO_OP.widen(choiceLeft);
    Kind<IOKind.Witness, Choice<A, B>> rightChoiceKind = IO_OP.widen(choiceRight);
    Kind<IOKind.Witness, Function<A, B>> funcKind = IO_OP.widen(IO.delay(() -> selectFunction));

    // Test select with Left (in Choice) - function should be applied
    Kind<IOKind.Witness, B> resultLeft = selective.select(leftChoiceKind, funcKind);
    IO<B> ioResultLeft = IO_OP.narrow(resultLeft);
    B valueLeft = ioResultLeft.unsafeRunSync();
    assertThat(valueLeft).isNotNull();

    // Test select with Right (in Choice) - function should NOT be applied
    Kind<IOKind.Witness, B> resultRight = selective.select(rightChoiceKind, funcKind);
    IO<B> ioResultRight = IO_OP.narrow(resultRight);
    B valueRight = ioResultRight.unsafeRunSync();
    assertThat(valueRight).isNotNull();
  }

  private void testBranch() {
    Kind<IOKind.Witness, Choice<A, B>> leftChoiceKind = IO_OP.widen(choiceLeft);
    Kind<IOKind.Witness, Choice<A, B>> rightChoiceKind = IO_OP.widen(choiceRight);
    Kind<IOKind.Witness, Function<A, B>> leftHandlerKind = IO_OP.widen(IO.delay(() -> leftHandler));
    Kind<IOKind.Witness, Function<B, B>> rightHandlerKind =
        IO_OP.widen(IO.delay(() -> rightHandler));

    // Test branch with Left - left handler should be applied
    Kind<IOKind.Witness, B> resultLeft =
        selective.branch(leftChoiceKind, leftHandlerKind, rightHandlerKind);
    IO<B> ioResultLeft = IO_OP.narrow(resultLeft);
    B valueLeft = ioResultLeft.unsafeRunSync();
    assertThat(valueLeft).isNotNull();

    // Test branch with Right - right handler should be applied
    Kind<IOKind.Witness, B> resultRight =
        selective.branch(rightChoiceKind, leftHandlerKind, rightHandlerKind);
    IO<B> ioResultRight = IO_OP.narrow(resultRight);
    B valueRight = ioResultRight.unsafeRunSync();
    assertThat(valueRight).isNotNull();
  }

  private void testWhenS() {
    Kind<IOKind.Witness, Boolean> trueKind = IO_OP.widen(booleanTrue);
    Kind<IOKind.Witness, Boolean> falseKind = IO_OP.widen(booleanFalse);
    Kind<IOKind.Witness, Unit> unitEffectKind = IO_OP.widen(IO.delay(() -> Unit.INSTANCE));

    // Test whenS with true - effect should execute
    Kind<IOKind.Witness, Unit> resultTrue = selective.whenS(trueKind, unitEffectKind);
    IO<Unit> ioResultTrue = IO_OP.narrow(resultTrue);
    Unit unitTrue = ioResultTrue.unsafeRunSync();
    assertThat(unitTrue).isEqualTo(Unit.INSTANCE);

    // Test whenS with false - effect should not execute
    Kind<IOKind.Witness, Unit> resultFalse = selective.whenS(falseKind, unitEffectKind);
    IO<Unit> ioResultFalse = IO_OP.narrow(resultFalse);
    Unit unitFalse = ioResultFalse.unsafeRunSync();
    assertThat(unitFalse).isEqualTo(Unit.INSTANCE);
  }

  private void testIfS() {
    Kind<IOKind.Witness, Boolean> trueKind = IO_OP.widen(booleanTrue);
    Kind<IOKind.Witness, Boolean> falseKind = IO_OP.widen(booleanFalse);
    Kind<IOKind.Witness, A> thenKind = IO_OP.widen(ioInstance);
    Kind<IOKind.Witness, A> elseKind = IO_OP.widen(ioInstance);

    // Test ifS with true - should return then branch
    Kind<IOKind.Witness, A> resultTrue = selective.ifS(trueKind, thenKind, elseKind);
    IO<A> ioResultTrue = IO_OP.narrow(resultTrue);
    A valueTrue = ioResultTrue.unsafeRunSync();
    assertThat(valueTrue).isNotNull();

    // Test ifS with false - should return else branch
    Kind<IOKind.Witness, A> resultFalse = selective.ifS(falseKind, thenKind, elseKind);
    IO<A> ioResultFalse = IO_OP.narrow(resultFalse);
    A valueFalse = ioResultFalse.unsafeRunSync();
    assertThat(valueFalse).isNotNull();
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
