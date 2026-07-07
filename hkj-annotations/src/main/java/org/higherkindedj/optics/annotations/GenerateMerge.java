// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a compile-time, reflection-free assembly of one target record from several source
 * records (issue #613). Forward-only: the multi-source case has no general inverse, so none is
 * generated (truthful types).
 *
 * <p>The spec method's signature <em>is</em> the merge declaration — target and sources are carried
 * by a typed, refactor-safe abstract method, never by class-literal annotation attributes:
 *
 * <pre>{@code
 * @GenerateMerge
 * public interface DashboardAssembly {
 *   DashboardDto assemble(User user, Account account, Settings settings);
 * }
 *
 * DashboardDto dashboard = DashboardAssemblyImpl.INSTANCE.assemble(user, account, settings);
 * }</pre>
 *
 * <p>Each target component is filled from the one source with a same-named component: identity when
 * the types match, through a zero-parameter {@code default} leaf method returning {@code
 * ValidatedPrism<SourceComponent, TargetComponent>} (an explicit leaf wins even over a same-typed
 * match, so it can validate or normalise a copied value), or through a sibling
 * {@code @GenerateMapping} spec in the same compilation — nested failures locate as dotted paths.
 * An ambiguous component (same name in two sources) or an unfilled one is a compile error. With any
 * fallible fill the declared return type must be {@code Validated<NonEmptyList<FieldError>,
 * Target>} — the types never over- or under-claim fallibility (and the fallible path inherits
 * {@code Validated}'s null-hostility: component values must not be null).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateMerge {}
