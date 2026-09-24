// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.higherkindedj.optics.validated.StandardCodecs.bigDecimal;
import static org.higherkindedj.optics.validated.StandardCodecs.enumByName;
import static org.higherkindedj.optics.validated.StandardCodecs.instant;
import static org.higherkindedj.optics.validated.StandardCodecs.localDate;
import static org.higherkindedj.optics.validated.StandardCodecs.offsetDateTime;
import static org.higherkindedj.optics.validated.StandardCodecs.uuid;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/codecs.html">Standard Codecs and Shared
 * Vocabulary</a> page: a typical DTO boundary (identifier, date, enum, money) mapped entirely from
 * the stock vocabulary, with no hand-written leaf, and the mix-in that shares a vocabulary across
 * specs.
 *
 * <p>The book {@code {{#include}}}s the anchored regions below, and {@code BookExampleOutputTest}
 * runs {@code main}.
 */
public final class StandardCodecsBook {

  private StandardCodecsBook() {}

  public static void main(String[] args) {
    // ANCHOR: mixin_usage
    // One vocabulary, two mappings - the inherited rename and leaf apply to both:
    ClientMappingImpl.INSTANCE.parse(new ClientDto("Ada Lovelace", "ada@example.org"));
    SupplierMappingImpl.INSTANCE.parse(new SupplierDto("Acme Ltd", "sales@acme.example", "01"));
    // ANCHOR_END: mixin_usage
    System.out.println(
        ClientMappingImpl.INSTANCE.parse(new ClientDto("Ada Lovelace", "not-an-email")));
  }
}

// ANCHOR: codecs_spec
enum OrderStatus {
  NEW,
  PAID,
  SHIPPED
}

record Order(UUID id, LocalDate placedOn, OrderStatus status, BigDecimal total) {}

record OrderDto(String id, String placedOn, String status, String total) {}

@GenerateMapping
interface OrderMapping extends MappingSpec<Order, OrderDto> {
  default ValidatedPrism<String, UUID> id() {
    return uuid();
  }

  default ValidatedPrism<String, LocalDate> placedOn() {
    return localDate();
  }

  default ValidatedPrism<String, OrderStatus> status() {
    return enumByName(OrderStatus.class);
  }

  default ValidatedPrism<String, BigDecimal> total() {
    return bigDecimal();
  }
}

// ANCHOR_END: codecs_spec

// ANCHOR: codecs_formatters
final class WireFormats {
  // Serves a JavaScript toISOString() producer: fixed three-digit millis, Z for UTC.
  // The canon is the formatter's, not that producer's output set: any spelling the
  // pattern round-trips (a +01:00 offset, say) is accepted as lawfully canonical.
  static final ValidatedPrism<String, OffsetDateTime> JS_WIRE =
      offsetDateTime(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX"));

  // Serves a +00:00-spelling producer (Python isoformat()): xxx renders the zero
  // offset as +00:00
  static final ValidatedPrism<String, OffsetDateTime> PYTHON_WIRE =
      offsetDateTime(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssxxx"));

  private WireFormats() {}
}

// ANCHOR_END: codecs_formatters

// ANCHOR: canonical_leaf
record Asset(UUID id, String label) {}

record AssetDto(String id, String label) {}

@GenerateMapping
interface AssetMapping extends MappingSpec<Asset, AssetDto> {
  // An uppercase-UUID wire (SQL Server): the lenient, throwing parse is fine,
  // because the render defines the canon and the per-value guard rejects
  // every spelling it cannot reproduce.
  default ValidatedPrism<String, UUID> id() {
    return ValidatedPrism.canonical(
        "not an uppercase UUID",
        UUID::fromString,
        uuid -> uuid.toString().toUpperCase(Locale.ROOT));
  }
}

// ANCHOR_END: canonical_leaf

// ANCHOR: instant_spec
record Reading(UUID id, Instant takenAt) {}

record ReadingDto(String id, String takenAt) {}

@GenerateMapping
interface ReadingMapping extends MappingSpec<Reading, ReadingDto> {
  default ValidatedPrism<String, UUID> id() {
    return uuid();
  }

  // instant() renders as Instant.toString() does, and accepts exactly that.
  default ValidatedPrism<String, Instant> takenAt() {
    return instant();
  }
}

// ANCHOR_END: instant_spec

// ANCHOR: mixin_spec
// Plain vocabulary - not a spec itself. Any spec whose records share these
// shapes extends it alongside MappingSpec.
interface ContactVocabulary {
  @MapField(to = "fullName")
  String name();

  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

record Client(String name, EmailAddress email) {}

record ClientDto(String fullName, String email) {}

@GenerateMapping
interface ClientMapping extends ContactVocabulary, MappingSpec<Client, ClientDto> {}

record Supplier(String name, EmailAddress email, String phone) {}

record SupplierDto(String fullName, String email, String phone) {}

@GenerateMapping
interface SupplierMapping extends ContactVocabulary, MappingSpec<Supplier, SupplierDto> {}

// ANCHOR_END: mixin_spec
