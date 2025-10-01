package org.higherkindedj.hkt.test.api.monad;

/**
 * Stage 7 for configuring validation contexts in Monad tests.
 *
 * @param <F> The Monad witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class MonadValidationStage<F, A, B> {
    private final MonadOperationsStage<F, A, B> operationsStage;
    private final MonadLawsStage<F, A, B> lawsStage;

    // Validation context classes
    private Class<?> mapContext;
    private Class<?> apContext;
    private Class<?> flatMapContext;

    MonadValidationStage(
            MonadOperationsStage<F, A, B> operationsStage,
            MonadLawsStage<F, A, B> lawsStage) {
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
     *         .withFlatMapFrom(EitherMonad.class)
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
     */
    public MonadValidationStage<F, A, B> useDefaultValidation() {
        this.mapContext = null;
        this.apContext = null;
        this.flatMapContext = null;
        return this;
    }

    /**
     * Fluent builder for inheritance-based validation configuration.
     */
    public final class InheritanceValidationBuilder {

        public InheritanceValidationBuilder withMapFrom(Class<?> contextClass) {
            mapContext = contextClass;
            return this;
        }

        public InheritanceValidationBuilder withApFrom(Class<?> contextClass) {
            apContext = contextClass;
            return this;
        }

        public InheritanceValidationBuilder withFlatMapFrom(Class<?> contextClass) {
            flatMapContext = contextClass;
            return this;
        }

        public MonadValidationStage<F, A, B> done() {
            return MonadValidationStage.this;
        }

        public MonadTestSelectionStage<F, A, B> selectTests() {
            return MonadValidationStage.this.selectTests();
        }

        public void testAll() {
            MonadValidationStage.this.testAll();
        }

        public void testValidations() {
            MonadValidationStage.this.testValidations();
        }
    }

    /**
     * Enters test selection mode.
     */
    public MonadTestSelectionStage<F, A, B> selectTests() {
        return new MonadTestSelectionStage<>(operationsStage, lawsStage, this);
    }

    /**
     * Executes all configured tests.
     */
    public void testAll() {
        if (lawsStage != null) {
            operationsStage.build(lawsStage, this).executeAll();
        } else {
            operationsStage.build(null, this).executeOperationsAndValidations();
        }
    }

    /**
     * Executes only validations with configured contexts.
     */
    public void testValidations() {
        MonadTestExecutor<F, A, B> executor = operationsStage.build(lawsStage, this);
        executor.executeValidations();
    }

    // Package-private getters
    Class<?> getMapContext() {
        return mapContext;
    }

    Class<?> getApContext() {
        return apContext;
    }

    Class<?> getFlatMapContext() {
        return flatMapContext;
    }
}