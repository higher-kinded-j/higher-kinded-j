// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
/**
 * The validated-boundary optic: {@link org.higherkindedj.optics.validated.ValidatedPrism} — a
 * {@code Prism} whose match accumulates located reasons instead of answering yes/no.
 *
 * <p>The forward direction ({@code parse}, wire to domain) is fallible and reports every failure as
 * located {@link org.higherkindedj.hkt.validated.FieldError}s; the backward direction ({@code
 * build}, domain to wire) is total. Nested composition short-circuits; sibling fields accumulate
 * through the assembly builders or the {@code Edits} builder.
 */
@NullMarked
package org.higherkindedj.optics.validated;

import org.jspecify.annotations.NullMarked;
