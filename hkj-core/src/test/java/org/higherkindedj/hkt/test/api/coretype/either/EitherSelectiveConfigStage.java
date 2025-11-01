// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTestConfigStage;

/**
 * Configuration stage for Either Selective tests.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and execution
 * options.
 *
 * @param <L> The Left type
 * @param <R> The Right type
 * @param <S> The result type
 */
public final class EitherSelectiveConfigStage<L, R, S>
        extends BaseTestConfigStage<EitherSelectiveConfigStage<L, R, S>> {

    private final Class<?> contextClass;
    private final Either<L, R> leftInstance;
    private final Either<L, R> rightInstance;
    private final Either<L, Choice<R, S>> choiceLeft;
    private final Either<L, Choice<R, S>> choiceRight;
    private final Either<L, Boolean> booleanTrue;
    private final Either<L, Boolean> booleanFalse;
    private final Function<R, S> selectFunction;
    private final Function<R, S> leftHandler;
    private final Function<S, S> rightHandler;

    // Selective-specific test selection flags
    private boolean includeSelect = true;
    private boolean includeBranch = true;
    private boolean includeWhenS = true;
    private boolean includeIfS = true;

    EitherSelectiveConfigStage(
            Class<?> contextClass,
            Either<L, R> leftInstance,
            Either<L, R> rightInstance,
            Either<L, Choice<R, S>> choiceLeft,
            Either<L, Choice<R, S>> choiceRight,
            Either<L, Boolean> booleanTrue,
            Either<L, Boolean> booleanFalse,
            Function<R, S> selectFunction,
            Function<R, S> leftHandler,
            Function<S, S> rightHandler) {
        this.contextClass = contextClass;
        this.leftInstance = leftInstance;
        this.rightInstance = rightInstance;
        this.choiceLeft = choiceLeft;
        this.choiceRight = choiceRight;
        this.booleanTrue = booleanTrue;
        this.booleanFalse = booleanFalse;
        this.selectFunction = selectFunction;
        this.leftHandler = leftHandler;
        this.rightHandler = rightHandler;
    }

    @Override
    protected EitherSelectiveConfigStage<L, R, S> self() {
        return this;
    }

    @Override
    public void testAll() {
        buildExecutor().executeAll();
    }

    @Override
    public EitherSelectiveValidationStage<L, R, S> configureValidation() {
        return new EitherSelectiveValidationStage<>(this);
    }

    @Override
    public EitherSelectiveConfigStage<L, R, S> onlyValidations() {
        disableAll();
        this.includeValidations = true;
        return this;
    }

    @Override
    public EitherSelectiveConfigStage<L, R, S> onlyEdgeCases() {
        disableAll();
        this.includeEdgeCases = true;
        return this;
    }

    @Override
    protected void disableAll() {
        super.disableAll();
        includeSelect = false;
        includeBranch = false;
        includeWhenS = false;
        includeIfS = false;
    }

    // Selective-specific test selection
    public EitherSelectiveConfigStage<L, R, S> skipSelect() {
        this.includeSelect = false;
        return this;
    }

    public EitherSelectiveConfigStage<L, R, S> skipBranch() {
        this.includeBranch = false;
        return this;
    }

    public EitherSelectiveConfigStage<L, R, S> skipWhenS() {
        this.includeWhenS = false;
        return this;
    }

    public EitherSelectiveConfigStage<L, R, S> skipIfS() {
        this.includeIfS = false;
        return this;
    }

    public EitherSelectiveConfigStage<L, R, S> onlySelect() {
        disableAll();
        this.includeSelect = true;
        return this;
    }

    public EitherSelectiveConfigStage<L, R, S> onlyBranch() {
        disableAll();
        this.includeBranch = true;
        return this;
    }

    public EitherSelectiveConfigStage<L, R, S> onlyWhenS() {
        disableAll();
        this.includeWhenS = true;
        return this;
    }

    public EitherSelectiveConfigStage<L, R, S> onlyIfS() {
        disableAll();
        this.includeIfS = true;
        return this;
    }

    private EitherSelectiveTestExecutor<L, R, S> buildExecutor() {
        return new EitherSelectiveTestExecutor<>(
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
                includeEdgeCases);
    }

    EitherSelectiveTestExecutor<L, R, S> buildExecutorWithValidation(
            EitherSelectiveValidationStage<L, R, S> validationStage) {
        return new EitherSelectiveTestExecutor<>(
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
                validationStage);
    }
}