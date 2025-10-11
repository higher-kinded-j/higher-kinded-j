// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either;

import java.util.function.Function;
import org.higherkindedj.hkt.either.Either;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and execution.
 *
 * @param <L> The Left type
 * @param <R> The Right type
 * @param <S> The mapped type
 */
public final class EitherTestConfigStage<L, R, S> {
    private final Class<?> contextClass;
    private final Either<L, R> leftInstance;
    private final Either<L, R> rightInstance;
    private final Function<R, S> mapper;

    // Test selection flags
    private boolean includeFactoryMethods = true;
    private boolean includeGetters = true;
    private boolean includeFold = true;
    private boolean includeSideEffects = true;
    private boolean includeMap = true;
    private boolean includeFlatMap = true;
    private boolean includeValidations = true;
    private boolean includeEdgeCases = true;

    EitherTestConfigStage(
            Class<?> contextClass,
            Either<L, R> leftInstance,
            Either<L, R> rightInstance,
            Function<R, S> mapper) {
        this.contextClass = contextClass;
        this.leftInstance = leftInstance;
        this.rightInstance = rightInstance;
        this.mapper = mapper;
    }

    // =============================================================================
    // Test Selection Methods
    // =============================================================================

    public EitherTestConfigStage<L, R, S> skipFactoryMethods() {
        this.includeFactoryMethods = false;
        return this;
    }

    public EitherTestConfigStage<L, R, S> skipGetters() {
        this.includeGetters = false;
        return this;
    }

    public EitherTestConfigStage<L, R, S> skipFold() {
        this.includeFold = false;
        return this;
    }

    public EitherTestConfigStage<L, R, S> skipSideEffects() {
        this.includeSideEffects = false;
        return this;
    }

    public EitherTestConfigStage<L, R, S> skipMap() {
        this.includeMap = false;
        return this;
    }

    public EitherTestConfigStage<L, R, S> skipFlatMap() {
        this.includeFlatMap = false;
        return this;
    }

    public EitherTestConfigStage<L, R, S> skipValidations() {
        this.includeValidations = false;
        return this;
    }

    public EitherTestConfigStage<L, R, S> skipEdgeCases() {
        this.includeEdgeCases = false;
        return this;
    }

    // =============================================================================
    // Positive Selection (Run Only Specific Tests)
    // =============================================================================

    public EitherTestConfigStage<L, R, S> onlyFactoryMethods() {
        disableAll();
        this.includeFactoryMethods = true;
        return this;
    }

    public EitherTestConfigStage<L, R, S> onlyGetters() {
        disableAll();
        this.includeGetters = true;
        return this;
    }

    public EitherTestConfigStage<L, R, S> onlyFold() {
        disableAll();
        this.includeFold = true;
        return this;
    }

    public EitherTestConfigStage<L, R, S> onlySideEffects() {
        disableAll();
        this.includeSideEffects = true;
        return this;
    }

    public EitherTestConfigStage<L, R, S> onlyMap() {
        disableAll();
        this.includeMap = true;
        return this;
    }

    public EitherTestConfigStage<L, R, S> onlyFlatMap() {
        disableAll();
        this.includeFlatMap = true;
        return this;
    }

    public EitherTestConfigStage<L, R, S> onlyValidations() {
        disableAll();
        this.includeValidations = true;
        return this;
    }

    public EitherTestConfigStage<L, R, S> onlyEdgeCases() {
        disableAll();
        this.includeEdgeCases = true;
        return this;
    }

    private void disableAll() {
        includeFactoryMethods = false;
        includeGetters = false;
        includeFold = false;
        includeSideEffects = false;
        includeMap = false;
        includeFlatMap = false;
        includeValidations = false;
        includeEdgeCases = false;
    }

    // =============================================================================
    // Validation Configuration
    // =============================================================================

    /**
     * Enters validation configuration mode.
     *
     * <p>Progressive disclosure: Shows validation context configuration options.
     *
     * @return Validation stage for configuring error message contexts
     */
    public EitherValidationStage<L, R, S> configureValidation() {
        return new EitherValidationStage<>(this);
    }

    // =============================================================================
    // Execution Methods
    // =============================================================================

    /**
     * Executes all configured tests.
     *
     * <p>This is the most comprehensive test execution option.
     */
    public void testAll() {
        EitherTestExecutor<L, R, S> executor = buildExecutor();
        executor.executeAll();
    }

    /**
     * Executes only core operation tests (no validations or edge cases).
     */
    public void testOperations() {
        includeValidations = false;
        includeEdgeCases = false;
        testAll();
    }

    /**
     * Executes only validation tests.
     */
    public void testValidations() {
        onlyValidations();
        testAll();
    }

    /**
     * Executes only edge case tests.
     */
    public void testEdgeCases() {
        onlyEdgeCases();
        testAll();
    }

    // =============================================================================
    // Internal Builder
    // =============================================================================

    private EitherTestExecutor<L, R, S> buildExecutor() {
        return new EitherTestExecutor<>(
                contextClass,
                leftInstance,
                rightInstance,
                mapper,
                includeFactoryMethods,
                includeGetters,
                includeFold,
                includeSideEffects,
                includeMap,
                includeFlatMap,
                includeValidations,
                includeEdgeCases);
    }

    EitherTestExecutor<L, R, S> buildExecutorWithValidation(EitherValidationStage<L, R, S> validationStage) {
        return new EitherTestExecutor<>(
                contextClass,
                leftInstance,
                rightInstance,
                mapper,
                includeFactoryMethods,
                includeGetters,
                includeFold,
                includeSideEffects,
                includeMap,
                includeFlatMap,
                includeValidations,
                includeEdgeCases,
                validationStage);
    }
}