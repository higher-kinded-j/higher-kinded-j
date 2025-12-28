// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
/**
 * OpenRewrite recipes for migrating Higher-Kinded-J code to v2.0.
 *
 * <p>This package contains recipes that help automate the migration from Higher-Kinded-J v1.x to
 * v2.0, which introduces the embedded kind system with {@code WitnessArity} and {@code TypeArity}.
 *
 * <h2>Available Recipes</h2>
 *
 * <ul>
 *   <li>{@code org.higherkindedj.openrewrite.UpgradeToV2} - Complete migration recipe
 *   <li>{@code org.higherkindedj.openrewrite.AddWitnessArityImports} - Adds required imports
 *   <li>{@code org.higherkindedj.openrewrite.AddWitnessArityToWitness} - Adds WitnessArity to
 *       witness classes
 *   <li>{@code org.higherkindedj.openrewrite.AddArityBoundsToTypeParameters} - Adds bounds to type
 *       parameters
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <p>Add to your build.gradle.kts:
 *
 * <pre>{@code
 * plugins {
 *     id("org.openrewrite.rewrite") version "7.21.0"
 * }
 *
 * dependencies {
 *     rewrite("io.github.higher-kinded-j:hkj-openrewrite:2.0.0")
 * }
 *
 * rewrite {
 *     activeRecipe("org.higherkindedj.openrewrite.UpgradeToV2")
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
