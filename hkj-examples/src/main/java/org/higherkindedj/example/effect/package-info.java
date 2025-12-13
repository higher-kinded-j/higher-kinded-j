// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Examples demonstrating the Effect Path API.
 *
 * <p>This package contains runnable examples showing how to use the Effect Path API for fluent
 * effect composition.
 *
 * <h2>Examples</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.example.effect.BasicPathExample} - Introduction to Path API
 *   <li>{@link org.higherkindedj.example.effect.ChainedComputationsExample} - Sequential
 *       composition with via/flatMap
 *   <li>{@link org.higherkindedj.example.effect.ErrorHandlingExample} - Error recovery patterns
 *   <li>{@link org.higherkindedj.example.effect.ValidationPipelineExample} - Form validation with
 *       zipWith
 *   <li>{@link org.higherkindedj.example.effect.ServiceLayerExample} - Repository pattern with IO
 * </ul>
 *
 * <h2>Running Examples</h2>
 *
 * <p>Each example has a {@code main} method and can be run via Gradle:
 *
 * <pre>{@code
 * ./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.effect.BasicPathExample
 * }</pre>
 */
@org.jspecify.annotations.NullMarked
package org.higherkindedj.example.effect;
