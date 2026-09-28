// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.math.BigDecimal;
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
import org.higherkindedj.optics.validated.StandardCodecs;
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

    // ANCHOR: list_usage
    Validated<NonEmptyList<FieldError>, Cart> cart =
        CartMappingImpl.INSTANCE.parse(
            new CartDto(
                "C-1",
                List.of(new LineItemDto("SKU-1", "9.99"), new LineItemDto("SKU-2", "12,50"))));
    // Invalid(NonEmptyList[lines.1.price: not a number in plain notation (expected e.g. 123.45)])
    // ANCHOR_END: list_usage
    System.out.println(cart);

    // ANCHOR: sealed_usage
    PaymentMappingImpl paymentMapping = PaymentMappingImpl.INSTANCE;

    PaymentDto bankWire = paymentMapping.build(new Bank("GB33BUKB20201555555555"));
    // BankDto[iban=GB33BUKB20201555555555]
    Validated<NonEmptyList<FieldError>, Payment> card =
        paymentMapping.parse(new CardDto("4111111111111111"));
    // Valid(Card[pan=4111111111111111])
    // ANCHOR_END: sealed_usage
    System.out.println(bankWire);
    System.out.println(card);

    // ANCHOR: bridge_nesting_usage
    ReferralMappingImpl referralMapping = ReferralMappingImpl.INSTANCE;

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
    PromotionMappingImpl promotionMapping = PromotionMappingImpl.INSTANCE;

    Validated<NonEmptyList<FieldError>, Promotion> noInvitees =
        promotionMapping.parse(new PromotionDto("Spring launch", null));
    // Valid(Promotion[name=Spring launch, invitees=Optional.empty])

    Validated<NonEmptyList<FieldError>, Promotion> badInvitee =
        promotionMapping.parse(
            new PromotionDto(
                "Spring launch",
                List.of(
                    new CustomerDto("Ada", "ada@example.org"), new CustomerDto("Bob", "nope"))));
    // Invalid(NonEmptyList[invitees.1.email: not an email address])
    // ANCHOR_END: bridge_container_usage
    System.out.println(noInvitees);
    System.out.println(badInvitee);

    // ANCHOR: flatten_usage
    PickupPointMappingImpl pickupMapping = PickupPointMappingImpl.INSTANCE;

    PickupPointDto flat =
        pickupMapping.build(
            new PickupPoint("Kirkstall", new Address("1 High Street", "Leeds", "LS1 4AP")));
    // PickupPointDto[name=Kirkstall, street=1 High Street, city=Leeds, postcode=LS1 4AP]
    Validated<NonEmptyList<FieldError>, PickupPoint> missing =
        pickupMapping.parse(new PickupPointDto("Kirkstall", null, "Leeds", null));
    // Invalid(NonEmptyList[address.street: must not be null, address.postcode: must not be null])
    // ANCHOR_END: flatten_usage
    System.out.println(flat + " / " + missing);

    // ANCHOR: widened_usage
    Validated<NonEmptyList<FieldError>, SupportDesk> desk =
        SupportDeskMappingImpl.INSTANCE.parse(
            new SupportDeskDto(
                Set.of("nope"),
                new String[] {"ada@example.org", "also-nope"},
                Map.of("bad-key", "a note")));
    // Invalid(NonEmptyList[
    //   agents.nope: not an email address,    <- a Set locates by the element itself
    //   standby.1: not an email address,      <- an array locates by index
    //   notes.bad-key: not an email address]) <- a key locates by the key it was sent as
    // ANCHOR_END: widened_usage
    System.out.println(desk);
  }
}

// ANCHOR: nesting_spec
record Invoice(String id, Customer customer) {}

record InvoiceDto(String id, CustomerDto customer) {}

@GenerateMapping
interface InvoiceMapping extends MappingSpec<Invoice, InvoiceDto> {}

// ANCHOR_END: nesting_spec

// ANCHOR: list_spec
record LineItem(String sku, BigDecimal price) {}

record LineItemDto(String sku, String price) {}

record Cart(String id, List<LineItem> lines) {}

record CartDto(String id, List<LineItemDto> lines) {}

@GenerateMapping
interface LineItemMapping extends MappingSpec<LineItem, LineItemDto> {
  default ValidatedPrism<String, BigDecimal> price() {
    return StandardCodecs.bigDecimal();
  }
}

@GenerateMapping
interface CartMapping extends MappingSpec<Cart, CartDto> {} // lines: LineItemMapping, per element

// ANCHOR_END: list_spec

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
// A promotion open to everyone comes with no invitee list, which is not an empty one: an empty
// list invites no one. So the domain keeps the difference.
record Promotion(String name, Optional<List<Customer>> invitees) {}

// A client that sends no list leaves the array out, which the JSON binder reads as null.
record PromotionDto(String name, @Nullable List<CustomerDto> invitees) {}

@GenerateMapping
interface PromotionMapping extends MappingSpec<Promotion, PromotionDto> {
  // CustomerMapping maps the elements, so the marker is all the list needs.
  @OptionalBridge
  Optional<List<Customer>> invitees();
}

// ANCHOR_END: bridge_container_spec

// ANCHOR: flatten_spec
// A courier's pickup point, whose API lists its address as plain fields.
record PickupPoint(String name, Address address) {} // Address as on Record Mapping Basics

record PickupPointDto(String name, String street, String city, String postcode) {} // flat

@GenerateMapping
interface PickupPointMapping extends MappingSpec<PickupPoint, PickupPointDto> {
  @Flatten
  Address address(); // spread by name: street, city and postcode
}

// ANCHOR_END: flatten_spec

// ANCHOR: widened_spec
// The support desk: its agents, those on standby in turn, and a note per agent.
record SupportDesk(
    Set<EmailAddress> agents, // a Set lifts like a List
    EmailAddress[] standby, // so does an array
    Map<EmailAddress, String> notes) {} // and a Map's KEYS, with @MapKey

record SupportDeskDto(Set<String> agents, String[] standby, Map<String, String> notes) {}

@GenerateMapping
interface SupportDeskMapping extends MappingSpec<SupportDesk, SupportDeskDto> {
  default ValidatedPrism<String, EmailAddress> agents() {
    return EmailCodecs.EMAIL;
  }

  default ValidatedPrism<String, EmailAddress> standby() {
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

// Jackson binds the wire before parse runs, so the wire says how to tell its subtypes apart.
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION) // by their fields: pan or iban
@JsonSubTypes({@JsonSubTypes.Type(CardDto.class), @JsonSubTypes.Type(BankDto.class)})
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

// ANCHOR: empty_subtype_spec
sealed interface Fulfilment permits Shipped, Collected {}

record Shipped(String tracking) implements Fulfilment {}

record Collected() implements Fulfilment {} // collected in store: nothing more to say

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION) // {} is the subtype with no properties
@JsonSubTypes({@JsonSubTypes.Type(ShippedDto.class), @JsonSubTypes.Type(CollectedDto.class)})
sealed interface FulfilmentDto permits ShippedDto, CollectedDto {}

record ShippedDto(String tracking) implements FulfilmentDto {}

record CollectedDto() implements FulfilmentDto {}

@GenerateMapping
interface ShippedMapping extends MappingSpec<Shipped, ShippedDto> {}

@GenerateMapping
interface CollectedMapping extends MappingSpec<Collected, CollectedDto> {}

@GenerateMapping
interface FulfilmentMapping extends MappingSpec<Fulfilment, FulfilmentDto> {}

// ANCHOR_END: empty_subtype_spec

// ANCHOR: checkout_spec
record Checkout(String id, List<Payment> payments) {}

record CheckoutDto(String id, List<PaymentDto> payments) {}

@GenerateMapping
interface CheckoutMapping extends MappingSpec<Checkout, CheckoutDto> {}

// ANCHOR_END: checkout_spec

// ANCHOR: order_note_spec
record OrderNote(String text, List<String> tags) {}

record OrderNoteDto(String text, List<String> tags) {}

@GenerateMapping
interface OrderNoteMapping extends MappingSpec<OrderNote, OrderNoteDto> {}

// ANCHOR_END: order_note_spec
