package org.higherkindedj.hkt.test.api.coretype.trymonad;



import org.higherkindedj.hkt.trymonad.Try;

/**
 * Stage 2: Complete Try instance configuration.
 *
 * <p>Progressive disclosure: Shows completion of instance setup.
 *
 * @param <T> The value type
 */
public final class TryInstanceStage<T> {
    private final Class<?> contextClass;
    private final Try<T> successInstance;
    private final Try<T> failureInstance;

    TryInstanceStage(Class<?> contextClass, Try<T> successInstance, Try<T> failureInstance) {
        this.contextClass = contextClass;
        this.successInstance = successInstance;
        this.failureInstance = failureInstance;
    }

    /**
     * Provides the Failure instance (if Success was configured first).
     *
     * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
     *
     * @param failureInstance A Failure instance
     * @return Next stage for configuring mappers
     */
    public TryOperationsStage<T> withFailure(Try<T> failureInstance) {
        if (this.successInstance == null) {
            throw new IllegalStateException("Cannot set Failure twice");
        }
        return new TryOperationsStage<>(contextClass, successInstance, failureInstance);
    }

    /**
     * Provides the Success instance (if Failure was configured first).
     *
     * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
     *
     * @param successInstance A Success instance
     * @return Next stage for configuring mappers
     */
    public TryOperationsStage<T> withSuccess(Try<T> successInstance) {
        if (this.failureInstance == null) {
            throw new IllegalStateException("Cannot set Success twice");
        }
        return new TryOperationsStage<>(contextClass, successInstance, failureInstance);
    }
}