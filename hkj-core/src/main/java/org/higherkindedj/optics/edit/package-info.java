// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
/**
 * Multi-edit builder over optics: apply N independent edits at different paths in one reusable,
 * composable operation — including the sparse, accumulating REST-{@code PATCH} shape.
 *
 * <p>The package has three parts:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.optics.edit.Edit} — a single <em>pure</em> edit at one path,
 *       created via {@code set}, {@code modify}, {@code setIfPresent}, {@code modifyIfPresent};
 *   <li>{@link org.higherkindedj.optics.edit.FallibleEdit} — an edit whose incoming value must
 *       first be parsed ({@code parseIfPresent}); failures are located {@link
 *       org.higherkindedj.hkt.validated.FieldError}s, labelled via {@code at};
 *   <li>{@link org.higherkindedj.optics.edit.Edits} — the combinators: {@code combine} folds pure
 *       edits into one {@link org.higherkindedj.hkt.Update}, and {@code accumulate} validates every
 *       fallible edit independently, reporting <em>all</em> bad fields at once.
 * </ul>
 *
 * <p>Absence is expressed with {@code null} incoming values: an {@code …IfPresent} edit whose value
 * is absent contributes the identity update — the algebraic basis for sparse partial updates. This
 * is a deliberate, documented exception to the library's eager null-rejection convention, confined
 * to the {@code …IfPresent} factories.
 */
@NullMarked
package org.higherkindedj.optics.edit;

import org.jspecify.annotations.NullMarked;
