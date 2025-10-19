// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for licence information.
package org.higherkindedj.hkt.test.api.coretype.reader_t;

import java.util.function.Function;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.reader_t.ReaderT;

/**
 * Stage 2: Configure mapping functions for ReaderT testing.
 *
 * <p>Progressive disclosure: Shows mapper configuration and execution options.
 *
 * @param <F> The outer monad witness type
 * @param <R> The environment type
 * @param <A> The value type
 */
public final class ReaderTOperationsStage<F, R, A> {
    private final Class<?> contextClass;
    private final Monad<F> outerMonad;
    private final ReaderT<F, R, A> readerTInstance;

    ReaderTOperationsStage(
            Class<?> contextClass, Monad<F> outerMonad, ReaderT<F, R, A> readerTInstance) {
        this.contextClass = contextClass;
        this.outerMonad = outerMonad;
        this.readerTInstance = readerTInstance;
    }

    /**
     * Provides mapping functions for testing map and flatMap operations.
     *
     * <p>Progressive disclosure: Next steps are test selection or execution.
     *
     * @param mapper The mapping function (A -> B)
     * @param <B> The mapped type
     * @return Configuration stage with execution options
     */
    public <B> ReaderTTestConfigStage<F, R, A, B> withMappers(Function<A, B> mapper) {
        return new ReaderTTestConfigStage<>(contextClass, outerMonad, readerTInstance, mapper);
    }

    /**
     * Skip mapper configuration and proceed to testing.
     *
     * <p>This is useful when you only want to test operations that don't require mappers (like
     * factory methods, runner methods).
     *
     * @return Configuration stage without mappers
     */
    public ReaderTTestConfigStage<F, R, A, String> withoutMappers() {
        return new ReaderTTestConfigStage<>(contextClass, outerMonad, readerTInstance, null);
    }
}