// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe_t;

import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.maybe_t.MaybeT;

/**
 * Stage 1: Configure MaybeT test instances.
 *
 * <p>Entry point for MaybeT core type testing with progressive disclosure.
 *
 * @param <F> The outer monad witness type
 * @param <A> The type of the value potentially held by the inner Maybe
 */
public final class MaybeTCoreTestStage<F, A> {
    private final Class<?> contextClass;
    private final Monad<F> outerMonad;

    public MaybeTCoreTestStage(Class<?> contextClass, Monad<F> outerMonad) {
        this.contextClass = contextClass;
        this.outerMonad = outerMonad;
    }

    /**
     * Provides a Just instance for testing.
     *
     * <p>Progressive disclosure: Next step is {@code .withNothing(...)}
     *
     * @param justInstance A Just instance (can have null value)
     * @return Next stage for configuring Nothing instance
     */
    public MaybeTInstanceStage<F, A> withJust(MaybeT<F, A> justInstance) {
        return new MaybeTInstanceStage<>(contextClass, outerMonad, justInstance, null);
    }

    /**
     * Provides a Nothing instance for testing.
     *
     * <p>Progressive disclosure: Next step is {@code .withJust(...)}
     *
     * @param nothingInstance A Nothing instance
     * @return Next stage for configuring Just instance
     */
    public MaybeTInstanceStage<F, A> withNothing(MaybeT<F, A> nothingInstance) {
        return new MaybeTInstanceStage<>(contextClass, outerMonad, null, nothingInstance);
    }
}