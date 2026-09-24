// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/basics.html">Record Mapping Basics</a>
 * page.
 *
 * <p>The book does not paraphrase this file: it {@code {{#include}}}s the anchored regions below,
 * so the page cannot drift from the API, and {@code BookExampleOutputTest} runs {@code main} and
 * holds each output comment to what it prints.
 *
 * <p>The specs are top-level, not nested in the class: a nested spec joins its enclosing simple
 * names, so {@code Shop.CustomerMapping} would generate {@code ShopCustomerMappingImpl}, where the
 * page teaches {@code CustomerMappingImpl}.
 */
public final class BasicsBook {

  private BasicsBook() {}

  public static void main(String[] args) {
    // ANCHOR: basics_usage
    Person person = new Person("Ada", 36);
    PersonMappingImpl personMapping = PersonMappingImpl.INSTANCE; // bind once, reuse

    // Same-named, same-typed components match automatically:
    PersonDto dto = personMapping.build(person); // total
    Validated<NonEmptyList<FieldError>, Person> back =
        personMapping.parse(dto); // accumulating, located
    // ANCHOR_END: basics_usage
    System.out.println(dto + " / " + back);

    // ANCHOR: leaf_usage
    Validated<NonEmptyList<FieldError>, Customer> parsed =
        CustomerMappingImpl.INSTANCE.parse(new CustomerDto("Bob", "not-an-email"));
    // Invalid(NonEmptyList[email: not an email address])
    // ANCHOR_END: leaf_usage
    System.out.println(parsed);

    // ANCHOR: derived_usage
    ProfileDto built = ProfileMappingImpl.INSTANCE.build(new Profile("Ada", "Lovelace"));
    // ProfileDto[first=Ada, last=Lovelace, displayName=Ada Lovelace]
    // ANCHOR_END: derived_usage
    System.out.println(built);
  }
}

// ANCHOR: email_leaf
record EmailAddress(String value) {}

final class EmailCodecs {
  static final ValidatedPrism<String, EmailAddress> EMAIL =
      ValidatedPrism.of(
          raw ->
              raw.contains("@")
                  ? Validated.validNel(new EmailAddress(raw))
                  : Validated.invalidNel(FieldError.of("not an email address")),
          EmailAddress::value);

  private EmailCodecs() {}
}

// ANCHOR_END: email_leaf

// ANCHOR: basics_spec
record Person(String name, int age) {}

record PersonDto(String name, int age) {}

@GenerateMapping
interface PersonMapping extends MappingSpec<Person, PersonDto> {}

// ANCHOR_END: basics_spec

// ANCHOR: leaf_spec
record Customer(String name, EmailAddress email) {}

record CustomerDto(String name, String email) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  default ValidatedPrism<String, EmailAddress> email() { // wire first, domain second
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: leaf_spec

// ANCHOR: rename_spec
record PersonCardDto(String fullName, int age) {}

@GenerateMapping
interface PersonCardMapping extends MappingSpec<Person, PersonCardDto> {
  @MapField(to = "fullName")
  String name(); // Person.name <-> PersonCardDto.fullName
}

// ANCHOR_END: rename_spec

// ANCHOR: derived_spec
record Profile(String first, String last) {}

record ProfileDto(String first, String last, String displayName) {}

@GenerateMapping
interface ProfileMapping extends MappingSpec<Profile, ProfileDto> {
  default Getter<Profile, String> displayName() {
    return Getter.of(p -> p.first() + " " + p.last());
  }
}

// ANCHOR_END: derived_spec
