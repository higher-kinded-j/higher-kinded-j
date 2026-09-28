// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import java.util.List;
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
    Address address = new Address("1 High Street", "Leeds", "LS1 4AP");
    AddressMappingImpl addressMapping = AddressMappingImpl.INSTANCE; // bind once, reuse

    // Same-named, same-typed components match automatically:
    AddressDto dto = addressMapping.build(address); // total
    Validated<NonEmptyList<FieldError>, Address> back =
        addressMapping.parse(dto); // accumulating, located
    // ANCHOR_END: basics_usage
    System.out.println(dto + " / " + back);

    // ANCHOR: leaf_usage
    Validated<NonEmptyList<FieldError>, Customer> parsed =
        CustomerMappingImpl.INSTANCE.parse(new CustomerDto("Bob", "not-an-email"));
    // Invalid(NonEmptyList[email: not an email address])
    // ANCHOR_END: leaf_usage
    System.out.println(parsed);

    // ANCHOR: fold_usage
    // One function for every error, one for the value:
    List<String> report =
        parsed.fold(
            errors -> errors.map(e -> e.pathString() + " -> " + e.message()).toJavaList(),
            customer -> List.of());
    // [email -> not an email address]
    // ANCHOR_END: fold_usage
    System.out.println(report);

    // ANCHOR: null_usage
    Validated<NonEmptyList<FieldError>, Customer> missing =
        CustomerMappingImpl.INSTANCE.parse(new CustomerDto(null, "not-an-email"));
    // Invalid(NonEmptyList[name: must not be null, email: not an email address])
    // ANCHOR_END: null_usage
    System.out.println(missing);

    // ANCHOR: derived_usage
    RecipientDto built = RecipientMappingImpl.INSTANCE.build(new Recipient("Ada", "Lovelace"));
    // RecipientDto[first=Ada, last=Lovelace, displayName=Ada Lovelace]
    // ANCHOR_END: derived_usage
    System.out.println(built);

    // ANCHOR: rename_leaf_usage
    Validated<NonEmptyList<FieldError>, Customer> subscribed =
        MailingListCustomerMappingImpl.INSTANCE.parse(
            new MailingListCustomerDto("Ada", "not-an-email"));
    // Invalid(NonEmptyList[email: not an email address])
    // ANCHOR_END: rename_leaf_usage
    System.out.println(subscribed);
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
record Address(String street, String city, String postcode) {}

record AddressDto(String street, String city, String postcode) {}

@GenerateMapping
interface AddressMapping extends MappingSpec<Address, AddressDto> {}

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
record PartnerCustomerDto(String fullName, String email) {} // a partner's customer feed

@GenerateMapping
interface PartnerCustomerMapping extends MappingSpec<Customer, PartnerCustomerDto> {
  @MapField(to = "fullName")
  String name(); // Customer.name <-> PartnerCustomerDto.fullName

  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: rename_spec

// ANCHOR: rename_leaf_spec
record MailingListCustomerDto(String name, String emailAddress) {} // a mailing list's export

@GenerateMapping
interface MailingListCustomerMapping extends MappingSpec<Customer, MailingListCustomerDto> {
  @MapField(to = "emailAddress") // Customer.email <-> MailingListCustomerDto.emailAddress
  default ValidatedPrism<String, EmailAddress> email() { // renamed, and converted
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: rename_leaf_spec

// ANCHOR: derived_spec
record Recipient(String first, String last) {}

record RecipientDto(String first, String last, String displayName) {}

@GenerateMapping
interface RecipientMapping extends MappingSpec<Recipient, RecipientDto> {
  default Getter<Recipient, String> displayName() {
    return Getter.of(r -> r.first() + " " + r.last());
  }
}

// ANCHOR_END: derived_spec

// ANCHOR: mapper_constants
// The MapStruct idiom, on two specs: never do this.
record Warehouse(String code, int bays) {}

record WarehouseDto(String code, int bays) {}

@GenerateMapping
interface WarehouseMapping extends MappingSpec<Warehouse, WarehouseDto> {
  WarehouseMappingImpl MAPPER = WarehouseMappingImpl.INSTANCE;
}

record Courier(String name, EmailAddress email) {}

record CourierDto(String name, String email) {}

@GenerateMapping
interface CourierMapping extends MappingSpec<Courier, CourierDto> {
  CourierMappingImpl MAPPER = CourierMappingImpl.INSTANCE;

  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: mapper_constants
