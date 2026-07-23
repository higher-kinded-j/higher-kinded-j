// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.laws;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * Law-verification helpers for a generated {@code @GenerateMapping} Impl, checked through its
 * exposed optics - one {@code assertMappingLaws} overload per emission tier.
 *
 * <p>Flat {@code assert...} helpers in the same style as the other optic-law classes; comparison is
 * by {@code equals} - right for records. Pick the overload matching the surface the generator
 * emitted:
 *
 * <ul>
 *   <li>lossless tier ({@code asIso()} present): pass the iso and the prism from {@code
 *       asValidatedPrism()} - the iso laws, both validated round trips, and the coherence between
 *       the two surfaces;
 *   <li>projection tier ({@code asLens()} present): pass the lens - the three lens laws;
 *   <li>fallible tier ({@code asValidatedPrism()} only): pass a parsing and a non-parsing wire
 *       value - both validated round-trip laws plus the no-parse sanity check;
 *   <li>total-parse mappings (no wire value can fail, e.g. derived wire fields over identity
 *       components): pass a domain sample - the round trip through {@code build}.
 *   <li>sparse-update tier ({@code updateFrom()} only, from an {@code UpdateSpec}): pass the {@code
 *       updateFrom} method reference, a domain sample, and an all-absent, a valid and an invalid
 *       wire - the identity, idempotence and validation laws.
 *   <li>validated-patch tier ({@code patch(domain, wire)} on a leaf-carrying projection, issue
 *       #625): pass the {@code patch} and {@code build} method references, a domain sample, and a
 *       valid and an invalid wire - the projection identity, idempotence and located-validation
 *       laws. A DENSE write-back (every projected component applies; null is a located error) - the
 *       opposite of the sparse-update tier above. {@code build} after {@code patch} is deliberately
 *       NOT a law: a normalising leaf rewrites the wire form by design.
 * </ul>
 *
 * <p>The derived-field reality: {@code build} recomputes a derived wire component and {@code parse}
 * ignores it, so {@code build} after {@code parse} restores only the NON-derived components. A wire
 * value whose derived component disagrees with the rest still parses, but rebuilds normalised - by
 * design, not a law violation. Law-check such mappings with {@link
 * #assertMappingLaws(ValidatedPrism, Object)}, which round-trips through consistent wire values
 * only, or pass the fallible overload a parseable wire whose derived components are exactly what
 * {@code build} would produce.
 */
public final class MappingLaws {

  private MappingLaws() {}

  /**
   * All laws of the lossless tier: the iso laws for {@code asIso()}, both validated round trips for
   * {@code asValidatedPrism()}, and the coherence between the two surfaces.
   *
   * <p>Guards against a vacuous fixture in both directions: {@code wireSample} must not simply be
   * {@code asIso().get(domainSample)}, and {@code domainSample} must not simply be {@code
   * asIso().reverseGet(wireSample)}, otherwise the two round-trip directions collapse into one.
   */
  public static <D, W> void assertMappingLaws(
      Iso<D, W> iso, ValidatedPrism<W, D> mapping, D domainSample, W wireSample) {
    assertThat(wireSample)
        .as(
            "assertMappingLaws needs a wire sample INDEPENDENT of the domain sample; %s is just"
                + " asIso().get(domainSample)",
            wireSample)
        .isNotEqualTo(iso.get(domainSample));
    assertThat(domainSample)
        .as(
            "assertMappingLaws needs a domain sample INDEPENDENT of the wire sample; %s is just"
                + " asIso().reverseGet(wireSample)",
            domainSample)
        .isNotEqualTo(iso.reverseGet(wireSample));
    IsoLaws.assertIsoLaws(iso, domainSample, wireSample);
    assertBuildAgreesWithIso(iso, mapping, domainSample);
    assertParseAgreesWithIso(iso, mapping, wireSample);
    ValidatedPrismLaws.assertParseBuild(mapping, domainSample);
    ValidatedPrismLaws.assertBuildParse(mapping, wireSample);
  }

  /**
   * Build-iso coherence: {@code build(a) == asIso().get(a)} - the mapping offers one forward
   * direction, not two.
   */
  public static <D, W> void assertBuildAgreesWithIso(
      Iso<D, W> iso, ValidatedPrism<W, D> mapping, D domainSample) {
    W built = mapping.build(domainSample);
    assertThat(built)
        .as(
            "Mapping build-iso coherence: build(%s) == asIso().get(it); got %s",
            domainSample, built)
        .isEqualTo(iso.get(domainSample));
  }

  /**
   * Parse-iso coherence: {@code parse(s) == Valid(asIso().reverseGet(s))} - a lossless parse is
   * total and agrees with the iso's independently generated reverse direction.
   */
  public static <D, W> void assertParseAgreesWithIso(
      Iso<D, W> iso, ValidatedPrism<W, D> mapping, W wireSample) {
    Validated<NonEmptyList<FieldError>, D> parsed = mapping.parse(wireSample);
    assertThat(parsed)
        .as(
            "Mapping parse-iso coherence: parse(%s) == Valid(asIso().reverseGet(it)); got %s",
            wireSample, parsed)
        .isEqualTo(Validated.validNel(iso.reverseGet(wireSample)));
  }

  /**
   * All laws of the projection tier: the three lens laws for {@code asLens()} ({@code get} is
   * {@code build}; {@code set} writes the wire components back and keeps the rest of the domain).
   * Delegates to {@link LensLaws#assertLensLaws}, including its distinct-values guard.
   */
  public static <D, W> void assertMappingLaws(
      Lens<D, W> lens, D domainSample, W wireSample1, W wireSample2) {
    LensLaws.assertLensLaws(lens, domainSample, wireSample1, wireSample2);
  }

  /**
   * All laws of the fallible tier: both validated round trips plus the no-parse sanity check for
   * {@code asValidatedPrism()}. Delegates to {@link ValidatedPrismLaws#assertValidatedPrismLaws},
   * including its guards (the two wire values must be distinct, and the first must parse).
   *
   * <p>For a spec with derived wire fields, {@code parseableWire}'s derived components must be
   * consistent (exactly what {@code build} would produce); {@code parse} ignores them, so an
   * inconsistent value parses but rebuilds normalised and fails the section law by design.
   */
  public static <W, D> void assertMappingLaws(
      ValidatedPrism<W, D> mapping, W parseableWire, W nonParseableWire) {
    ValidatedPrismLaws.assertValidatedPrismLaws(mapping, parseableWire, nonParseableWire);
  }

  /**
   * The round-trip law of a total-parse mapping (identity components, infallible leaves or derived
   * wire fields): exactly {@code parse(build(domainSample)) == Valid(domainSample)}, and nothing
   * else. No non-parsing wire value exists for such a mapping, so there is no no-parse check; and
   * the section law on {@code build(domainSample)} would be checking {@code build(a) == build(a)}
   * once the round trip holds, so it is deliberately not asserted.
   *
   * <p>This is the strongest guarantee a derived-field mapping offers: only NON-derived components
   * round-trip, and {@code build(domainSample)} is a wire value whose derived components are
   * consistent by construction.
   *
   * <p>Caveat: this weak overload also PASSES on a genuinely fallible mapping without exercising
   * any failure path, because {@code build} only ever produces parseable wire values. For a mapping
   * with a fallible leaf, use {@link #assertMappingLaws(ValidatedPrism, Object, Object)} with a
   * non-parsing wire sample instead.
   */
  public static <W, D> void assertMappingLaws(ValidatedPrism<W, D> mapping, D domainSample) {
    ValidatedPrismLaws.assertParseBuild(mapping, domainSample);
  }

  /**
   * All laws of the sparse-update tier ({@code updateFrom(Wire) : Edits.Accumulated<Domain>}, issue
   * #645): the identity, idempotence and validation laws that make a null-as-absent PATCH lawful.
   *
   * <ul>
   *   <li><b>Identity</b> — {@code updateFrom(allAbsentWire).apply(d) == Valid(d)}: an all-absent
   *       wire folds to the identity update, leaving the domain untouched.
   *   <li><b>Idempotence</b> — applying a valid patch twice equals applying it once. This holds
   *       because generated edits <em>set</em> or <em>parse</em> (overwrite), never
   *       <em>modify</em>; a modify-shaped edit (e.g. increment) is deliberately not idempotent and
   *       would fail here.
   *   <li><b>Validation</b> — {@code updateFrom(invalidWire).apply(d)} is {@code Invalid}: a
   *       present but invalid field fails, so sparseness never weakens validation of what was sent.
   * </ul>
   *
   * <p>Monoidal composition of the folded {@code Update} is {@code Monoids.update()}'s own law
   * suite (verified there); this helper checks only the laws specific to {@code updateFrom}.
   *
   * <p>Guards against vacuous fixtures: {@code validWire} must parse and must actually change
   * {@code domainSample} (otherwise idempotence is trivially true), and {@code invalidWire} must
   * fail (otherwise the validation law is not exercised).
   *
   * @param updateFrom the generated {@code Impl.INSTANCE::updateFrom}
   * @param domainSample the current domain value a patch is applied onto
   * @param allAbsentWire a wire with every property absent (null)
   * @param validWire a wire with at least one present, valid property that changes the domain
   * @param invalidWire a wire with a present but invalid property
   */
  public static <W, D> void assertMappingLaws(
      Function<? super W, Edits.Accumulated<D>> updateFrom,
      D domainSample,
      W allAbsentWire,
      W validWire,
      W invalidWire) {
    assertSparseIdentity(updateFrom, domainSample, allAbsentWire);
    assertSparseIdempotent(updateFrom, domainSample, validWire);
    assertSparseValidationFails(updateFrom, domainSample, invalidWire);
  }

  /**
   * All laws of the validated-patch tier ({@code patch(Domain, Wire) :
   * Validated<NonEmptyList<FieldError>, Domain>} on a leaf-carrying projection, issue #625): the
   * projection identity, idempotence and located-validation laws that make a dense write-back
   * lawful.
   *
   * <ul>
   *   <li><b>Projection identity</b> - {@code patch(d, build(d)) == Valid(d)}: writing the domain's
   *       own projection back is the identity (each leaf's section law lifted to the record).
   *   <li><b>Idempotence</b> - applying a valid wire twice equals applying it once. Holds because
   *       generated legs <em>set</em> or <em>parse</em> (overwrite), never <em>modify</em>.
   *   <li><b>Located validation</b> - {@code patch(d, invalidWire)} is {@code Invalid} and every
   *       accumulated error carries a non-empty path: the tier's every-bad-field-located promise.
   * </ul>
   *
   * <p>{@code build(patched) == validWire} is deliberately NOT asserted: a normalising leaf (trim,
   * lowercase) rewrites the wire form by design, the same weakening as the fallible full tier.
   *
   * <p>Guards against vacuous fixtures: {@code validWire} must parse and must actually change
   * {@code domainSample} (otherwise idempotence is trivially true), and {@code invalidWire} must
   * fail (otherwise the validation law is not exercised).
   *
   * @param patch the generated {@code Impl.INSTANCE::patch}
   * @param build the generated {@code Impl.INSTANCE::build}
   * @param domainSample the domain value the wire is written onto
   * @param validWire a wire that parses and changes the domain
   * @param invalidWire a wire with at least one invalid projected component
   */
  public static <D, W> void assertMappingLaws(
      BiFunction<? super D, ? super W, Validated<NonEmptyList<FieldError>, D>> patch,
      Function<? super D, ? extends W> build,
      D domainSample,
      W validWire,
      W invalidWire) {
    assertPatchIdentity(patch, build, domainSample);
    assertPatchIdempotent(patch, domainSample, validWire);
    assertPatchValidationFails(patch, domainSample, invalidWire);
  }

  /**
   * Patch identity law: writing the domain's own projection back is the identity, so {@code
   * patch(domainSample, build(domainSample)) == Valid(domainSample)}.
   */
  public static <D, W> void assertPatchIdentity(
      BiFunction<? super D, ? super W, Validated<NonEmptyList<FieldError>, D>> patch,
      Function<? super D, ? extends W> build,
      D domainSample) {
    W projected = build.apply(domainSample);
    Validated<NonEmptyList<FieldError>, D> result = patch.apply(domainSample, projected);
    assertThat(result)
        .as("Patch identity law: patch(%s, build(it)) == Valid(it); got %s", domainSample, result)
        .isEqualTo(Validated.validNel(domainSample));
  }

  /**
   * Patch idempotence law: applying the same valid wire twice equals applying it once. Guards that
   * {@code validWire} parses and genuinely changes the domain, so the law is not vacuously true.
   */
  public static <D, W> void assertPatchIdempotent(
      BiFunction<? super D, ? super W, Validated<NonEmptyList<FieldError>, D>> patch,
      D domainSample,
      W validWire) {
    Validated<NonEmptyList<FieldError>, D> once = patch.apply(domainSample, validWire);
    assertThat(once.isValid())
        .as(
            "Patch idempotence needs a validWire that PARSES; patch(%s, validWire) was %s",
            domainSample, once)
        .isTrue();
    D patched = once.get();
    assertThat(patched)
        .as(
            "Patch idempotence needs a validWire that CHANGES the domain; %s left %s unchanged",
            validWire, domainSample)
        .isNotEqualTo(domainSample);
    Validated<NonEmptyList<FieldError>, D> twice = patch.apply(patched, validWire);
    assertThat(twice)
        .as(
            "Patch idempotence law: applying the same wire twice equals applying it once; got %s",
            twice)
        .isEqualTo(Validated.validNel(patched));
  }

  /**
   * Patch located-validation law: an invalid projected component fails, and every accumulated error
   * is located (a non-empty path) - the tier's every-bad-field-located promise.
   */
  public static <D, W> void assertPatchValidationFails(
      BiFunction<? super D, ? super W, Validated<NonEmptyList<FieldError>, D>> patch,
      D domainSample,
      W invalidWire) {
    Validated<NonEmptyList<FieldError>, D> result = patch.apply(domainSample, invalidWire);
    assertThat(result.isInvalid())
        .as(
            "Patch validation law: an invalid projected component must fail;"
                + " patch(%s, invalidWire) was %s",
            domainSample, result)
        .isTrue();
    assertThat(result.getError().toJavaList())
        .as("Patch validation law: every accumulated error must be located (non-empty path)")
        .allSatisfy(error -> assertThat(error.path()).isNotEmpty());
  }

  /**
   * Sparse identity law: an all-absent wire folds to the identity update, so {@code
   * updateFrom(allAbsentWire).apply(domainSample) == Valid(domainSample)}.
   */
  public static <W, D> void assertSparseIdentity(
      Function<? super W, Edits.Accumulated<D>> updateFrom, D domainSample, W allAbsentWire) {
    Validated<NonEmptyList<FieldError>, D> result =
        updateFrom.apply(allAbsentWire).apply(domainSample);
    assertThat(result)
        .as(
            "Sparse identity law: updateFrom(allAbsentWire).apply(%s) == Valid(it); got %s",
            domainSample, result)
        .isEqualTo(Validated.validNel(domainSample));
  }

  /**
   * Sparse idempotence law: applying the same valid patch twice equals applying it once. Guards
   * that {@code validWire} parses and genuinely changes the domain, so the law is not vacuously
   * true.
   */
  public static <W, D> void assertSparseIdempotent(
      Function<? super W, Edits.Accumulated<D>> updateFrom, D domainSample, W validWire) {
    Validated<NonEmptyList<FieldError>, D> once = updateFrom.apply(validWire).apply(domainSample);
    assertThat(once.isValid())
        .as(
            "Sparse idempotence needs a validWire that PARSES; updateFrom(validWire).apply(%s) was"
                + " %s",
            domainSample, once)
        .isTrue();
    D patched = once.get();
    assertThat(patched)
        .as(
            "Sparse idempotence needs a validWire that CHANGES the domain; %s left %s unchanged",
            validWire, domainSample)
        .isNotEqualTo(domainSample);
    Validated<NonEmptyList<FieldError>, D> twice = updateFrom.apply(validWire).apply(patched);
    assertThat(twice)
        .as(
            "Sparse idempotence law: applying the same patch twice equals applying it once; got %s",
            twice)
        .isEqualTo(Validated.validNel(patched));
  }

  /**
   * Sparse validation law: a present but invalid field fails, so {@code
   * updateFrom(invalidWire).apply(domainSample)} is {@code Invalid}.
   */
  public static <W, D> void assertSparseValidationFails(
      Function<? super W, Edits.Accumulated<D>> updateFrom, D domainSample, W invalidWire) {
    Validated<NonEmptyList<FieldError>, D> result =
        updateFrom.apply(invalidWire).apply(domainSample);
    assertThat(result.isInvalid())
        .as(
            "Sparse validation law: a present invalid field must fail;"
                + " updateFrom(invalidWire).apply(%s) was %s",
            domainSample, result)
        .isTrue();
  }
}
