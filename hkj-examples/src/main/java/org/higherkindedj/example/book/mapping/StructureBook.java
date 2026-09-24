// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.Flatten;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MapKey;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.annotations.OptionalBridge;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.jspecify.annotations.Nullable;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/structure.html">Nesting, Containers, and
 * Sealed Hierarchies</a> page.
 *
 * <p>The book does not paraphrase this file: it {@code {{#include}}}s the anchored regions below,
 * so the page cannot drift from the API, and {@code BookExampleOutputTest} runs {@code main} and
 * holds each output comment to what it prints.
 *
 * <p>The specs are top-level, not nested in the class: a nested spec joins its enclosing simple
 * names, so {@code Shop.CustomerMapping} would generate {@code ShopCustomerMappingImpl}, where the
 * page teaches {@code CustomerMappingImpl}.
 *
 * <p>Types several pages share, such as {@code Customer} and {@code EmailAddress}, are declared in
 * {@link BasicsBook}, beside the page that introduces them.
 */
public final class StructureBook {

  private StructureBook() {}

  public static void main(String[] args) {
    // ANCHOR: nesting_usage
    Validated<NonEmptyList<FieldError>, Invoice> invoice =
        InvoiceMappingImpl.INSTANCE.parse(new InvoiceDto("INV-2", new CustomerDto("Bob", "nope")));
    // Invalid(NonEmptyList[customer.email: not an email address])
    // ANCHOR_END: nesting_usage
    System.out.println(invoice);

    // ANCHOR: bridge_nesting_usage
    var referralMapping = ReferralMappingImpl.INSTANCE;

    Validated<NonEmptyList<FieldError>, Referral> noReferrer =
        referralMapping.parse(new ReferralDto("R-7", null));
    // Valid(Referral[code=R-7, referrer=Optional.empty])

    Validated<NonEmptyList<FieldError>, Referral> badReferrer =
        referralMapping.parse(new ReferralDto("R-7", new CustomerDto("Bob", "nope")));
    // Invalid(NonEmptyList[referrer.email: not an email address])
    // ANCHOR_END: bridge_nesting_usage
    System.out.println(noReferrer);
    System.out.println(badReferrer);

    // ANCHOR: bridge_container_usage
    var guestlistMapping = GuestlistMappingImpl.INSTANCE;

    Validated<NonEmptyList<FieldError>, Guestlist> absentGuests =
        guestlistMapping.parse(new GuestlistDto("Launch", null));
    // Valid(Guestlist[event=Launch, guests=Optional.empty])

    Validated<NonEmptyList<FieldError>, Guestlist> badGuest =
        guestlistMapping.parse(
            new GuestlistDto(
                "Launch",
                List.of(
                    new CustomerDto("Ada", "ada@example.org"), new CustomerDto("Bob", "nope"))));
    // Invalid(NonEmptyList[guests.1.email: not an email address])
    // ANCHOR_END: bridge_container_usage
    System.out.println(absentGuests);
    System.out.println(badGuest);

    // ANCHOR: flatten_usage
    var vendorMapping = VendorMappingImpl.INSTANCE;

    VendorDto flat =
        vendorMapping.build(new Vendor("Acme", new Address("1 High St", "Leeds", "LS1 4AP")));
    // VendorDto[name=Acme, street=1 High St, city=Leeds, postcode=LS1 4AP]
    Validated<NonEmptyList<FieldError>, Vendor> missing =
        vendorMapping.parse(new VendorDto("Acme", null, "Leeds", null));
    // Invalid(NonEmptyList[address.street: must not be null, address.postcode: must not be null])
    // ANCHOR_END: flatten_usage
    System.out.println(flat + " / " + missing);

    // ANCHOR: widened_usage
    Validated<NonEmptyList<FieldError>, Crew> crew =
        CrewMappingImpl.INSTANCE.parse(
            new CrewDto(
                Set.of("nope"),
                new String[] {"ada@example.org", "also-nope"},
                Map.of("bad-key", "a note")));
    // Invalid(NonEmptyList[
    //   members.nope: not an email address,   <- a Set locates by the element itself
    //   reserves.1: not an email address,     <- an array locates by index
    //   notes.bad-key: not an email address]) <- a key locates by the key it was sent as
    // ANCHOR_END: widened_usage
    System.out.println(crew);
  }
}

// ANCHOR: nesting_spec
record Invoice(String id, Customer customer) {}

record InvoiceDto(String id, CustomerDto customer) {}

@GenerateMapping
interface InvoiceMapping extends MappingSpec<Invoice, InvoiceDto> {}

// ANCHOR_END: nesting_spec

// ANCHOR: bridge_nesting_spec
record Referral(String code, Optional<Customer> referrer) {}

// A client with no referrer leaves the object out, which the JSON binder reads as null.
record ReferralDto(String code, @Nullable CustomerDto referrer) {}

@GenerateMapping
interface ReferralMapping extends MappingSpec<Referral, ReferralDto> {
  // CustomerMapping maps the element pair, so the marker is all this component needs.
  @OptionalBridge
  Optional<Customer> referrer();
}

// ANCHOR_END: bridge_nesting_spec

// ANCHOR: bridge_container_spec
// An absent guest list is not an empty one, so the domain keeps the difference.
record Guestlist(String event, Optional<List<Customer>> guests) {}

// A client that sends no list leaves the array out, which the JSON binder reads as null.
record GuestlistDto(String event, @Nullable List<CustomerDto> guests) {}

@GenerateMapping
interface GuestlistMapping extends MappingSpec<Guestlist, GuestlistDto> {
  // CustomerMapping maps the elements, so the marker is all the list needs.
  @OptionalBridge
  Optional<List<Customer>> guests();
}

// ANCHOR_END: bridge_container_spec

// ANCHOR: flatten_spec
record Address(String street, String city, String postcode) {}

record Vendor(String name, Address address) {}

record VendorDto(String name, String street, String city, String postcode) {} // fixed, flat

@GenerateMapping
interface VendorMapping extends MappingSpec<Vendor, VendorDto> {
  @Flatten
  Address address(); // spread by name: street, city and postcode
}

// ANCHOR_END: flatten_spec

// ANCHOR: widened_spec
record Crew(
    Set<EmailAddress> members, // a Set lifts like a List
    EmailAddress[] reserves, // so does an array
    Map<EmailAddress, String> notes) {} // and a Map's KEYS, with @MapKey

record CrewDto(Set<String> members, String[] reserves, Map<String, String> notes) {}

@GenerateMapping
interface CrewMapping extends MappingSpec<Crew, CrewDto> {
  default ValidatedPrism<String, EmailAddress> members() {
    return EmailCodecs.EMAIL;
  }

  default ValidatedPrism<String, EmailAddress> reserves() {
    return EmailCodecs.EMAIL;
  }

  // A value leaf is named after its component; a key leaf is named BY its annotation,
  // because the two cannot share the one name Java allows.
  @MapKey("notes")
  default ValidatedPrism<String, EmailAddress> noteKey() {
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: widened_spec

// ANCHOR: sealed_spec
sealed interface Payment permits Card, Bank {}

record Card(String pan) implements Payment {}

record Bank(String iban) implements Payment {}

sealed interface PaymentDto permits CardDto, BankDto {}

record CardDto(String pan) implements PaymentDto {}

record BankDto(String iban) implements PaymentDto {}

@GenerateMapping
interface CardMapping extends MappingSpec<Card, CardDto> {}

@GenerateMapping
interface BankMapping extends MappingSpec<Bank, BankDto> {}

@GenerateMapping
interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {}

// ANCHOR_END: sealed_spec
