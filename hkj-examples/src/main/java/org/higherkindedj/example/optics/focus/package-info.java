// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Example applications demonstrating the Focus DSL for optics.
 *
 * <p>This package contains runnable examples showcasing Focus DSL features:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.example.optics.focus.NavigatorExample} - Fluent cross-type
 *       navigation using generated navigator classes
 *   <li>{@link org.higherkindedj.example.optics.focus.TraverseIntegrationExample} - Using
 *       traverseOver() with Traverse type class
 *   <li>{@link org.higherkindedj.example.optics.focus.ValidationPipelineExample} - Validation
 *       pipelines using modifyF() and foldMap()
 *   <li>{@link org.higherkindedj.example.optics.focus.AsyncFetchExample} - Async data loading with
 *       CompletableFuture and modifyF()
 * </ul>
 *
 * @see org.higherkindedj.optics.focus.FocusPath
 * @see org.higherkindedj.optics.focus.AffinePath
 * @see org.higherkindedj.optics.focus.TraversalPath
 */
@NullMarked
package org.higherkindedj.example.optics.focus;

import org.jspecify.annotations.NullMarked;
