// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.laws;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Setter;
import org.higherkindedj.optics.edit.Edit;
import org.higherkindedj.optics.edit.Edits;
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

  // Sparse-update tier: a hand-written updateFrom over an owner (identity) and a validated contact
  // (leaf), mirroring what an UpdateSpec Impl emits (issue #645).
  record Account(String owner, EmailAddress contact) {}

  record AccountPatch(String owner, String contact) {}

  private static final Setter<Account, String> ACCOUNT_OWNER =
      Setter.fromGetSet(Account::owner, (a, v) -> new Account(v, a.contact()));

  private static final Setter<Account, EmailAddress> ACCOUNT_CONTACT =
      Setter.fromGetSet(Account::contact, (a, v) -> new Account(a.owner(), v));

  private static final Account ACCOUNT = new Account("Ada", new EmailAddress("ada@example.org"));

  // Lawful: absent -> no-op, present owner -> set, present contact -> parsed and located.
  private static final Function<AccountPatch, Edits.Accumulated<Account>> LAWFUL_UPDATE =
      p ->
          Edits.accumulate(
              Edit.setIfPresent(ACCOUNT_OWNER, p.owner()),
              Edit.parseIfPresent(ACCOUNT_CONTACT, p.contact(), EMAIL::parse).at("contact"));

  private static final AccountPatch ABSENT_PATCH = new AccountPatch(null, null);
  private static final AccountPatch VALID_PATCH = new AccountPatch("Grace", "grace@example.org");
  private static final AccountPatch INVALID_PATCH = new AccountPatch(null, "not-an-email");

  // Validated-patch tier (#625): a hand-written DENSE patch over an owner (identity, null-guarded)
  // and a validated contact (leaf), mirroring what a leaf-carrying projection Impl emits: every
  // reference read null-guards into a located error, and every leg is labelled.
  private static final BiFunction<
          Account, AccountPatch, Validated<NonEmptyList<FieldError>, Account>>
      LAWFUL_DENSE_PATCH =
          (account, patch) ->
              Validated.fields()
                  .field(
                      "owner",
                      patch.owner() == null
                          ? Validated.invalidNel(FieldError.of("must not be null"))
                          : Validated.validNel(patch.owner()))
                  .field(
                      "contact",
                      patch.contact() == null
                          ? Validated.invalidNel(FieldError.of("must not be null"))
                          : EMAIL.parse(patch.contact()))
                  .apply(Account::new);

  private static final Function<Account, AccountPatch> DENSE_BUILD =
      account -> new AccountPatch(account.owner(), account.contact().value());

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
    @DisplayName("the sparse-update tier passes identity, idempotence and validation")
    void sparseUpdateTierPasses() {
      MappingLaws.assertMappingLaws(
          LAWFUL_UPDATE, ACCOUNT, ABSENT_PATCH, VALID_PATCH, INVALID_PATCH);
    }

    @Test
    @DisplayName("the validated-patch tier passes identity, idempotence and located validation")
    void patchTierPasses() {
      MappingLaws.assertMappingLaws(
          LAWFUL_DENSE_PATCH, DENSE_BUILD, ACCOUNT, VALID_PATCH, INVALID_PATCH);
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

    @Test
    @DisplayName("a sparse update that ignores the absent contract fails the identity law")
    void sparseNonIdentityFails() {
      // Always sets owner, even for an all-absent wire, so the domain never survives untouched.
      Function<AccountPatch, Edits.Accumulated<Account>> alwaysSets =
          p -> Edits.accumulate(Edit.set(ACCOUNT_OWNER, "CONSTANT"));

      assertThatThrownBy(() -> MappingLaws.assertSparseIdentity(alwaysSets, ACCOUNT, ABSENT_PATCH))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("identity law");
    }

    @Test
    @DisplayName("a modify-shaped sparse update fails the idempotence law")
    void sparseNonIdempotentFails() {
      // Appends the present owner to the current one, so applying twice differs from applying once.
      Function<AccountPatch, Edits.Accumulated<Account>> appending =
          p ->
              Edits.accumulate(Edit.modifyIfPresent(ACCOUNT_OWNER, p.owner(), (v, cur) -> cur + v));

      assertThatThrownBy(() -> MappingLaws.assertSparseIdempotent(appending, ACCOUNT, VALID_PATCH))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("idempotence law");
    }

    @Test
    @DisplayName("a sparse update that ignores a present invalid field fails the validation law")
    void sparseValidationDoesNotFail() {
      // Never parses contact, so an invalid contact still yields a Valid result.
      Function<AccountPatch, Edits.Accumulated<Account>> ignoresContact =
          p -> Edits.accumulate(Edit.setIfPresent(ACCOUNT_OWNER, p.owner()));

      assertThatThrownBy(
              () -> MappingLaws.assertSparseValidationFails(ignoresContact, ACCOUNT, INVALID_PATCH))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("must fail");
    }

    @Test
    @DisplayName("a patch that rewrites its own projection fails the identity law")
    void patchNonIdentityFails() {
      // Uppercases the owner on the way back in, so the domain's own projection round-trips
      // changed.
      BiFunction<Account, AccountPatch, Validated<NonEmptyList<FieldError>, Account>> shouting =
          (account, patch) ->
              LAWFUL_DENSE_PATCH.apply(
                  account,
                  new AccountPatch(
                      patch.owner() == null ? null : patch.owner().toUpperCase(Locale.ROOT),
                      patch.contact()));

      assertThatThrownBy(() -> MappingLaws.assertPatchIdentity(shouting, DENSE_BUILD, ACCOUNT))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("Patch identity law");
    }

    @Test
    @DisplayName("a modify-shaped patch fails the idempotence law")
    void patchNonIdempotentFails() {
      // Appends the wire owner to the current one, so applying twice differs from applying once.
      BiFunction<Account, AccountPatch, Validated<NonEmptyList<FieldError>, Account>> appending =
          (account, patch) ->
              Validated.fields()
                  .field("owner", Validated.validNel(account.owner() + patch.owner()))
                  .field(
                      "contact",
                      patch.contact() == null
                          ? Validated.invalidNel(FieldError.of("must not be null"))
                          : EMAIL.parse(patch.contact()))
                  .apply(Account::new);

      assertThatThrownBy(() -> MappingLaws.assertPatchIdempotent(appending, ACCOUNT, VALID_PATCH))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("idempotence law");
    }

    @Test
    @DisplayName("a patch that ignores an invalid projected component fails the validation law")
    void patchValidationDoesNotFail() {
      BiFunction<Account, AccountPatch, Validated<NonEmptyList<FieldError>, Account>> ignores =
          (account, patch) -> Validated.validNel(account);

      assertThatThrownBy(
              () -> MappingLaws.assertPatchValidationFails(ignores, ACCOUNT, INVALID_PATCH))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("must fail");
    }

    @Test
    @DisplayName("a patch whose failures carry no location fails the located-validation law")
    void patchUnlocatedErrorsFail() {
      BiFunction<Account, AccountPatch, Validated<NonEmptyList<FieldError>, Account>> unlocated =
          (account, patch) -> Validated.invalidNel(FieldError.of("bad, but where?"));

      assertThatThrownBy(
              () -> MappingLaws.assertPatchValidationFails(unlocated, ACCOUNT, INVALID_PATCH))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("located");
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

    @Test
    @DisplayName("the sparse tier rejects a validWire that does not change the domain")
    void sparseTierRejectsVacuousValidWire() {
      // A patch equal to the current values changes nothing, making idempotence vacuously true.
      AccountPatch vacuous = new AccountPatch("Ada", "ada@example.org");

      assertThatThrownBy(() -> MappingLaws.assertSparseIdempotent(LAWFUL_UPDATE, ACCOUNT, vacuous))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("CHANGES");
    }

    @Test
    @DisplayName("the sparse tier rejects a validWire that does not parse")
    void sparseTierRejectsNonParsingValidWire() {
      assertThatThrownBy(
              () -> MappingLaws.assertSparseIdempotent(LAWFUL_UPDATE, ACCOUNT, INVALID_PATCH))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("PARSES");
    }

    @Test
    @DisplayName("the patch tier rejects a validWire that does not parse")
    void patchTierRejectsNonParsingValidWire() {
      assertThatThrownBy(
              () -> MappingLaws.assertPatchIdempotent(LAWFUL_DENSE_PATCH, ACCOUNT, INVALID_PATCH))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("PARSES");
    }

    @Test
    @DisplayName("the patch tier rejects a validWire that does not change the domain")
    void patchTierRejectsVacuousValidWire() {
      // The domain's own projection changes nothing, making idempotence vacuously true.
      assertThatThrownBy(
              () ->
                  MappingLaws.assertPatchIdempotent(
                      LAWFUL_DENSE_PATCH, ACCOUNT, DENSE_BUILD.apply(ACCOUNT)))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("CHANGES");
    }
  }
}
