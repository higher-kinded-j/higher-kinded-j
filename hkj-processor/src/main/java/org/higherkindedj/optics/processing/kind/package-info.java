// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Support for {@code Kind<F, A>} field analysis in the Focus DSL annotation processor.
 *
 * <p>This package provides the infrastructure for detecting and processing higher-kinded type
 * fields in records annotated with {@code @GenerateFocus}.
 *
 * <h2>Key Components</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.optics.processing.kind.KindFieldAnalyser} - Analyses fields to
 *       detect Kind types
 *   <li>{@link org.higherkindedj.optics.processing.kind.KindRegistry} - Registry of known library
 *       Kind types
 *   <li>{@link org.higherkindedj.optics.processing.kind.KindFieldInfo} - Analysis result record
 * </ul>
 *
 * <h2>Extensibility Design</h2>
 *
 * <p>The architecture supports future enhancements:
 *
 * <ul>
 *   <li>Nested Kind types ({@code Kind<F, Kind<G, A>>})
 *   <li>Custom Traverse registration via annotations
 *   <li>Package-level Kind mappings
 * </ul>
 *
 * @see org.higherkindedj.optics.annotations.TraverseField
 * @see org.higherkindedj.optics.annotations.KindSemantics
 */
package org.higherkindedj.optics.processing.kind;
