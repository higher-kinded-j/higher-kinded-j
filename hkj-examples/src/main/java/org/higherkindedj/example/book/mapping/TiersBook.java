// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import java.util.Locale;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/tiers.html">What Your Spec Generates</a>
 * page.
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
public final class TiersBook {

  private TiersBook() {}

  public static void main(String[] args) {
    // ANCHOR: projection_usage
    Employee employee = new Employee("Ada", "Research", 36);
    Lens<Employee, EmployeeCardDto> badge = EmployeeCardMappingImpl.INSTANCE.asLens();
    Employee moved = badge.set(new EmployeeCardDto("Ada", "Platform"), employee);
    // Employee[name=Ada, department=Platform, age=36]
    // ANCHOR_END: projection_usage
    System.out.println(moved);

    // ANCHOR: leaf_projection_usage
    Subscriber subscriber = new Subscriber("7", new EmailAddress("ada@corp.example"), 36);
    var subscriberDetailsMapping = SubscriberDetailsMappingImpl.INSTANCE;

    // The projected components validate and write back; the unprojected id survives untouched.
    Validated<NonEmptyList<FieldError>, Subscriber> renewed =
        subscriberDetailsMapping.patch(
            subscriber, new SubscriberDetailsDto("grace@corp.example", 37));
    // Valid(Subscriber[id=7, email=EmailAddress[value=grace@corp.example], age=37])

    // Dense semantics: every projected field applies - a null is a located error, never absence.
    Validated<NonEmptyList<FieldError>, Subscriber> nullEmail =
        subscriberDetailsMapping.patch(subscriber, new SubscriberDetailsDto(null, 37));
    // Invalid(NonEmptyList[email: must not be null])
    // ANCHOR_END: leaf_projection_usage
    System.out.println(renewed);
    System.out.println(nullEmail);
  }
}

// ANCHOR: projection_spec
record Employee(String name, String department, int age) {}

record EmployeeCardDto(String name, String department) {}

@GenerateMapping
interface EmployeeCardMapping extends MappingSpec<Employee, EmployeeCardDto> {}

// ANCHOR_END: projection_spec

// ANCHOR: leaf_projection_spec
record Subscriber(String id, EmailAddress email, int age) {}

record SubscriberDetailsDto(String email, int age) {}

@GenerateMapping
interface SubscriberDetailsMapping extends MappingSpec<Subscriber, SubscriberDetailsDto> {
  default ValidatedPrism<String, EmailAddress> email() {
    return ValidatedPrism.of(
        raw ->
            raw.contains("@")
                ? Validated.validNel(new EmailAddress(raw))
                : Validated.invalidNel(FieldError.of("not an email address")),
        EmailAddress::value);
  }
}

// ANCHOR_END: leaf_projection_spec

// ANCHOR: coupon_spec
record Coupon(String code, int percent) {}

record CouponDto(String code, int percent) {}

@GenerateMapping
interface CouponMapping extends MappingSpec<Coupon, CouponDto> {
  default ValidatedPrism<String, String> code() { // never fails: it only tidies the spelling
    return ValidatedPrism.of(
        raw -> Validated.validNel(raw.strip().toUpperCase(Locale.ROOT)), code -> code);
  }
}

// ANCHOR_END: coupon_spec
