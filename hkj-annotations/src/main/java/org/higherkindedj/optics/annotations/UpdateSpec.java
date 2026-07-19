// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

/**
 * The specification interface for a generated sparse PATCH write-back — the null-as-absent sibling
 * of {@link MappingSpec} (issue #645).
 *
 * <p>Declare an interface extending {@code UpdateSpec<Domain, Wire>} and annotate it with {@link
 * GenerateMapping}. Where a {@link MappingSpec} treats a null bean property as broken data (a
 * located {@code FieldError}), an {@code UpdateSpec} treats it as <em>not provided, leave
 * unchanged</em> — the contract a REST PATCH request DTO follows, where the client sends only the
 * fields it wants to change and the generated bean arrives with everything else null. The two
 * meanings of null are a property of the DTO's contract, not something the mapper may infer, so
 * sparse semantics are an explicit opt-in at the declaration site — never a silent reinterpretation
 * of a {@link MappingSpec}.
 *
 * <p>The generated {@code <Spec>Impl} exposes a single method and nothing else — no {@code build},
 * no {@code parse}, no {@code asIso}/{@code asLens}/{@code asValidatedPrism} (a sparse mapping is
 * not a projection of information, and an all-null wire is <em>valid</em>, not a total parse):
 *
 * <pre>{@code
 * public record User(String name, EmailAddress email, int age) {}
 * // wire properties are wrappers: a primitive can never be absent (rejected with a diagnostic).
 * public class UserPatchDto {
 *   String getName(); void setName(String);
 *   String getEmail(); void setEmail(String);
 *   Integer getAge(); void setAge(Integer);
 * }
 *
 * @GenerateMapping
 * public interface UserPatchMapping extends UpdateSpec<User, UserPatchDto> {
 *   default ValidatedPrism<String, EmailAddress> email() { return EmailCodecs.EMAIL; }
 * }
 * // generates UserPatchMappingImpl with:
 * //   Edits.Accumulated<User> updateFrom(UserPatchDto)   (folds the PRESENT fields into an Update)
 *
 * Edits.Accumulated<User> u = UserPatchMappingImpl.INSTANCE.updateFrom(dto);
 * Validated<NonEmptyList<FieldError>, User> patched = u.apply(current); // or applyPath / toValidated
 * }</pre>
 *
 * <ul>
 *   <li><b>Present and valid</b> — the field is set (or parsed through its leaf) and folded into
 *       the accumulated {@code Update}, composing with {@code Monoids.update()} and the {@code
 *       Edits} ecosystem.
 *   <li><b>Present and invalid</b> — a located {@code FieldError}, accumulating as usual:
 *       sparseness never weakens validation of what <em>was</em> sent.
 *   <li><b>Absent (null)</b> — skipped; the domain's current value survives.
 * </ul>
 *
 * <p>The wire type {@code B} must be a bean-shaped class (a record cannot distinguish an absent
 * component from a null-typed one), and every wire property must be reference-typed — a primitive
 * property is always present, so it can never carry the null-as-absent signal and is rejected with
 * a diagnostic pointing at the wrapper type. The domain type {@code A} must be a record. Coverage
 * is one-sided: every wire property maps to a domain component, but a domain component with no wire
 * property is simply never changed.
 *
 * @param <A> the domain type (a record)
 * @param <B> the wire type (a bean-shaped PATCH DTO)
 * @see MappingSpec
 */
public interface UpdateSpec<A, B> extends MappingSpec<A, B> {}
