// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either;

import org.higherkindedj.hkt.either.Either;

/**
 * Stage 2: Complete Either instance configuration.
 *
 * <p>Progressive disclosure: Shows completion of instance setup.
 *
 * @param <L> The Left type
 * @param <R> The Right type
 */
public final class EitherInstanceStage<L, R> {
    private final Class<?> contextClass;
    private final Either<L, R> leftInstance;
    private final Either<L, R> rightInstance;

    EitherInstanceStage(Class<?> contextClass, Either<L, R> leftInstance, Either<L, R> rightInstance) {
        this.contextClass = contextClass;
        this.leftInstance = leftInstance;
        this.rightInstance = rightInstance;
    }

    /**
     * Provides the Right instance (if Left was configured first).
     *
     * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
     *
     * @param rightInstance A Right instance
     * @return Next stage for configuring mappers
     */
    public EitherOperationsStage<L, R> withRight(Either<L, R> rightInstance) {
        if (this.leftInstance == null) {
            throw new IllegalStateException("Cannot set Right twice");
        }
        return new EitherOperationsStage<>(contextClass, leftInstance, rightInstance);
    }

    /**
     * Provides the Left instance (if Right was configured first).
     *
     * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
     *
     * @param leftInstance A Left instance
     * @return Next stage for configuring mappers
     */
    public EitherOperationsStage<L, R> withLeft(Either<L, R> leftInstance) {
        if (this.rightInstance == null) {
            throw new IllegalStateException("Cannot set Left twice");
        }
        return new EitherOperationsStage<>(contextClass, leftInstance, rightInstance);
    }
}