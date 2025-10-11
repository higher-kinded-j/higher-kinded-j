package org.higherkindedj.hkt.test.api.monad;

/**
 * Stage 6: Fine-grained test selection for Monad.
 *
 * @param <F> The Monad witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class MonadTestSelectionStage<F, A, B> {
    private final MonadOperationsStage<F, A, B> operationsStage;
    private final MonadLawsStage<F, A, B> lawsStage;
    private final MonadValidationStage<F, A, B> validationStage;

    private boolean includeOperations = true;
    private boolean includeValidations = true;
    private boolean includeExceptions = true;
    private boolean includeLaws = true;

    MonadTestSelectionStage(
            MonadOperationsStage<F, A, B> operationsStage,
            MonadLawsStage<F, A, B> lawsStage,
            MonadValidationStage<F, A, B> validationStage) {
        this.operationsStage = operationsStage;
        this.lawsStage = lawsStage;
        this.validationStage = validationStage;
    }

    public MonadTestSelectionStage<F, A, B> skipOperations() {
        includeOperations = false;
        return this;
    }

    public MonadTestSelectionStage<F, A, B> skipValidations() {
        includeValidations = false;
        return this;
    }

    public MonadTestSelectionStage<F, A, B> skipExceptions() {
        includeExceptions = false;
        return this;
    }

    public MonadTestSelectionStage<F, A, B> skipLaws() {
        includeLaws = false;
        return this;
    }

    public MonadTestSelectionStage<F, A, B> onlyOperations() {
        includeOperations = true;
        includeValidations = false;
        includeExceptions = false;
        includeLaws = false;
        return this;
    }

    public MonadTestSelectionStage<F, A, B> onlyValidations() {
        includeOperations = false;
        includeValidations = true;
        includeExceptions = false;
        includeLaws = false;
        return this;
    }

    public MonadTestSelectionStage<F, A, B> onlyExceptions() {
        includeOperations = false;
        includeValidations = false;
        includeExceptions = true;
        includeLaws = false;
        return this;
    }

    public MonadTestSelectionStage<F, A, B> onlyLaws() {
        includeOperations = false;
        includeValidations = false;
        includeExceptions = false;
        includeLaws = true;
        return this;
    }

    public MonadOperationsStage<F, A, B> and() {
        return operationsStage;
    }

    public void test() {
        // Build executor with both laws and validation stages
        MonadTestExecutor<F, A, B> executor =
                new MonadTestExecutor<>(
                        operationsStage.getContextClass(),
                        operationsStage.getMonad(),
                        operationsStage.getValidKind(),
                        operationsStage.getValidKind2(),
                        operationsStage.getMapper(),
                        operationsStage.getFlatMapper(),
                        operationsStage.getFunctionKind(),
                        operationsStage.getCombiningFunction(),
                        lawsStage,
                        validationStage  // Pass validationStage to constructor
                );

        executor.setTestSelection(
                includeOperations, includeValidations, includeExceptions, includeLaws);
        executor.executeSelected();
    }
}