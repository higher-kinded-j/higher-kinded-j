// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.applicative;

/**
 * Stage 7 for configuring validation contexts in Applicative tests.
 *
 * <p>Allows specifying which implementation class should be used in validation
 * error messages for each operation, supporting inheritance hierarchies.
 *
 * @param <F> The Applicative witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class ApplicativeValidationStage<F, A, B> {
    private final ApplicativeOperationsStage<F, A, B> operationsStage;
    private final ApplicativeLawsStage<F, A, B> lawsStage;

    // Validation context classes
    private Class<?> mapContext;
    private Class<?> apContext;
    private Class<?> map2Context;

    ApplicativeValidationStage(
            ApplicativeOperationsStage<F, A, B> operationsStage,
            ApplicativeLawsStage<F, A, B> lawsStage) {
        this.operationsStage = operationsStage;
        this.lawsStage = lawsStage;
    }

    /**
     * Uses inheritance-based validation with fluent configuration.
     *
     * <p>Example:
     * <pre>{@code
     * .configureValidation()
     *     .useInheritanceValidation()
     *         .withMapFrom(EitherFunctor.class)
     *         .withApFrom(EitherMonad.class)
     *         .withMap2From(EitherMonad.class)
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
    public ApplicativeValidationStage<F, A, B> useDefaultValidation() {
        this.mapContext = null;
        this.apContext = null;
        this.map2Context = null;
        return this;
    }

    /**
     * Fluent builder for inheritance-based validation configuration.
     */
    public final class InheritanceValidationBuilder {

        /**
         * Specifies the class used for map operation validation.
         *
         * @param contextClass The class that implements map
         * @return This builder for chaining
         */
        public InheritanceValidationBuilder withMapFrom(Class<?> contextClass) {
            mapContext = contextClass;
            return this;
        }

        /**
         * Specifies the class used for ap operation validation.
         *
         * @param contextClass The class that implements ap
         * @return This builder for chaining
         */
        public InheritanceValidationBuilder withApFrom(Class<?> contextClass) {
            apContext = contextClass;
            return this;
        }

        /**
         * Specifies the class used for map2 operation validation.
         *
         * @param contextClass The class that implements map2
         * @return This builder for chaining
         */
        public InheritanceValidationBuilder withMap2From(Class<?> contextClass) {
            map2Context = contextClass;
            return this;
        }

        /**
         * Completes inheritance validation configuration.
         *
         * @return The parent validation stage for execution
         */
        public ApplicativeValidationStage<F, A, B> done() {
            return ApplicativeValidationStage.this;
        }

        /**
         * Enters test selection mode for fine-grained control.
         *
         * @return Stage for selecting which tests to run
         */
        public ApplicativeTestSelectionStage<F, A, B> selectTests() {
            return ApplicativeValidationStage.this.selectTests();
        }

        /**
         * Executes all configured tests.
         */
        public void testAll() {
            ApplicativeValidationStage.this.testAll();
        }

        /**
         * Executes only operation and validation tests.
         */
        public void testOperationsAndValidations() {
            ApplicativeValidationStage.this.testOperationsAndValidations();
        }

        /**
         * Executes only validation tests.
         */
        public void testValidations() {
            ApplicativeValidationStage.this.testValidations();
        }
    }

    /**
     * Enters test selection mode for fine-grained control.
     *
     * <p>Progressive disclosure: Shows test selection options.
     *
     * @return Stage for selecting which tests to run
     */
    public ApplicativeTestSelectionStage<F, A, B> selectTests() {
        return new ApplicativeTestSelectionStage<>(operationsStage, lawsStage, this);
    }

    /**
     * Executes all configured tests.
     *
     * <p>If laws were configured, includes law tests. Otherwise, runs operations and validations only.
     */
    public void testAll() {
        if (lawsStage != null) {
            operationsStage.build(lawsStage, this).executeAll();
        } else {
            operationsStage.build(null, this).executeOperationsAndValidations();
        }
    }

    public void testOperationsAndValidations() {
        operationsStage.build(lawsStage, this).executeOperationsAndValidations();
    }

    public void testValidations() {
        operationsStage.build(lawsStage, this).executeValidations();
    }


    // Package-private getters
    Class<?> getMapContext() {
        return mapContext;
    }

    Class<?> getApContext() {
        return apContext;
    }

    Class<?> getMap2Context() {
        return map2Context;
    }

    private ApplicativeTestExecutor<F, A, B> buildExecutor() {
        return new ApplicativeTestExecutor<>(
                operationsStage.getContextClass(),
                operationsStage.getApplicative(),
                operationsStage.getValidKind(),
                operationsStage.getValidKind2(),
                operationsStage.getMapper(),
                operationsStage.getFunctionKind(),
                operationsStage.getCombiningFunction(),
                lawsStage,
                this);
    }
}