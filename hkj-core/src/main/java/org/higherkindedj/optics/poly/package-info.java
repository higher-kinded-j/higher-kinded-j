// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Polymorphic optics: a small, intentionally narrow surface for type-changing optics.
 *
 * <p>The everyday optics in {@link org.higherkindedj.optics} ({@code Lens}, {@code Prism}, {@code
 * Iso}, {@code Affine}, {@code Traversal}, {@code Setter}) are <b>monomorphic</b>: they specialise
 * the four-parameter {@link org.higherkindedj.optics.Optic Optic} to {@code S = T} and {@code A =
 * B}. That covers the overwhelming majority of practical use cases and keeps the public API short
 * and easy to read.
 *
 * <p>This package is the escape hatch for the few cases where a type-changing optic is genuinely
 * the right tool, without forcing every user to pay the cost of four type parameters.
 *
 * <h2>When to reach for this package</h2>
 *
 * <ul>
 *   <li>You are authoring a generic wrapper or container type ({@code Box<A>}, {@code Tagged<T,
 *       A>}, ...) and want a polymorphic optic into its contents.
 *   <li>You want to compose monomorphic optics with a leaf step that changes element type, for
 *       example mapping {@code List<String>} to {@code List<Integer>} as part of a larger optic
 *       chain.
 * </ul>
 *
 * <h2>When you do not need this package</h2>
 *
 * <p>If you are bridging API/DTO formats, adapting wrapper types to existing optics, or migrating
 * between {@code V1} and {@code V2} of a record, the {@code contramap} / {@code map} / {@code
 * dimap} profunctor adapters on the existing monomorphic optics are very likely all you need. See
 * the <em>Profunctor Optics</em> chapter of the documentation.
 *
 * <h2>What is here</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.optics.poly.PolyOptics} factories ({@code polyLens}, {@code
 *       polyIso}) and runners ({@code modifyF}, {@code modify}, {@code set}, {@code get}) over the
 *       raw {@link org.higherkindedj.optics.Optic Optic} interface.
 *   <li>{@link org.higherkindedj.optics.poly.Optics} typeclass-driven factories ({@code mapped},
 *       {@code traversed}) that turn a {@link org.higherkindedj.hkt.Functor Functor} or {@link
 *       org.higherkindedj.hkt.Traverse Traverse} instance into a polymorphic optic over its
 *       contents.
 * </ul>
 */
@NullMarked
package org.higherkindedj.optics.poly;

import org.jspecify.annotations.NullMarked;
