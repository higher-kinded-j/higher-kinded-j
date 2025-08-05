// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import org.jspecify.annotations.NullMarked;

/**
 * The core of the Higher-Kinded Type (HKT) simulation.
 *
 * <p>{@code Kind<F, A>} is a type that represents the application of a type constructor {@code F}
 * to a type argument {@code A}. Since Java's type system does not natively support type
 * constructors as parameters (like F&lt;_&gt;), we use a "witness type" for {@code F} to stand in
 * for the constructor.
 *
 * <p>For example, a {@code java.util.List<String>} would be represented as {@code
 * Kind<ListKind.Witness, String>}, where {@code ListKind.Witness} is the marker type that
 * represents the {@code List} type constructor.
 *
 * @param <F> The witness type for the type constructor.
 * @param <A> The type of the value contained within the context.
 */
@NullMarked
public interface Kind<F, A> {}
