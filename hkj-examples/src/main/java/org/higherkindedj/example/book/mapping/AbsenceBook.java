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
    var memberMapping = MemberMappingImpl.INSTANCE;

    // Absence travels as null in both directions; a present value still validates.
    MemberDto wire = memberMapping.build(new Member("Ada", Optional.empty(), Optional.empty()));
    // MemberDto[name=Ada, nickname=null, altEmail=null]

    Validated<NonEmptyList<FieldError>, Member> absent =
        memberMapping.parse(new MemberDto("Ada", null, null));
    // Valid(Member[name=Ada, nickname=Optional.empty, altEmail=Optional.empty])

    Validated<NonEmptyList<FieldError>, Member> badAltEmail =
        memberMapping.parse(new MemberDto("Ada", "countess", "not-an-email"));
    // Invalid(NonEmptyList[altEmail: not an email address])
    // ANCHOR_END: bridge_usage
    System.out.println(wire);
    System.out.println(absent);
    System.out.println(badAltEmail);

    // ANCHOR: invariant_usage
    Validated<NonEmptyList<FieldError>, Reservation> reservation =
        ReservationMappingImpl.INSTANCE.parse(
            new ReservationDto(
                null,
                List.of(
                    new StayDto("2026-03-01", "2026-03-04"),
                    new StayDto("2026-03-09", "2026-03-07"))));
    // Invalid(NonEmptyList[guest: must not be null, stays.1: checkOut must be after checkIn])
    // ANCHOR_END: invariant_usage
    System.out.println(reservation);
  }
}

// ANCHOR: bridge_spec
record Member(String name, Optional<String> nickname, Optional<EmailAddress> altEmail) {}

// The wire carries optional data the way a JSON binder does: a nullable component.
record MemberDto(String name, @Nullable String nickname, @Nullable String altEmail) {}

@GenerateMapping
interface MemberMapping extends MappingSpec<Member, MemberDto> {
  // No conversion: the marker restates the component and the value is copied.
  @OptionalBridge
  Optional<String> nickname();

  // A conversion: the same annotation on the component's leaf, declared over the ELEMENT types.
  @OptionalBridge
  default ValidatedPrism<String, EmailAddress> altEmail() {
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: bridge_spec

// ANCHOR: invariant_spec
// The domain guards itself: a stay must end after it starts. The wire carries no such rule.
record Stay(LocalDate checkIn, LocalDate checkOut) {
  Stay {
    if (!checkOut.isAfter(checkIn)) {
      throw new IllegalArgumentException("checkOut must be after checkIn");
    }
  }
}

record StayDto(String checkIn, String checkOut) {}

record Reservation(String guest, List<Stay> stays) {}

record ReservationDto(String guest, List<StayDto> stays) {}

@GenerateMapping
interface StayMapping extends MappingSpec<Stay, StayDto> {
  default ValidatedPrism<String, LocalDate> checkIn() {
    return StandardCodecs.localDate();
  }

  default ValidatedPrism<String, LocalDate> checkOut() {
    return StandardCodecs.localDate();
  }
}

@GenerateMapping
interface ReservationMapping extends MappingSpec<Reservation, ReservationDto> {}

// ANCHOR_END: invariant_spec
