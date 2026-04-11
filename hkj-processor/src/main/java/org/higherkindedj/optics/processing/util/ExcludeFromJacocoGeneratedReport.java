// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or type as excluded from JaCoCo coverage measurement.
 *
 * <p>JaCoCo (since 0.8.2) automatically filters out any element annotated with an annotation whose
 * simple name contains {@code "Generated"} and whose retention is {@code CLASS} or {@code RUNTIME}.
 * This annotation leverages that mechanism to mark <strong>structurally untestable</strong> code —
 * specifically the {@code catch (IOException e)} blocks that wrap {@code javaFile.writeTo(filer)}
 * calls inside annotation processors.
 *
 * <p>These catches cannot be exercised through the standard {@code google/compile-testing} harness:
 * the in-memory {@code Filer} used by {@code javac()} never throws {@code IOException}. The catches
 * remain in production code as defensive error-reporting, but excluding them from coverage metrics
 * keeps the coverage report focused on genuinely exercisable code paths.
 *
 * <p><strong>Usage guidelines:</strong>
 *
 * <ul>
 *   <li>Apply only to private helper methods that wrap an {@code IOException} catch around a {@code
 *       Filer}-writing call.
 *   <li>Do not use to silence coverage gaps in ordinary business logic — fix the tests instead.
 *   <li>Do not apply to public API methods; this is an internal build-metric annotation.
 * </ul>
 *
 * @see <a href="https://www.jacoco.org/jacoco/trunk/doc/changes.html">JaCoCo release notes
 *     (annotation filtering)</a>
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE})
public @interface ExcludeFromJacocoGeneratedReport {}
