// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe_t;

import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.maybe_t.MaybeT;

/**
 * Stage 2: Complete MaybeT instance configuration.
 *
 * <p>Progressive disclosure: Shows completion of instance setup.
 *
 * @param <F> The outer monad witness type
 * @param <A> The type of the value potentially held by the inner Maybe
 */
public final class MaybeTInstanceStage<F, A> {
    private final Class<?> contextClass;
    private final Monad<F> outerMonad;
    private final MaybeT<F, A> justInstance;
    private final MaybeT<F, A> nothingInstance;

    MaybeTInstanceStage(
            Class<?> contextClass,
            Monad<F> outerMonad,
            MaybeT<F, A> justInstance,
            MaybeT<F, A> nothingInstance) {
        this.contextClass = contextClass;
        this.outerMonad = outerMonad;
        this.justInstance = justInstance;
        this.nothingInstance = nothingInstance;
    }

    /**
     * Provides the Nothing instance (if Just was configured first).
     *
     * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
     *
     * @param nothingInstance A Nothing instance
     * @return Next stage for configuring mappers
     */
    public MaybeTOperationsStage<F, A> withNothing(MaybeT<F, A> nothingInstance) {
        if (this.justInstance == null) {
            throw new IllegalStateException("Cannot set Nothing twice");
        }
        return new MaybeTOperationsStage<>(contextClass, outerMonad, justInstance, nothingInstance);
    }

    /**
     * Provides the Just instance (if Nothing was configured first).
     *
     * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
     *
     * @param justInstance A Just instance
     * @return Next stage for configuring mappers
     */
    public MaybeTOperationsStage<F, A> withJust(MaybeT<F, A> justInstance) {
        if (this.nothingInstance == null) {
            throw new IllegalStateException("Cannot set Just twice");
        }
        return new MaybeTOperationsStage<>(contextClass, outerMonad, justInstance, nothingInstance);
    }
}