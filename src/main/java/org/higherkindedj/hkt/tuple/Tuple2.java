// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

/**
 * An immutable tuple containing two elements of potentially different types.
 *
 * <p>As a {@link java.lang.Record}, it automatically provides a canonical constructor, accessors
 * ({@code _1()}, {@code _2()}), and implementations for {@code equals()}, {@code hashCode()}, and
 * {@code toString()}.
 *
 * @param <A> The type of the first element.
 * @param <B> The type of the second element.
 * @param _1 The first element of the tuple.
 * @param _2 The second element of the tuple.
 */
public record Tuple2<A, B>(A _1, B _2) implements Tuple {}
