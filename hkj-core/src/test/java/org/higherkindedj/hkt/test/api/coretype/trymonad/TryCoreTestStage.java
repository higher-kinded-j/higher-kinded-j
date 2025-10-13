package org.higherkindedj.hkt.test.api.coretype.trymonad;


import org.higherkindedj.hkt.trymonad.Try;

/**
 * Stage 1: Configure Try test instances.
 *
 * <p>Entry point for Try core type testing with progressive disclosure.
 *
 * @param <T> The value type
 */
public final class TryCoreTestStage<T> {
    private final Class<?> contextClass;

    public TryCoreTestStage(Class<?> contextClass) {
        this.contextClass = contextClass;
    }

    /**
     * Provides a Success instance for testing.
     *
     * <p>Progressive disclosure: Next step is {@code .withFailure(...)}
     *
     * @param successInstance A Success instance (can have null value)
     * @return Next stage for configuring Failure instance
     */
    public TryInstanceStage<T> withSuccess(Try<T> successInstance) {
        return new TryInstanceStage<>(contextClass, successInstance, null);
    }

    /**
     * Provides a Failure instance for testing.
     *
     * <p>Progressive disclosure: Next step is {@code .withSuccess(...)}
     *
     * @param failureInstance A Failure instance
     * @return Next stage for configuring Success instance
     */
    public TryInstanceStage<T> withFailure(Try<T> failureInstance) {
        return new TryInstanceStage<>(contextClass, null, failureInstance);
    }
}