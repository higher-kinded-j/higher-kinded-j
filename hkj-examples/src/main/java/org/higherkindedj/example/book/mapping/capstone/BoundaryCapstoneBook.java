// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping.capstone;

import static org.higherkindedj.optics.validated.StandardCodecs.bigDecimal;
import static org.higherkindedj.optics.validated.StandardCodecs.enumByName;
import static org.higherkindedj.optics.validated.StandardCodecs.instant;
import static org.higherkindedj.optics.validated.StandardCodecs.uuid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.GenerateMerge;
import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.annotations.UpdateSpec;
import org.higherkindedj.optics.validated.StandardCodecs;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * The order-intake boundary shown on the book's <a
 * href="https://higher-kinded-j.github.io/mapping/capstone.html">capstone page</a>. The page {@code
 * {{#include}}}s the anchored regions below, so it cannot drift from the API, and {@code
 * BoundaryCapstoneBookLawsTest} proves both the located five-error payoff and the mapping laws.
 */
public final class BoundaryCapstoneBook {

  private BoundaryCapstoneBook() {}

  // ANCHOR: capstone_before
  /** The hand-written version this chapter replaces: throws on the first problem, unlocated. */
  static Order toDomainByHand(OrderDto dto) {
    Objects.requireNonNull(dto.customer(), "customer required"); // first null wins
    if (!dto.customer().email().contains("@")) {
      throw new IllegalArgumentException("bad email"); // no field name, no path
    }
    List<LineItem> lines = new ArrayList<>();
    for (LineItemDto line : dto.lines()) {
      lines.add(new LineItem(line.sku(), line.quantity(), new BigDecimal(line.price())));
    }
    return new Order(
        UUID.fromString(dto.id()), // throws its own exception
        new Customer(dto.customer().fullName(), new EmailAddress(dto.customer().email())),
        List.copyOf(lines),
        Instant.parse(dto.placedAt()), // and so does this one
        Currency.getInstance(dto.currency()), // and this one
        OrderStatus.valueOf(dto.status())); // and this one
  }

  // ANCHOR_END: capstone_before

  public static void main(String[] args) {
    // ANCHOR: capstone_build
    Order order =
        new Order(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            new Customer("Ada Lovelace", new EmailAddress("ada@corp.example")),
            List.of(new LineItem("SKU-1", 2, new BigDecimal("9.99"))),
            Instant.parse("2026-07-28T12:34:56Z"),
            Currency.getInstance("GBP"),
            OrderStatus.PAID);

    OrderDto outbound = OrderMappingImpl.INSTANCE.build(order); // total: cannot fail
    // OrderDto[id=123e4567-..., customer=CustomerDto[fullName=Ada Lovelace, ...],
    //          placedAt=2026-07-28T12:34:56Z, currency=GBP, status=PAID,
    //          displayTotal=GBP 19.98]   <- computed by the derived getter
    // ANCHOR_END: capstone_build
    System.out.println(outbound);

    // ANCHOR: capstone_payoff
    OrderDto hostile =
        new OrderDto(
            "NOPE", // not a UUID
            new CustomerDto("Ada Lovelace", "not-an-email"), // fails the email leaf
            List.of(new LineItemDto("SKU-1", 2, "9.99"), new LineItemDto("SKU-2", 1, "1E+3")),
            "28/07/2026", // not an ISO-8601 instant
            "GBP",
            "DISPATCHED", // not a permitted OrderStatus
            null); // derived: parse ignores it

    Validated<NonEmptyList<FieldError>, Order> parsed = OrderMappingImpl.INSTANCE.parse(hostile);
    // Invalid(NonEmptyList[
    //   id: not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000),
    //   customer.email: not an email address,
    //   lines.1.price: not a number in plain notation (expected e.g. 123.45),
    //   placedAt: not an ISO-8601 instant (expected e.g. 2026-07-28T12:34:56Z),
    //   status: unknown OrderStatus (expected one of NEW, PAID, SHIPPED)])
    // ANCHOR_END: capstone_payoff
    System.out.println(parsed);

    // ANCHOR: capstone_patch_usage
    Customer current = new Customer("Ada Lovelace", new EmailAddress("ada@corp.example"));

    CustomerPatchBean patchBody = new CustomerPatchBean();
    patchBody.setEmail("countess@lovelace.example"); // name omitted: null, keep the current one

    Validated<NonEmptyList<FieldError>, Customer> patched =
        CustomerPatchMappingImpl.INSTANCE.updateFrom(patchBody).apply(current);
    // Valid(Customer[name=Ada Lovelace, email=EmailAddress[value=countess@lovelace.example]])
    // ANCHOR_END: capstone_patch_usage
    System.out.println(patched);

    // ANCHOR: capstone_merge_usage
    Receipt receipt = ReceiptAssemblyImpl.INSTANCE.assemble(order, order.customer());
    // Receipt[id=123e4567-..., name=Ada Lovelace, email=..., placedAt=2026-07-28T12:34:56Z]
    // ANCHOR_END: capstone_merge_usage
    System.out.println(receipt);
  }
}

// ANCHOR: capstone_domain
enum OrderStatus {
  NEW,
  PAID,
  SHIPPED
}

record EmailAddress(String value) {}

record Customer(String name, EmailAddress email) {}

record LineItem(String sku, int quantity, BigDecimal price) {}

record Order(
    UUID id,
    Customer customer,
    List<LineItem> lines,
    Instant placedAt,
    Currency currency,
    OrderStatus status) {}

// ANCHOR_END: capstone_domain

// ANCHOR: capstone_wire
record CustomerDto(String fullName, String email) {}

record LineItemDto(String sku, int quantity, String price) {}

record OrderDto(
    String id,
    CustomerDto customer,
    List<LineItemDto> lines,
    String placedAt,
    String currency,
    String status,
    String displayTotal) {} // no domain counterpart: derived on build, ignored on parse

// ANCHOR_END: capstone_wire

// ANCHOR: capstone_vocab
/** The one hand-written leaf; everything else comes from StandardCodecs. */
final class Codecs {
  static final ValidatedPrism<String, EmailAddress> EMAIL =
      ValidatedPrism.of(
          raw ->
              raw.contains("@")
                  ? Validated.validNel(new EmailAddress(raw))
                  : Validated.invalidNel(FieldError.of("not an email address")),
          EmailAddress::value);

  private Codecs() {}
}

/** Shared vocabulary: the same email leaf serves the full mapping and its PATCH sibling. */
interface OrderVocabulary {
  default ValidatedPrism<String, EmailAddress> email() {
    return Codecs.EMAIL;
  }
}

// ANCHOR_END: capstone_vocab

// ANCHOR: capstone_spec
@GenerateMapping
interface CustomerMapping extends OrderVocabulary, MappingSpec<Customer, CustomerDto> {
  @MapField(to = "fullName")
  String name(); // the wire spells it differently
}

@GenerateMapping
interface LineItemMapping extends MappingSpec<LineItem, LineItemDto> {
  default ValidatedPrism<String, BigDecimal> price() {
    return bigDecimal();
  }
}

@GenerateMapping
interface OrderMapping extends MappingSpec<Order, OrderDto> {
  default ValidatedPrism<String, UUID> id() {
    return uuid();
  }

  default ValidatedPrism<String, Instant> placedAt() {
    return instant();
  }

  default ValidatedPrism<String, Currency> currency() {
    return StandardCodecs.currency(); // qualified: the leaf shares the factory's name
  }

  default ValidatedPrism<String, OrderStatus> status() {
    return enumByName(OrderStatus.class);
  }

  // A wire-only component, computed from the whole domain value on build:
  default Getter<Order, String> displayTotal() {
    return Getter.of(
        order ->
            order.currency().getCurrencyCode()
                + " "
                + order.lines().stream()
                    .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
  }
}

// ANCHOR_END: capstone_spec

// ANCHOR: capstone_patch
/** The PATCH request bean: a null property means "not provided, leave unchanged". */
class CustomerPatchBean {
  private String name;
  private String email;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }
}

@GenerateMapping
interface CustomerPatchMapping extends OrderVocabulary, UpdateSpec<Customer, CustomerPatchBean> {}

// ANCHOR_END: capstone_patch

// ANCHOR: capstone_merge_spec
record Receipt(UUID id, String name, EmailAddress email, Instant placedAt) {}

@GenerateMerge
interface ReceiptAssembly {
  Receipt assemble(Order order, Customer customer);
}

// ANCHOR_END: capstone_merge_spec
