// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.lazy;

import java.util.function.Function;
import org.higherkindedj.hkt.lazy.Lazy;

/**
 * Stage 3: Configure mapping functions for Lazy testing.
 *
 * <p>Progressive disclosure: Shows mapper configuration and execution options.
 *
 * @param <A> The value type
 */
public final class LazyOperationsStage<A> {
    private final Class<?> contextClass;
    private final Lazy<A> deferredInstance;
    private final Lazy<A> nowInstance;

    LazyOperationsStage(Class<?> contextClass, Lazy<A> deferredInstance, Lazy<A> nowInstance) {
        this.contextClass = contextClass;
        this.deferredInstance = deferredInstance;
        this.nowInstance = nowInstance;
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
    public <B> LazyTestConfigStage<A, B> withMappers(Function<A, B> mapper) {
        return new LazyTestConfigStage<>(contextClass, deferredInstance, nowInstance, mapper);
    }

    /**
     * Skip mapper configuration and proceed to testing.
     *
     * <p>This is useful when you only want to test operations that don't require mappers
     * (like force, toString, equals).
     *
     * @return Configuration stage without mappers
     */
    public LazyTestConfigStage<A, String> withoutMappers() {
        return new LazyTestConfigStage<>(contextClass, deferredInstance, nowInstance, null);
    }
}