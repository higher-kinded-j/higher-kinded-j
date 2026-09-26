// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.annotations.OptionalBridge;
import org.higherkindedj.optics.validated.StandardCodecs;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.jspecify.annotations.Nullable;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/absence.html">Absent Fields and Record
 * Invariants</a> page.
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
public final class AbsenceBook {

  private AbsenceBook() {}

  public static void main(String[] args) {
    // ANCHOR: bridge_usage
    CustomerProfileMappingImpl profileMapping = CustomerProfileMappingImpl.INSTANCE;

    // Absence travels as null in both directions; a present value still validates.
    CustomerProfileDto wire =
        profileMapping.build(new CustomerProfile("Ada", Optional.empty(), Optional.empty()));
    // CustomerProfileDto[name=Ada, nickname=null, altEmail=null]

    Validated<NonEmptyList<FieldError>, CustomerProfile> absent =
        profileMapping.parse(new CustomerProfileDto("Ada", null, null));
    // Valid(CustomerProfile[name=Ada, nickname=Optional.empty, altEmail=Optional.empty])

    Validated<NonEmptyList<FieldError>, CustomerProfile> badAltEmail =
        profileMapping.parse(new CustomerProfileDto("Ada", "countess", "not-an-email"));
    // Invalid(NonEmptyList[altEmail: not an email address])
    // ANCHOR_END: bridge_usage
    System.out.println(wire);
    System.out.println(absent);
    System.out.println(badAltEmail);

    // ANCHOR: invariant_usage
    Validated<NonEmptyList<FieldError>, Delivery> delivery =
        DeliveryMappingImpl.INSTANCE.parse(
            new DeliveryDto(
                null,
                List.of(
                    new DeliveryWindowDto("2026-03-01", "2026-03-04"),
                    new DeliveryWindowDto("2026-03-09", "2026-03-07"))));
    // Invalid(NonEmptyList[recipient: must not be null, windows.1: latest must be after earliest])
    // ANCHOR_END: invariant_usage
    System.out.println(delivery);
  }
}

// ANCHOR: bridge_spec
record CustomerProfile(String name, Optional<String> nickname, Optional<EmailAddress> altEmail) {}

// The wire carries optional data the way a JSON binder does: a nullable component.
record CustomerProfileDto(String name, @Nullable String nickname, @Nullable String altEmail) {}

@GenerateMapping
interface CustomerProfileMapping extends MappingSpec<CustomerProfile, CustomerProfileDto> {
  // No conversion: the marker restates the component and the value is copied.
  @OptionalBridge
  Optional<String> nickname();

  // A conversion: the same annotation on the component's leaf, over the types inside the Optional.
  @OptionalBridge
  default ValidatedPrism<String, EmailAddress> altEmail() {
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: bridge_spec

// ANCHOR: invariant_spec
// The domain guards itself: a window must end after it starts. The wire carries no such rule.
record DeliveryWindow(LocalDate earliest, LocalDate latest) {
  DeliveryWindow {
    if (!latest.isAfter(earliest)) {
      throw new IllegalArgumentException("latest must be after earliest");
    }
  }
}

record DeliveryWindowDto(String earliest, String latest) {}

record Delivery(String recipient, List<DeliveryWindow> windows) {}

record DeliveryDto(String recipient, List<DeliveryWindowDto> windows) {}

@GenerateMapping
interface DeliveryWindowMapping extends MappingSpec<DeliveryWindow, DeliveryWindowDto> {
  default ValidatedPrism<String, LocalDate> earliest() {
    return StandardCodecs.localDate();
  }

  default ValidatedPrism<String, LocalDate> latest() {
    return StandardCodecs.localDate();
  }
}

@GenerateMapping
interface DeliveryMapping extends MappingSpec<Delivery, DeliveryDto> {}

// ANCHOR_END: invariant_spec

// ANCHOR: voucher_pair
record Voucher(String code, Optional<LocalDate> expiry) {}

record VoucherDto(String code, @Nullable String expiry) {}

// ANCHOR_END: voucher_pair

// ANCHOR: voucher_spec
@GenerateMapping
interface VoucherMapping extends MappingSpec<Voucher, VoucherDto> {
  @OptionalBridge
  default ValidatedPrism<String, LocalDate> expiry() {
    return StandardCodecs.localDate();
  }
}

// ANCHOR_END: voucher_spec

// ANCHOR: discount_spec
// A basket's discount, spread over its items: at most 500p off each.
record BulkDiscount(int totalPence, int items) {
  BulkDiscount {
    if (Math.ceilDiv(totalPence, items) > 500) { // rounds up, so 1001p over 2 items is 501p
      throw new IllegalArgumentException("at most 500p off per item");
    }
  }
}

record BulkDiscountDto(int totalPence, int items) {}

record Basket(String id, BulkDiscount discount) {}

record BasketDto(String id, BulkDiscountDto discount) {}

@GenerateMapping
interface BulkDiscountMapping extends MappingSpec<BulkDiscount, BulkDiscountDto> {}

@GenerateMapping
interface BasketMapping extends MappingSpec<Basket, BasketDto> {}

// ANCHOR_END: discount_spec
