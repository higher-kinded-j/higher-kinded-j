// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.traverse;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;

/**
 * Stage 4: Configure applicative for traverse operations.
 *
 * <p>This stage captures the applicative type parameter G, enabling proper type inference.
 *
 * @param <F> The Traverse witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class TraverseMapperStage<F, A, B> {
    private final Class<?> contextClass;
    private final Traverse<F> traverse;
    private final Kind<F, A> validKind;
    private final Function<A, B> mapper;

    TraverseMapperStage(
            Class<?> contextClass,
            Traverse<F> traverse,
            Kind<F, A> validKind,
            Function<A, B> mapper) {
        this.contextClass = contextClass;
        this.traverse = traverse;
        this.validKind = validKind;
        this.mapper = mapper;
    }

    /**
     * Provides applicative and traverse function.
     *
     * <p>Progressive disclosure: Next step is {@code .withFoldableOperations(...)}
     *
     * <p>The type parameter G is automatically inferred from the applicative parameter.
     *
     * @param applicative The Applicative for traverse
     * @param traverseFunction The traverse function
     * @param <G> The Applicative witness type (automatically inferred)
     * @return Next stage for configuring foldable operations
     */
    public <G> TraverseApplicativeStage<F, G, A, B> withApplicative(
            Applicative<G> applicative,
            Function<A, Kind<G, B>> traverseFunction) {

        return new TraverseApplicativeStage<>(
                contextClass,
                traverse,
                validKind,
                mapper,
                applicative,
                traverseFunction);
    }
}