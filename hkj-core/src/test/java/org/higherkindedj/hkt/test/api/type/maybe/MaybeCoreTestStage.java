// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.type.maybe;

import org.higherkindedj.hkt.maybe.Maybe;

/**
 * Stage 1: Configure Maybe test instances.
 *
 * <p>Entry point for Maybe core type testing with progressive disclosure.
 *
 * @param <T> The value type
 */
public final class MaybeCoreTestStage<T> {
    private final Class<?> contextClass;

    public MaybeCoreTestStage(Class<?> contextClass) {
        this.contextClass = contextClass;
    }

    /**
     * Provides a Just instance for testing.
     *
     * <p>Progressive disclosure: Next step is {@code .withNothing(...)}
     *
     * @param justInstance A Just instance
     * @return Next stage for configuring Nothing instance
     */
    public MaybeInstanceStage<T> withJust(Maybe<T> justInstance) {
        return new MaybeInstanceStage<>(contextClass, justInstance, null);
    }

    /**
     * Provides a Nothing instance for testing.
     *
     * <p>Progressive disclosure: Next step is {@code .withJust(...)}
     *
     * @param nothingInstance A Nothing instance
     * @return Next stage for configuring Just instance
     */
    public MaybeInstanceStage<T> withNothing(Maybe<T> nothingInstance) {
        return new MaybeInstanceStage<>(contextClass, null, nothingInstance);
    }
}