// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.type.io;

import org.higherkindedj.hkt.io.IO;

/**
 * Stage 1: Configure IO test instances.
 *
 * <p>Entry point for IO core type testing with progressive disclosure.
 *
 * @param <A> The value type
 */
public final class IOCoreTestStage<A> {
    private final Class<?> contextClass;

    public IOCoreTestStage(Class<?> contextClass) {
        this.contextClass = contextClass;
    }

    /**
     * Provides an IO instance for testing.
     *
     * <p>Progressive disclosure: Next step is {@code .withMapper(...)}
     *
     * @param ioInstance An IO instance
     * @return Next stage for configuring mapper
     */
    public IOOperationsStage<A> withIO(IO<A> ioInstance) {
        return new IOOperationsStage<>(contextClass, ioInstance);
    }
}