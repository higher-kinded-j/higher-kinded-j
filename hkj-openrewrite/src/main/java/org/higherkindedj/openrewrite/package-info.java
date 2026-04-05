// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
/**
 * OpenRewrite recipes for Higher-Kinded-J code migrations.
 *
 * <h2>Arity Migration (0.2.x to 0.3.0)</h2>
 *
 * <p>Recipes that automate the migration from 0.2.x to 0.3.0, which introduced the type arity
 * system with {@code WitnessArity} and {@code TypeArity}.
 *
 * <ul>
 *   <li>{@code org.higherkindedj.openrewrite.AddArityBounds} - Complete arity migration recipe
 *   <li>{@code org.higherkindedj.openrewrite.AddWitnessArityImports} - Adds required imports
 *   <li>{@code org.higherkindedj.openrewrite.AddWitnessArityToWitness} - Adds WitnessArity to
 *       witness classes
 *   <li>{@code org.higherkindedj.openrewrite.AddArityBoundsToTypeParameters} - Adds bounds to type
 *       parameters
 * </ul>
 *
 * <h2>Effect Algebra Migration</h2>
 *
 * <p>Recipes that help migrate effect algebra code to use {@code @EffectAlgebra} and
 * {@code @ComposeEffects} generated infrastructure.
 *
 * <ul>
 *   <li>{@code org.higherkindedj.openrewrite.EffectAlgebraMigration} - Complete effect migration
 *   <li>{@code org.higherkindedj.openrewrite.AddHandleErrorCaseRecipe} - Detects Free switches
 *       missing HandleError/Ap cases
 *   <li>{@code org.higherkindedj.openrewrite.ConvertRawFreeToFreePathRecipe} - Detects raw Free
 *       usage for FreePath migration
 *   <li>{@code org.higherkindedj.openrewrite.DetectInjectBoilerplateRecipe} - Detects manual Inject
 *       construction
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * plugins {
 *     id("org.openrewrite.rewrite") version "7.21.0"
 * }
 *
 * dependencies {
 *     rewrite("io.github.higher-kinded-j:hkj-openrewrite:0.3.0")
 * }
 *
 * rewrite {
 *     activeRecipe("org.higherkindedj.openrewrite.AddArityBounds")
 * }
 * }</pre>
 *
 * <p>Then run:
 *
 * <pre>{@code
 * ./gradlew rewriteRun
 * }</pre>
 */
package org.higherkindedj.openrewrite;
