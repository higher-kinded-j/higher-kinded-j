// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Demonstrates spec interfaces for generating optics for external third-party types.
 *
 * <p>This package shows how to use {@code @ImportOptics} with {@code OpticsSpec<S>} to define
 * optics for types you don't own, such as:
 *
 * <ul>
 *   <li>{@code java.time.LocalDate} - using {@code @Wither} for wither methods
 *   <li>{@code tools.jackson.databind.JsonNode} - using {@code @MatchWhen} for prisms
 * </ul>
 *
 * @see org.higherkindedj.optics.annotations.ImportOptics
 * @see org.higherkindedj.optics.annotations.OpticsSpec
 */
@NullMarked
package org.higherkindedj.example.optics.external;

import org.jspecify.annotations.NullMarked;
