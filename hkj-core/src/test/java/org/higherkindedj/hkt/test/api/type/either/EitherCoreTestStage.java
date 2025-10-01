// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.type.either;

import org.higherkindedj.hkt.either.Either;

/**
 * Stage 1: Configure Either test instances.
 *
 * <p>Entry point for Either core type testing with progressive disclosure.
 *
 * @param <L> The Left type
 * @param <R> The Right type
 */
public final class EitherCoreTestStage<L, R> {
    private final Class<?> contextClass;

    public EitherCoreTestStage(Class<?> contextClass) {
        this.contextClass = contextClass;
    }

    /**
     * Provides a Left instance for testing.
     *
     * <p>Progressive disclosure: Next step is {@code .withRight(...)}
     *
     * @param leftInstance A Left instance (can have null value)
     * @return Next stage for configuring Right instance
     */
    public EitherInstanceStage<L, R> withLeft(Either<L, R> leftInstance) {
        return new EitherInstanceStage<>(contextClass, leftInstance, null);
    }

    /**
     * Provides a Right instance for testing.
     *
     * <p>Progressive disclosure: Next step is {@code .withLeft(...)}
     *
     * @param rightInstance A Right instance (can have null value)
     * @return Next stage for configuring Left instance
     */
    public EitherInstanceStage<L, R> withRight(Either<L, R> rightInstance) {
        return new EitherInstanceStage<>(contextClass, null, rightInstance);
    }
}