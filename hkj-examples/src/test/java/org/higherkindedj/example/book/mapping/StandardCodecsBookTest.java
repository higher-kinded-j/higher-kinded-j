// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.optics.validated.StandardCodecs.currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.laws.MappingLaws;
import org.higherkindedj.optics.validated.StandardCodecs;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Standard codecs section's running example: the stock-codec mapping obeys the mapping laws,
 * and a bad wire reports every bad field at once with the codecs' located messages.
 */
@DisplayName("the book's stock-codec mapping obeys the laws and locates every failure")
class StandardCodecsBookTest {

  @Test
  void orderMappingObeysTheLaws() {
    MappingLaws.assertMappingLaws(
        OrderMappingImpl.INSTANCE.asValidatedPrism(),
        new OrderDto(
            "123e4567-e89b-12d3-a456-426614174000", "2026-07-28", "PAID", "99.95"), // parses
        new OrderDto("nope", "2026-07-28", "PAID", "99.95")); // must not parse
  }

  @Test
  void everyBadFieldReportsAtOnceWithTheCodecMessage() {
    // ANCHOR: codecs_errors
    Validated<NonEmptyList<FieldError>, Order> parsed =
        OrderMappingImpl.INSTANCE.parse(new OrderDto("NOPE", "28/07/2026", "DISPATCHED", "1E+3"));

    assertThatValidated(parsed)
        .isInvalid()
        .hasFieldErrors(
            "id: not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000)",
            "placedOn: not an ISO-8601 date (expected e.g. 2026-07-28)",
            "status: unknown OrderStatus (expected one of NEW, PAID, SHIPPED)",
            "total: not a number in plain notation (expected e.g. 123.45)");
    // ANCHOR_END: codecs_errors
  }

  @Test
  void aGoodWireBuildsBack() {
    Order order =
        new Order(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            LocalDate.of(2026, 7, 28),
            OrderStatus.PAID,
            new BigDecimal("99.95"));

    assertThat(OrderMappingImpl.INSTANCE.build(order))
        .isEqualTo(
            new OrderDto("123e4567-e89b-12d3-a456-426614174000", "2026-07-28", "PAID", "99.95"));
  }

  @Test
  void formatterOverloadsMakeTheProducersCanonTheCanon() {
    assertThatValidated(WireFormats.BROWSER_OFFSET.parse("2026-07-28T12:34:56.500Z")).isValid();
    assertThatValidated(WireFormats.BROWSER_OFFSET.parse("2026-07-28T12:34:56.5Z")).isInvalid();
    assertThatValidated(WireFormats.PYTHON_OFFSET.parse("2026-07-28T12:34:56+00:00")).isValid();
  }

  @Test
  void aFormatterCanonIsThePatternsNotTheProducersOutputSet() {
    // toISOString() only ever emits Z, but the pattern round-trips any offset: lawful.
    assertThatValidated(WireFormats.BROWSER_OFFSET.parse("2026-07-28T12:34:56.500+01:00"))
        .isValid();
    // The pattern also fixes the precision: extra fractional digits do not fit it.
    assertThatValidated(WireFormats.BROWSER_OFFSET.parse("2026-07-28T12:34:56.5001Z")).isInvalid();
    assertThatValidated(WireFormats.PYTHON_OFFSET.parse("2026-07-28T12:34:56.5+00:00")).isInvalid();
  }

  @Test
  void theCanonicalLeafAcceptsExactlyWhatItsRenderProduces() {
    String upper = "123E4567-E89B-12D3-A456-426614174000";
    Asset asset = new Asset(UUID.fromString(upper), "rack");

    assertThat(AssetMappingImpl.INSTANCE.build(asset).id()).isEqualTo(upper);
    assertThatValidated(AssetMappingImpl.INSTANCE.parse(new AssetDto(upper, "rack"))).isValid();
    // The JDK parses lowercase happily; the render cannot reproduce it, so the guard rejects it.
    assertThatValidated(
            AssetMappingImpl.INSTANCE.parse(
                new AssetDto(upper.toLowerCase(java.util.Locale.ROOT), "rack")))
        .isInvalid();
  }

  @Test
  @DisplayName("instant() accepts exactly the spellings it renders")
  void instantAcceptsExactlyWhatItRenders() {
    String id = "123e4567-e89b-12d3-a456-426614174000";

    // Instant.toString() writes Z for a zero offset, and fractions in three-digit groups.
    assertThatValidated(
            ReadingMappingImpl.INSTANCE.parse(new ReadingDto(id, "2026-07-28T12:34:56Z")))
        .isValid();
    assertThatValidated(
            ReadingMappingImpl.INSTANCE.parse(new ReadingDto(id, "2026-07-28T12:34:56.500Z")))
        .isValid();

    // It renders no fraction at all when the fraction is zero, and never one digit.
    List<String> rejected =
        List.of(
            "2026-07-28T12:34:56.000Z", // a browser's toISOString()
            "2026-07-28T12:34:56.5Z", // one digit where Instant writes three
            "2026-07-28T12:34:56+00:00"); // Python's isoformat(), a spelled-out zero offset
    for (String spelling : rejected) {
      assertThatValidated(ReadingMappingImpl.INSTANCE.parse(new ReadingDto(id, spelling)))
          .isInvalid()
          .hasFieldErrors("takenAt: not an ISO-8601 instant (expected e.g. 2026-07-28T12:34:56Z)");
    }
  }

  @Test
  @DisplayName("the canon grid: each stock date-time codec accepts exactly its own render")
  void theCanonGridHolds() {
    record Cell(String spelling, boolean instant, boolean offset) {}
    List<Cell> grid =
        List.of(
            new Cell("2026-07-28T12:34:56Z", true, true),
            new Cell("2026-07-28T12:34:56.500Z", true, false),
            new Cell("2026-07-28T12:34:56.5Z", false, true),
            new Cell("2026-07-28T12:34:56.123Z", true, true),
            new Cell("2026-07-28T12:34:56.000Z", false, false),
            new Cell("2026-07-28T12:34:56+00:00", false, false));
    for (Cell cell : grid) {
      assertThat(StandardCodecs.instant().parse(cell.spelling()).isValid())
          .as("instant() on %s", cell.spelling())
          .isEqualTo(cell.instant());
      assertThat(StandardCodecs.offsetDateTime().parse(cell.spelling()).isValid())
          .as("offsetDateTime() on %s", cell.spelling())
          .isEqualTo(cell.offset());
    }
  }

  @Test
  @DisplayName(
      "a browser's timestamps: instant() drops the whole seconds, BROWSER_INSTANT takes all")
  void theBrowserLeavesTakeEveryToIsoStringSpelling() {
    for (String millis : List.of("000", "100", "120", "123")) {
      String spelling = "2026-07-28T12:34:56." + millis + "Z";
      assertThatValidated(WireFormats.BROWSER_INSTANT.parse(spelling)).isValid();
      assertThatValidated(WireFormats.BROWSER_OFFSET.parse(spelling)).isValid();
      assertThat(StandardCodecs.instant().parse(spelling).isValid())
          .as("instant() on %s", spelling)
          .isEqualTo(!millis.equals("000"));
    }
    Instant moment = Instant.parse("2026-07-28T12:34:56Z");
    assertThat(WireFormats.BROWSER_INSTANT.build(moment)).isEqualTo("2026-07-28T12:34:56.000Z");
    assertThatValidated(WireFormats.BROWSER_INSTANT.parse("2026-07-28T12:34:56Z")).isInvalid();
    assertThatValidated(WireFormats.BROWSER_INSTANT.parse("2026-07-28T12:34:56.123+01:00"))
        .isInvalid();
  }

  @Test
  @DisplayName(
      "Python's isoformat(): whole seconds or six digits, both taken, anything else refused")
  void thePythonLeafTakesIsoformatInBothShapes() {
    assertThatValidated(WireFormats.PYTHON_OFFSET.parse("2026-07-28T12:34:56+00:00")).isValid();
    assertThatValidated(WireFormats.PYTHON_OFFSET.parse("2026-07-28T12:34:56.123456+00:00"))
        .isValid();
    assertThatValidated(WireFormats.PYTHON_OFFSET.parse("2026-07-28T12:34:56.123+00:00"))
        .isInvalid();
    assertThatValidated(WireFormats.PYTHON_OFFSET.parse("2026-07-28T12:34:56Z")).isInvalid();
    assertThat(StandardCodecs.offsetDateTime().parse("2026-07-28T12:34:56+00:00").isValid())
        .isFalse();
  }

  @Test
  @DisplayName("intFromString() accepts exactly the spelling Integer.toString renders")
  void intFromStringAcceptsOnlyItsOwnRender() {
    // ANCHOR: check_int_canon
    ValidatedPrism<String, Integer> quantity = StandardCodecs.intFromString();

    assertThatValidated(quantity.parse("42")).isValid();
    for (String spelling : List.of("042", "+42", " 42", "42.0")) {
      assertThatValidated(quantity.parse(spelling)).isInvalid(); // Integer.parseInt takes two
    }
    // ANCHOR_END: check_int_canon
  }

  @Test
  @DisplayName("plain offsetDateTime() rejects a browser millisecond value that ends in zero")
  void offsetDateTimeRejectsTrailingZeroMillis() {
    // ANCHOR: check_browser_offset
    ValidatedPrism<String, OffsetDateTime> placedAt = StandardCodecs.offsetDateTime();

    assertThatValidated(placedAt.parse("2026-07-28T12:34:56.123Z")).isValid();
    for (String millis : List.of("120", "100", "000")) { // it renders .12Z, .1Z and no fraction
      assertThatValidated(placedAt.parse("2026-07-28T12:34:56." + millis + "Z")).isInvalid();
    }
    // ANCHOR_END: check_browser_offset
  }

  @Test
  @DisplayName("a leaf named like its factory calls itself unless the call is qualified")
  void anUnqualifiedFactoryCallRecurses() {
    assertThatThrownBy(() -> new CurrencyLeaves() {}.currency())
        .isInstanceOf(StackOverflowError.class);
  }

  /** The leaf a spec would declare, calling the statically imported factory unqualified. */
  interface CurrencyLeaves {
    default ValidatedPrism<String, Currency> currency() {
      return currency(); // meant StandardCodecs.currency(); the nearer method wins
    }
  }
}
