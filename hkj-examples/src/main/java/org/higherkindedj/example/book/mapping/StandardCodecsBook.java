// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

// ANCHOR: codecs_imports
import static org.higherkindedj.optics.validated.StandardCodecs.bigDecimal;
import static org.higherkindedj.optics.validated.StandardCodecs.enumByName;
import static org.higherkindedj.optics.validated.StandardCodecs.instant;
import static org.higherkindedj.optics.validated.StandardCodecs.localDate;
import static org.higherkindedj.optics.validated.StandardCodecs.offsetDateTime;
import static org.higherkindedj.optics.validated.StandardCodecs.uuid;
// ANCHOR_END: codecs_imports

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
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
    // One vocabulary, two mappings: the rename and email leaf apply to both, phone to Supplier.
    Validated<NonEmptyList<FieldError>, Client> client =
        ClientMappingImpl.INSTANCE.parse(new ClientDto("Ada Lovelace", "not-an-email"));
    // Invalid(NonEmptyList[email: not an email address])
    Validated<NonEmptyList<FieldError>, Supplier> supplier =
        SupplierMappingImpl.INSTANCE.parse(
            new SupplierDto("Acme Ltd", "sales@acme.example", "call us"));
    // Invalid(NonEmptyList[phone: not a phone number])
    // ANCHOR_END: mixin_usage
    System.out.println(client);
    System.out.println(supplier);
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
  // A browser's toISOString(): always three fraction digits, and Z. withZone lets it write an
  // Instant.
  private static final DateTimeFormatter BROWSER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSX").withZone(ZoneOffset.UTC);

  // For an Instant component. instant() rejects .000Z, which Instant.toString() never writes.
  static final ValidatedPrism<String, Instant> BROWSER_INSTANT =
      ValidatedPrism.canonical(
          "not a browser timestamp (expected e.g. 2026-07-28T12:34:56.000Z)",
          raw -> Instant.from(BROWSER.parse(raw)),
          BROWSER::format);

  // For an OffsetDateTime component, the formatter overload takes the browser's spelling, at any
  // offset.
  static final ValidatedPrism<String, OffsetDateTime> BROWSER_OFFSET =
      offsetDateTime(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX"));

  // Python's isoformat(): +00:00 for UTC, and six fraction digits, or none when they are zero.
  private static final DateTimeFormatter WHOLE_SECONDS =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssxxx");
  private static final DateTimeFormatter MICROSECONDS =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSxxx");

  static final ValidatedPrism<String, OffsetDateTime> PYTHON_OFFSET =
      ValidatedPrism.canonical(
          "not a Python isoformat() timestamp (expected e.g. 2026-07-28T12:34:56.123456+00:00)",
          OffsetDateTime::parse,
          time -> time.format(time.getNano() / 1_000 == 0 ? WHOLE_SECONDS : MICROSECONDS));

  // For an Instant component: the same two shapes, with the offset fixed at +00:00.
  static final ValidatedPrism<String, Instant> PYTHON_INSTANT =
      ValidatedPrism.canonical(
          "not a Python isoformat() UTC timestamp (expected e.g. 2026-07-28T12:34:56.123456+00:00)",
          raw -> OffsetDateTime.parse(raw).toInstant(),
          instant -> PYTHON_OFFSET.build(instant.atOffset(ZoneOffset.UTC)));

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

  // Client has no phone, so this leaf stays inert there; only Supplier binds it.
  default ValidatedPrism<String, String> phone() {
    return ValidatedPrism.of(
        raw ->
            raw.matches("\\+?[0-9 ]+")
                ? Validated.validNel(raw)
                : Validated.invalidNel(FieldError.of("not a phone number")),
        phone -> phone);
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
