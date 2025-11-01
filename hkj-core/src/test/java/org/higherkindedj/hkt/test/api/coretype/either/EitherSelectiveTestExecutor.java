// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either;

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
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
        Kind<EitherKind.Witness<L>, S> effectKind = EITHER.widen(Either.right(null));

        // Select validations
        Class<?> selectCtx = getSelectContext();
        builder.assertFunctionNull(
                () -> selective.select(null, funcKind),
                "fab",
                selectCtx,
                Operation.SELECT,
                "choice");
        builder.assertFunctionNull(
                () -> selective.select(choiceKind, null),
                "ff",
                selectCtx,
                Operation.SELECT,
                "function");

        // Branch validations
        Class<?> branchCtx = getBranchContext();
        builder.assertFunctionNull(
                () -> selective.branch(null, leftHandlerKind, rightHandlerKind),
                "fab",
                branchCtx,
                Operation.BRANCH,
                "choice");
        builder.assertFunctionNull(
                () -> selective.branch(choiceKind, null, rightHandlerKind),
                "fl",
                branchCtx,
                Operation.BRANCH,
                "leftHandler");
        builder.assertFunctionNull(
                () -> selective.branch(choiceKind, leftHandlerKind, null),
                "fr",
                branchCtx,
                Operation.BRANCH,
                "rightHandler");

        // WhenS validations
        Class<?> whenSCtx = getWhenSContext();
        builder.assertFunctionNull(
                () -> selective.whenS(null, effectKind),
                "fcond",
                whenSCtx,
                Operation.WHEN_S,
                "condition");
        builder.assertFunctionNull(
                () -> selective.whenS(condKind, null),
                "fa",
                whenSCtx,
                Operation.WHEN_S,
                "effect");

        // IfS validations
        Class<?> ifSCtx = getIfSContext();
        builder.assertFunctionNull(
                () -> selective.ifS(null, effectKind, effectKind),
                "fcond",
                ifSCtx,
                Operation.IF_S,
                "condition");
        builder.assertFunctionNull(
                () -> selective.ifS(condKind, null, effectKind),
                "fthen",
                ifSCtx,
                Operation.IF_S,
                "thenBranch");
        builder.assertFunctionNull(
                () -> selective.ifS(condKind, effectKind, null),
                "felse",
                ifSCtx,
                Operation.IF_S,
                "elseBranch");

        builder.execute();
    }

    @Override
    protected void executeEdgeCaseTests() {
        // Test with null values in Choice
        Either<L, Choice<R, S>> choiceWithNull = Either.right(
                new org.higherkindedj.hkt.Selective.SimpleChoice<>(true, null, null));
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
        Kind<EitherKind.Witness<L>, S> resultLeft =
                selective.select(leftChoiceKind, funcKind);
        Either<L, S> eitherResultLeft = EITHER.narrow(resultLeft);
        assertThat(eitherResultLeft.isRight()).isTrue();

        // Test select with Right (in Choice) - function should NOT be applied
        Kind<EitherKind.Witness<L>, S> resultRight =
                selective.select(rightChoiceKind, funcKind);
        Either<L, S> eitherResultRight = EITHER.narrow(resultRight);
        assertThat(eitherResultRight.isRight()).isTrue();

        // Test select with Either.Left - should propagate error
        Either<L, Choice<R, S>> errorChoice = Either.left(leftInstance.getLeft());
        Kind<EitherKind.Witness<L>, Choice<R, S>> errorChoiceKind = EITHER.widen(errorChoice);
        Kind<EitherKind.Witness<L>, S> errorResult =
                selective.select(errorChoiceKind, funcKind);
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
        Kind<EitherKind.Witness<L>, R> effectKind = EITHER.widen(rightInstance);

        // Test whenS with true - effect should execute
        Kind<EitherKind.Witness<L>, R> resultTrue = selective.whenS(trueKind, effectKind);
        Either<L, R> eitherResultTrue = EITHER.narrow(resultTrue);
        assertThat(eitherResultTrue.isRight()).isTrue();

        // Test whenS with false - effect should not execute
        Kind<EitherKind.Witness<L>, R> resultFalse = selective.whenS(falseKind, effectKind);
        Either<L, R> eitherResultFalse = EITHER.narrow(resultFalse);
        assertThat(eitherResultFalse.isRight()).isTrue();

        // Test whenS with Either.Left condition - should propagate error
        Either<L, Boolean> errorCondition = Either.left(leftInstance.getLeft());
        Kind<EitherKind.Witness<L>, Boolean> errorCondKind = EITHER.widen(errorCondition);
        Kind<EitherKind.Witness<L>, R> errorResult =
                selective.whenS(errorCondKind, effectKind);
        Either<L, R> eitherErrorResult = EITHER.narrow(errorResult);
        assertThat(eitherErrorResult.isLeft()).isTrue();
    }

    private void testIfS() {
        Kind<EitherKind.Witness<L>, Boolean> trueKind = EITHER.widen(booleanTrue);
        Kind<EitherKind.Witness<L>, Boolean> falseKind = EITHER.widen(booleanFalse);
        Kind<EitherKind.Witness<L>, R> thenKind = EITHER.widen(rightInstance);
        Kind<EitherKind.Witness<L>, R> elseKind = EITHER.widen(rightInstance);

        // Test ifS with true - should return then branch
        Kind<EitherKind.Witness<L>, R> resultTrue =
                selective.ifS(trueKind, thenKind, elseKind);
        Either<L, R> eitherResultTrue = EITHER.narrow(resultTrue);
        assertThat(eitherResultTrue.isRight()).isTrue();
        assertThat(eitherResultTrue).isSameAs(rightInstance);

        // Test ifS with false - should return else branch
        Kind<EitherKind.Witness<L>, R> resultFalse =
                selective.ifS(falseKind, thenKind, elseKind);
        Either<L, R> eitherResultFalse = EITHER.narrow(resultFalse);
        assertThat(eitherResultFalse.isRight()).isTrue();
        assertThat(eitherResultFalse).isSameAs(rightInstance);

        // Test ifS with Either.Left condition - should propagate error
        Either<L, Boolean> errorCondition = Either.left(leftInstance.getLeft());
        Kind<EitherKind.Witness<L>, Boolean> errorCondKind = EITHER.widen(errorCondition);
        Kind<EitherKind.Witness<L>, R> errorResult =
                selective.ifS(errorCondKind, thenKind, elseKind);
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