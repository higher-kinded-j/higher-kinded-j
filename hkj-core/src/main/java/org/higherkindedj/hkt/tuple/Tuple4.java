// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

/**
 * An immutable tuple containing four elements of potentially different types.
 *
 * <p>As a {@link java.lang.Record}, it automatically provides a canonical constructor, accessors
 * (e.g., {@code _1()}, {@code _2()}), and implementations for {@code equals()}, {@code hashCode()},
 * and {@code toString()}.
 *
 * @param <A> The type of the first element.
 * @param <B> The type of the second element.
 * @param <C> The type of the third element.
 * @param <D> The type of the fourth element.
 * @param _1 The first element of the tuple.
 * @param _2 The second element of the tuple.
 * @param _3 The third element of the tuple.
 * @param _4 The fourth element of the tuple.
 */
public record Tuple4<A, B, C, D>(A _1, B _2, C _3, D _4) implements Tuple {}
