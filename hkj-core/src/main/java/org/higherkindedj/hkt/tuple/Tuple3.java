// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * An immutable tuple containing three elements of potentially different types.
 *
 * <p>As a {@link java.lang.Record}, it automatically provides a canonical constructor, accessors
 * ({@code _1()}, {@code _2()}, {@code _3()}), and implementations for {@code equals()}, {@code
 * hashCode()}, and {@code toString()}.
 *
 * @param <A> The type of the first element.
 * @param <B> The type of the second element.
 * @param <C> The type of the third element.
 * @param _1 The first element of the tuple.
 * @param _2 The second element of the tuple.
 * @param _3 The third element of the tuple.
 */
@GenerateLenses
public record Tuple3<A, B, C>(A _1, B _2, C _3) implements Tuple {}
