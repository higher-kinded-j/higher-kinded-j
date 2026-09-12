// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

/**
 * The specification interface for a generated sparse PATCH write-back — the null-as-absent sibling
 * of {@link MappingSpec}.
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
 * not a projection of information, and an all-absent wire is <em>valid</em>, not a total parse):
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
 * <p>The wire type {@code W} must be a bean-shaped class (a record cannot distinguish an absent
 * component from a null-typed one), and every wire property must be reference-typed — a primitive
 * property is always present, so it can never carry the null-as-absent signal and is rejected with
 * a diagnostic pointing at the wrapper type. The domain type {@code D} must be a record. Coverage
 * is one-sided: every wire property maps to a domain component, but a domain component with no wire
 * property is simply never changed.
 *
 * <p>A getter-only {@code List} property is rejected: the JAXB convention creates the list on first
 * call, so the property never reads {@code null} and cannot express <em>not provided</em>. Give it
 * a setter, which an omitted field leaves {@code null}.
 *
 * <p>A domain {@code Optional<T>} component takes an {@code Optional}-typed property whose field
 * defaults to {@code null}: {@code null} leaves the component unchanged and a present empty {@code
 * Optional} clears it. A plain property is rejected unless a whole-component leaf converts it,
 * because its {@code null} already means leave unchanged; and a field defaulting to {@code
 * Optional.empty()} would clear the component on every request that omits it.
 *
 * <p>A spec names one tier: an interface extending {@code UpdateSpec} must not also extend {@link
 * MappingSpec}, and declaring both is rejected with a diagnostic. One interface generates one Impl,
 * and the two tiers emit disjoint members, so nothing an Impl could carry answers both clauses. A
 * domain needing both is a pair of specs, which may share their renames and leaves through a plain
 * mix-in interface both extend.
 *
 * @param <D> the domain type (a record)
 * @param <W> the wire type (a bean-shaped PATCH DTO)
 * @see MappingSpec
 */
public interface UpdateSpec<D, W> {}
