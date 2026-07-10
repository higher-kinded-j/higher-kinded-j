// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.laws;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Mapping-law helpers - every emission tier, pass and fail paths")
class MappingLawsTest {

  // Lossless tier: a rename-only mapping, so asIso() and parse/build are all total.
  record Person(String name, int age) {}

  record PersonDto(String fullName, int age) {}

  private static final Iso<Person, PersonDto> LOSSLESS_ISO =
      Iso.of(p -> new PersonDto(p.name(), p.age()), w -> new Person(w.fullName(), w.age()));

  private static final ValidatedPrism<PersonDto, Person> LOSSLESS_MAPPING =
      ValidatedPrism.of(
          w -> Validated.validNel(new Person(w.fullName(), w.age())),
          p -> new PersonDto(p.name(), p.age()));

  private static final Person ADA = new Person("Ada", 36);
  private static final PersonDto GRACE_DTO = new PersonDto("Grace", 41);

  // Projection tier: the wire drops age; set writes name and department back.
  record Employee(String name, String department, int age) {}

  record EmployeeCardDto(String name, String department) {}

  private static final Lens<Employee, EmployeeCardDto> PROJECTION =
      Lens.of(
          e -> new EmployeeCardDto(e.name(), e.department()),
          (e, w) -> new Employee(w.name(), w.department(), e.age()));

  private static final Employee EMPLOYEE = new Employee("Ada", "Platform", 36);

  // Fallible tier: a validated leaf that rejects non-addresses.
  record EmailAddress(String value) {}

  private static final ValidatedPrism<String, EmailAddress> EMAIL =
      ValidatedPrism.of(
          raw ->
              raw.contains("@")
                  ? Validated.validNel(new EmailAddress(raw))
                  : Validated.invalidNel(FieldError.of("not an email address")),
          EmailAddress::value);

  // Derived-field shape: build recomputes displayName, parse ignores it, so parse is total.
  record FullName(String first, String last) {}

  record FullNameDto(String first, String last, String displayName) {}

  private static final ValidatedPrism<FullNameDto, FullName> DERIVED_MAPPING =
      ValidatedPrism.of(
          w -> Validated.validNel(new FullName(w.first(), w.last())),
          d -> new FullNameDto(d.first(), d.last(), d.first() + " " + d.last()));

  @Nested
  @DisplayName("Lawful mappings pass every tier's laws")
  class LawfulMappings {

    @Test
    @DisplayName("the lossless tier passes iso laws, round trips and both coherence checks")
    void losslessTierPasses() {
      MappingLaws.assertMappingLaws(LOSSLESS_ISO, LOSSLESS_MAPPING, ADA, GRACE_DTO);
    }

    @Test
    @DisplayName("the projection tier passes the three lens laws")
    void projectionTierPasses() {
      MappingLaws.assertMappingLaws(
          PROJECTION,
          EMPLOYEE,
          new EmployeeCardDto("Grace", "Compilers"),
          new EmployeeCardDto("Alan", "Computing"));
    }

    @Test
    @DisplayName("the fallible tier passes both round trips and the no-parse check")
    void fallibleTierPasses() {
      MappingLaws.assertMappingLaws(EMAIL, "ada@example.org", "not-an-email");
    }

    @Test
    @DisplayName("a total-parse mapping with a derived field round-trips its domain sample")
    void totalParseTierPasses() {
      MappingLaws.assertMappingLaws(DERIVED_MAPPING, new FullName("Ada", "Lovelace"));
    }

    @Test
    @DisplayName(
        "derived reality: an inconsistent derived component parses but rebuilds normalised")
    void derivedComponentIsRecomputedNotRoundTripped() {
      FullNameDto inconsistent = new FullNameDto("Ada", "Lovelace", "nonsense");

      // parse ignores the derived component entirely...
      ValidatedPrismLaws.assertParseBuild(DERIVED_MAPPING, new FullName("Ada", "Lovelace"));
      // ...so the section law holds only for consistent wire values; asserting it on an
      // inconsistent one fails by design, and nothing in MappingLaws claims otherwise.
      assertThatThrownBy(() -> ValidatedPrismLaws.assertBuildParse(DERIVED_MAPPING, inconsistent))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("build-parse");
    }
  }

  @Nested
  @DisplayName("Incoherent surfaces fail with the check named in the message")
  class IncoherentSurfaces {

    @Test
    @DisplayName("a build disagreeing with asIso() fails build-iso coherence")
    void buildDisagreeingWithIsoFails() {
      ValidatedPrism<PersonDto, Person> shouting =
          ValidatedPrism.of(
              w -> Validated.validNel(new Person(w.fullName(), w.age())),
              p -> new PersonDto(p.name().toUpperCase(Locale.ROOT), p.age()));

      assertThatThrownBy(() -> MappingLaws.assertBuildAgreesWithIso(LOSSLESS_ISO, shouting, ADA))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("build-iso")
          .hasMessageContaining("ADA");
    }

    @Test
    @DisplayName("a parse disagreeing with asIso() fails parse-iso coherence")
    void parseDisagreeingWithIsoFails() {
      ValidatedPrism<PersonDto, Person> trimming =
          ValidatedPrism.of(
              w -> Validated.validNel(new Person(w.fullName().trim(), w.age())),
              p -> new PersonDto(p.name(), p.age()));

      assertThatThrownBy(
              () ->
                  MappingLaws.assertParseAgreesWithIso(
                      LOSSLESS_ISO, trimming, new PersonDto("  Grace  ", 41)))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("parse-iso");
    }

    @Test
    @DisplayName("a lossy total-parse mapping fails the parse-build round trip")
    void lossyTotalParseMappingFails() {
      ValidatedPrism<FullNameDto, FullName> lowering =
          ValidatedPrism.of(
              w -> Validated.validNel(new FullName(w.first().toLowerCase(Locale.ROOT), w.last())),
              d -> new FullNameDto(d.first(), d.last(), d.first() + " " + d.last()));

      assertThatThrownBy(
              () -> MappingLaws.assertMappingLaws(lowering, new FullName("Ada", "Lovelace")))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("parse-build");
    }
  }

  @Nested
  @DisplayName("Guard rails reject vacuous fixtures")
  class GuardRails {

    @Test
    @DisplayName("the lossless tier rejects a wire sample that is just the built domain sample")
    void losslessTierRejectsDependentWireSample() {
      assertThatThrownBy(
              () ->
                  MappingLaws.assertMappingLaws(
                      LOSSLESS_ISO, LOSSLESS_MAPPING, ADA, LOSSLESS_ISO.get(ADA)))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("INDEPENDENT")
          .hasMessageContaining("asIso().get(domainSample)");
    }

    @Test
    @DisplayName("the lossless tier rejects a domain sample that is just the parsed wire sample")
    void losslessTierRejectsDependentDomainSample() {
      // The mirror of the guard above: independence must hold in both directions. On a lawful
      // iso the two guards coincide (the forward one fires first), so the mirror matters exactly
      // when the iso is broken: a skewed get slips past the forward guard, and only the
      // reverseGet direction exposes the manufactured domain sample.
      Iso<Person, PersonDto> skewed =
          Iso.of(
              p -> new PersonDto(p.name().toUpperCase(Locale.ROOT), p.age()),
              w -> new Person(w.fullName(), w.age()));

      assertThatThrownBy(
              () ->
                  MappingLaws.assertMappingLaws(
                      skewed, LOSSLESS_MAPPING, skewed.reverseGet(GRACE_DTO), GRACE_DTO))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("INDEPENDENT")
          .hasMessageContaining("asIso().reverseGet(wireSample)");
    }

    @Test
    @DisplayName("the projection tier inherits the lens distinct-values guard")
    void projectionTierInheritsLensGuard() {
      EmployeeCardDto same = new EmployeeCardDto("Grace", "Compilers");

      assertThatThrownBy(() -> MappingLaws.assertMappingLaws(PROJECTION, EMPLOYEE, same, same))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("DISTINCT");
    }

    @Test
    @DisplayName("the fallible tier inherits the validated-prism guards")
    void fallibleTierInheritsValidatedPrismGuards() {
      assertThatThrownBy(() -> MappingLaws.assertMappingLaws(EMAIL, "nope", "nope"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("DISTINCT");
      assertThatThrownBy(() -> MappingLaws.assertMappingLaws(EMAIL, "nope", "also-nope"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("PARSING");
    }
  }
}
