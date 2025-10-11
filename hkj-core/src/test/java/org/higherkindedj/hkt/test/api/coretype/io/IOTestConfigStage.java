// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.io;

import java.util.function.Function;
import org.higherkindedj.hkt.io.IO;

/**
 * Stage 3: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and execution.
 *
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class IOTestConfigStage<A, B> {
    private final Class<?> contextClass;
    private final IO<A> ioInstance;
    private final Function<A, B> mapper;

    // Test selection flags
    private boolean includeFactoryMethods = true;
    private boolean includeExecution = true;
    private boolean includeMap = true;
    private boolean includeFlatMap = true;
    private boolean includeValidations = true;
    private boolean includeEdgeCases = true;

    IOTestConfigStage(Class<?> contextClass, IO<A> ioInstance, Function<A, B> mapper) {
        this.contextClass = contextClass;
        this.ioInstance = ioInstance;
        this.mapper = mapper;
    }

    // =============================================================================
    // Test Selection Methods
    // =============================================================================

    public IOTestConfigStage<A, B> skipFactoryMethods() {
        this.includeFactoryMethods = false;
        return this;
    }

    public IOTestConfigStage<A, B> skipExecution() {
        this.includeExecution = false;
        return this;
    }

    public IOTestConfigStage<A, B> skipMap() {
        this.includeMap = false;
        return this;
    }

    public IOTestConfigStage<A, B> skipFlatMap() {
        this.includeFlatMap = false;
        return this;
    }

    public IOTestConfigStage<A, B> skipValidations() {
        this.includeValidations = false;
        return this;
    }

    public IOTestConfigStage<A, B> skipEdgeCases() {
        this.includeEdgeCases = false;
        return this;
    }

    // =============================================================================
    // Positive Selection (Run Only Specific Tests)
    // =============================================================================

    public IOTestConfigStage<A, B> onlyFactoryMethods() {
        disableAll();
        this.includeFactoryMethods = true;
        return this;
    }

    public IOTestConfigStage<A, B> onlyExecution() {
        disableAll();
        this.includeExecution = true;
        return this;
    }

    public IOTestConfigStage<A, B> onlyMap() {
        disableAll();
        this.includeMap = true;
        return this;
    }

    public IOTestConfigStage<A, B> onlyFlatMap() {
        disableAll();
        this.includeFlatMap = true;
        return this;
    }

    public IOTestConfigStage<A, B> onlyValidations() {
        disableAll();
        this.includeValidations = true;
        return this;
    }

    public IOTestConfigStage<A, B> onlyEdgeCases() {
        disableAll();
        this.includeEdgeCases = true;
        return this;
    }

    private void disableAll() {
        includeFactoryMethods = false;
        includeExecution = false;
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
    public IOValidationStage<A, B> configureValidation() {
        return new IOValidationStage<>(this);
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
        IOTestExecutor<A, B> executor = buildExecutor();
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

    private IOTestExecutor<A, B> buildExecutor() {
        return new IOTestExecutor<>(
                contextClass,
                ioInstance,
                mapper,
                includeFactoryMethods,
                includeExecution,
                includeMap,
                includeFlatMap,
                includeValidations,
                includeEdgeCases);
    }

    IOTestExecutor<A, B> buildExecutorWithValidation(IOValidationStage<A, B> validationStage) {
        return new IOTestExecutor<>(
                contextClass,
                ioInstance,
                mapper,
                includeFactoryMethods,
                includeExecution,
                includeMap,
                includeFlatMap,
                includeValidations,
                includeEdgeCases,
                validationStage);
    }
}