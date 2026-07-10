// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.laws;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
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
}
