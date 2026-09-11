// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts one domain {@code Optional<T>} component into the null-as-absent bridge against a
 * <em>record</em> wire.
 *
 * <p>A bean wire bridges automatically, because bean conventions leave {@code Optional} off
 * property types. A record wire does not: on a record, {@code null} is a defect by default, so a
 * nullable component would parse to a located {@code must not be null}. Real-world record DTOs
 * nonetheless carry optional data as a nullable reference component (Jackson's default), and this
 * annotation is how a spec says so — per component, never implicitly:
 *
 * <pre>{@code
 * public record Customer(String name, Optional<String> nickname) {}
 * public record CustomerDto(String name, String nickname) {}   // nullable, by JSON convention
 *
 * @GenerateMapping
 * public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
 *   @OptionalBridge
 *   Optional<String> nickname();
 * }
 * // parse: null -> Valid(Optional.empty());  "ada" -> Valid(Optional.of("ada"))
 * // build: Optional.empty() -> null;         Optional.of("ada") -> "ada"
 * }</pre>
 *
 * <p><b>Two placements, one meaning.</b> Which one a component uses is decided by whether its
 * element needs converting, and the two can never be combined — a bare marker and a same-named leaf
 * declare the same method with incompatible return types, which javac rejects:
 *
 * <ul>
 *   <li><b>On a bare abstract method</b> named after the domain component, as above, when the
 *       present element copies by identity. The method is a marker the generated Impl stubs out,
 *       exactly like a {@link MapField} rename, and its return type declares the domain component's
 *       type, so a spec that drifts from its domain fails to compile rather than bridging the wrong
 *       component.
 *   <li><b>On that component's {@code default} leaf</b> when the present element converts, so the
 *       leaf validates the value the bridge found:
 *       <pre>{@code
 * @OptionalBridge
 * default ValidatedPrism<String, Nickname> nickname() { return NICKNAME; }
 * }</pre>
 *       The leaf is declared over the <em>element</em> types ({@code ValidatedPrism<WireComponent,
 *       OptionalElement>}), not over the {@code Optional}, matching the bean bridge.
 * </ul>
 *
 * <p><b>The contract.</b> {@code build} maps an empty {@code Optional} to a {@code null} wire
 * component and a present one to its value; {@code parse} reads {@code Optional.ofNullable}, so a
 * {@code null} is deliberate absence rather than a located error. A bridged component is the one
 * carve-out in the {@linkplain MappingSpec null doctrine}: absence is expressible on a record wire
 * only where a spec has asked for it here.
 *
 * <p>A bridged component is a non-identity correspondence, exactly as on a bean wire, so a mapping
 * carrying one withholds {@code asIso()} and a projection carrying one maps through the validated
 * {@code patch(Domain, Wire)} rather than {@code asLens()}.
 *
 * <p>The annotation is refused where it can mean nothing: a domain component that is not {@code
 * Optional}, a wire component that is already {@code Optional} (which needs no bridge), a sealed
 * mapping, and a sparse {@link UpdateSpec} (whose {@code null} already means "leave unchanged"). On
 * a bean wire it is redundant rather than wrong — the bridge is automatic there — and a locally
 * declared one is reported as a note, so a shared mix-in vocabulary can carry it for both wire
 * shapes.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface OptionalBridge {}
