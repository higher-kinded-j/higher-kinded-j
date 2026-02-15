// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.reader;

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.reader.ReaderKind;
import org.higherkindedj.hkt.reader.ReaderKindHelper;
import org.higherkindedj.hkt.reader.ReaderSelective;
import org.higherkindedj.hkt.test.api.coretype.common.BaseCoreTypeTestExecutor;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for Reader Selective tests.
 *
 * <p>This class coordinates Selective test execution.
 *
 * @param <R> The environment type
 * @param <A> The input type
 * @param <B> The result type
 */
final class ReaderSelectiveTestExecutor<R, A, B>
    extends BaseCoreTypeTestExecutor<A, B, ReaderSelectiveValidationStage<R, A, B>> {

  private static final ReaderKindHelper READER = ReaderKindHelper.READER;

  private final Reader<R, A> readerInstance;
  private final R environment;
  private final Reader<R, Choice<A, B>> choiceLeft;
  private final Reader<R, Choice<A, B>> choiceRight;
  private final Reader<R, Boolean> booleanTrue;
  private final Reader<R, Boolean> booleanFalse;
  private final Function<A, B> selectFunction;
  private final Function<A, B> leftHandler;
  private final Function<B, B> rightHandler;

  private final boolean includeSelect;
  private final boolean includeBranch;
  private final boolean includeWhenS;
  private final boolean includeIfS;

  private final ReaderSelective<R> selective = ReaderSelective.instance();

  ReaderSelectiveTestExecutor(
      Class<?> contextClass,
      Reader<R, A> readerInstance,
      R environment,
      Reader<R, Choice<A, B>> choiceLeft,
      Reader<R, Choice<A, B>> choiceRight,
      Reader<R, Boolean> booleanTrue,
      Reader<R, Boolean> booleanFalse,
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
        readerInstance,
        environment,
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

  ReaderSelectiveTestExecutor(
      Class<?> contextClass,
      Reader<R, A> readerInstance,
      R environment,
      Reader<R, Choice<A, B>> choiceLeft,
      Reader<R, Choice<A, B>> choiceRight,
      Reader<R, Boolean> booleanTrue,
      Reader<R, Boolean> booleanFalse,
      Function<A, B> selectFunction,
      Function<A, B> leftHandler,
      Function<B, B> rightHandler,
      boolean includeSelect,
      boolean includeBranch,
      boolean includeWhenS,
      boolean includeIfS,
      boolean includeValidations,
      boolean includeEdgeCases,
      ReaderSelectiveValidationStage<R, A, B> validationStage) {

    super(contextClass, selectFunction, includeValidations, includeEdgeCases, validationStage);

    this.readerInstance = readerInstance;
    this.environment = environment;
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

    Kind<ReaderKind.Witness<R>, Choice<A, B>> choiceKind = READER.widen(choiceLeft);
    Kind<ReaderKind.Witness<R>, Function<A, B>> funcKind =
        READER.widen(Reader.constant(selectFunction));
    Kind<ReaderKind.Witness<R>, Function<A, B>> leftHandlerKind =
        READER.widen(Reader.constant(leftHandler));
    Kind<ReaderKind.Witness<R>, Function<B, B>> rightHandlerKind =
        READER.widen(Reader.constant(rightHandler));
    Kind<ReaderKind.Witness<R>, Boolean> condKind = READER.widen(booleanTrue);
    Kind<ReaderKind.Witness<R>, Unit> unitEffectKind = READER.widen(Reader.constant(Unit.INSTANCE));

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
    Kind<ReaderKind.Witness<R>, A> effectKind = READER.widen(readerInstance);
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
    Reader<R, Choice<A, B>> choiceWithNull = Reader.constant(Selective.left(null));
    Kind<ReaderKind.Witness<R>, Choice<A, B>> choiceKind = READER.widen(choiceWithNull);
    Kind<ReaderKind.Witness<R>, Function<A, B>> funcKind =
        READER.widen(Reader.constant(a -> a == null ? null : selectFunction.apply(a)));

    assertThatCode(() -> selective.select(choiceKind, funcKind)).doesNotThrowAnyException();

    // Test toString
    assertThat(selective.toString()).isNotNull();
  }

  private void testSelect() {
    Kind<ReaderKind.Witness<R>, Choice<A, B>> leftChoiceKind = READER.widen(choiceLeft);
    Kind<ReaderKind.Witness<R>, Choice<A, B>> rightChoiceKind = READER.widen(choiceRight);
    Kind<ReaderKind.Witness<R>, Function<A, B>> funcKind =
        READER.widen(Reader.constant(selectFunction));

    // Test select with Left (in Choice) - function should be applied
    Kind<ReaderKind.Witness<R>, B> resultLeft = selective.select(leftChoiceKind, funcKind);
    Reader<R, B> readerResultLeft = READER.narrow(resultLeft);
    B valueLeft = readerResultLeft.run(environment);
    assertThat(valueLeft).isNotNull();

    // Test select with Right (in Choice) - function should NOT be applied
    Kind<ReaderKind.Witness<R>, B> resultRight = selective.select(rightChoiceKind, funcKind);
    Reader<R, B> readerResultRight = READER.narrow(resultRight);
    B valueRight = readerResultRight.run(environment);
    assertThat(valueRight).isNotNull();
  }

  private void testBranch() {
    Kind<ReaderKind.Witness<R>, Choice<A, B>> leftChoiceKind = READER.widen(choiceLeft);
    Kind<ReaderKind.Witness<R>, Choice<A, B>> rightChoiceKind = READER.widen(choiceRight);
    Kind<ReaderKind.Witness<R>, Function<A, B>> leftHandlerKind =
        READER.widen(Reader.constant(leftHandler));
    Kind<ReaderKind.Witness<R>, Function<B, B>> rightHandlerKind =
        READER.widen(Reader.constant(rightHandler));

    // Test branch with Left - left handler should be applied
    Kind<ReaderKind.Witness<R>, B> resultLeft =
        selective.branch(leftChoiceKind, leftHandlerKind, rightHandlerKind);
    Reader<R, B> readerResultLeft = READER.narrow(resultLeft);
    B valueLeft = readerResultLeft.run(environment);
    assertThat(valueLeft).isNotNull();

    // Test branch with Right - right handler should be applied
    Kind<ReaderKind.Witness<R>, B> resultRight =
        selective.branch(rightChoiceKind, leftHandlerKind, rightHandlerKind);
    Reader<R, B> readerResultRight = READER.narrow(resultRight);
    B valueRight = readerResultRight.run(environment);
    assertThat(valueRight).isNotNull();
  }

  private void testWhenS() {
    Kind<ReaderKind.Witness<R>, Boolean> trueKind = READER.widen(booleanTrue);
    Kind<ReaderKind.Witness<R>, Boolean> falseKind = READER.widen(booleanFalse);
    Kind<ReaderKind.Witness<R>, Unit> unitEffectKind = READER.widen(Reader.constant(Unit.INSTANCE));

    // Test whenS with true - effect should execute
    Kind<ReaderKind.Witness<R>, Unit> resultTrue = selective.whenS(trueKind, unitEffectKind);
    Reader<R, Unit> readerResultTrue = READER.narrow(resultTrue);
    Unit unitTrue = readerResultTrue.run(environment);
    assertThat(unitTrue).isEqualTo(Unit.INSTANCE);

    // Test whenS with false - effect should not execute
    Kind<ReaderKind.Witness<R>, Unit> resultFalse = selective.whenS(falseKind, unitEffectKind);
    Reader<R, Unit> readerResultFalse = READER.narrow(resultFalse);
    Unit unitFalse = readerResultFalse.run(environment);
    assertThat(unitFalse).isEqualTo(Unit.INSTANCE);
  }

  private void testIfS() {
    Kind<ReaderKind.Witness<R>, Boolean> trueKind = READER.widen(booleanTrue);
    Kind<ReaderKind.Witness<R>, Boolean> falseKind = READER.widen(booleanFalse);
    Kind<ReaderKind.Witness<R>, A> thenKind = READER.widen(readerInstance);
    Kind<ReaderKind.Witness<R>, A> elseKind = READER.widen(readerInstance);

    // Test ifS with true - should return then branch
    Kind<ReaderKind.Witness<R>, A> resultTrue = selective.ifS(trueKind, thenKind, elseKind);
    Reader<R, A> readerResultTrue = READER.narrow(resultTrue);
    A valueTrue = readerResultTrue.run(environment);
    assertThat(valueTrue).isNotNull();

    // Test ifS with false - should return else branch
    Kind<ReaderKind.Witness<R>, A> resultFalse = selective.ifS(falseKind, thenKind, elseKind);
    Reader<R, A> readerResultFalse = READER.narrow(resultFalse);
    A valueFalse = readerResultFalse.run(environment);
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
