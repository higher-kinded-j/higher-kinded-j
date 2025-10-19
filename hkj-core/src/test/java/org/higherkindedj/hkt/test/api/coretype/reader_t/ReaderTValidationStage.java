// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for licence information.
package org.higherkindedj.hkt.test.api.coretype.reader_t;

/**
 * Stage for configuring validation contexts in ReaderT core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages,
 * supporting inheritance hierarchies.
 *
 * @param <F> The outer monad witness type
 * @param <R> The environment type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class ReaderTValidationStage<F, R, A, B> {
    private final ReaderTTestConfigStage<F, R, A, B> configStage;

    // Validation context class
    private Class<?> validationContext;

    ReaderTValidationStage(ReaderTTestConfigStage<F, R, A, B> configStage) {
        this.configStage = configStage;
    }

    /**
     * Uses inheritance-based validation with fluent configuration.
     *
     * <p>Specifies which implementation class is used for validation messages.
     *
     * <p>Example:
     *
     * <pre>{@code
     * .configureValidation()
     *     .useInheritanceValidation()
     *         .withContextFrom(ReaderT.class)
     *     .testAll()
     * }</pre>
     *
     * @return Fluent configuration builder
     */
    public InheritanceValidationBuilder useInheritanceValidation() {
        return new InheritanceValidationBuilder();
    }

    /**
     * Uses default validation (no class context).
     *
     * <p>Error messages will not include specific class names.
     *
     * @return This stage for further configuration or execution
     */
    public ReaderTValidationStage<F, R, A, B> useDefaultValidation() {
        this.validationContext = null;
        return this;
    }

    /** Fluent builder for inheritance-based validation configuration. */
    public final class InheritanceValidationBuilder {

        /**
         * Specifies the class used for validation error messages.
         *
         * @param contextClass The class that implements the operations
         * @return This builder for chaining
         */
        public InheritanceValidationBuilder withContextFrom(Class<?> contextClass) {
            validationContext = contextClass;
            return this;
        }

        /**
         * Completes inheritance validation configuration.
         *
         * @return The parent validation stage for execution
         */
        public ReaderTValidationStage<F, R, A, B> done() {
            return ReaderTValidationStage.this;
        }

        /** Executes all configured tests. */
        public void testAll() {
            ReaderTValidationStage.this.testAll();
        }

        /** Executes only validation tests with configured contexts. */
        public void testValidations() {
            ReaderTValidationStage.this.testValidations();
        }
    }

    /**
     * Executes all configured tests.
     *
     * <p>Includes all test categories with the configured validation contexts.
     */
    public void testAll() {
        ReaderTTestExecutor<F, R, A, B> executor = buildExecutor();
        executor.executeAll();
    }

    /** Executes only validation tests with configured contexts. */
    public void testValidations() {
        ReaderTTestExecutor<F, R, A, B> executor = buildExecutor();
        executor.testValidations();
    }

    // Package-private getter
    Class<?> getValidationContext() {
        return validationContext;
    }

    private ReaderTTestExecutor<F, R, A, B> buildExecutor() {
        return configStage.buildExecutorWithValidation(this);
    }
}