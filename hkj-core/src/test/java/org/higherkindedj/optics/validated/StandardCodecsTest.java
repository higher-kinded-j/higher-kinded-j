// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.optics.laws.ValidatedPrismLaws.assertValidatedPrismLaws;

import java.math.BigDecimal;
import java.net.URI;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The stock codec vocabulary: every codec obeys both {@code ValidatedPrism} laws on canonical
 * samples, rejects non-canonical spellings rather than normalising them, and reports the documented
 * located message.
 */
@DisplayName("StandardCodecs - the stock ValidatedPrism vocabulary")
class StandardCodecsTest {

  private static List<String> errors(Validated<NonEmptyList<FieldError>, ?> result) {
    return result.fold(nel -> nel.map(FieldError::toString).toJavaList(), _ -> List.of());
  }

  @Nested
  @DisplayName("identifiers")
  class Identifiers {

    @Test
    @DisplayName("uuid: canonical lowercase parses lawfully; uppercase is a rejection")
    void uuidCodec() {
      assertValidatedPrismLaws(
          StandardCodecs.uuid(), "123e4567-e89b-12d3-a456-426614174000", "not-a-uuid");
      assertThatValidated(StandardCodecs.uuid().parse("123E4567-E89B-12D3-A456-426614174000"))
          .isInvalid();
      assertThat(errors(StandardCodecs.uuid().parse("nope")))
          .containsExactly("not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000)");
      assertThat(StandardCodecs.uuid().build(new UUID(0L, 42L)))
          .isEqualTo("00000000-0000-0000-0000-00000000002a");
    }

    @Test
    @DisplayName("uri: valid syntax round-trips exactly; broken syntax is located")
    void uriCodec() {
      assertValidatedPrismLaws(
          StandardCodecs.uri(), "https://example.org/orders/42", "ht tp://broken");
      assertThat(errors(StandardCodecs.uri().parse("ht tp://broken")))
          .containsExactly("not a URI (expected e.g. https://example.org/orders/42)");
      assertThat(StandardCodecs.uri().build(URI.create("mailto:ada@example.org")))
          .isEqualTo("mailto:ada@example.org");
    }

    @Test
    @DisplayName("parameterless codecs are cached instances")
    void cachedInstances() {
      assertThat(StandardCodecs.uuid()).isSameAs(StandardCodecs.uuid());
      assertThat(StandardCodecs.localDate()).isSameAs(StandardCodecs.localDate());
      assertThat(StandardCodecs.bigDecimal()).isSameAs(StandardCodecs.bigDecimal());
    }

    @Test
    @DisplayName("parse(null) stays the caller's error, per the ValidatedPrism contract")
    void nullStaysCallerContract() {
      assertThatNullPointerException()
          .isThrownBy(() -> StandardCodecs.uuid().parse(null))
          .withMessage("source must not be null");
    }
  }

  @Nested
  @DisplayName("dates and times")
  class DatesAndTimes {

    @Test
    @DisplayName("localDate: ISO-8601 by default")
    void localDateIso() {
      assertValidatedPrismLaws(StandardCodecs.localDate(), "2026-07-28", "28/07/2026");
      assertThat(errors(StandardCodecs.localDate().parse("2026-7-28")))
          .containsExactly("not an ISO-8601 date (expected e.g. 2026-07-28)");
      assertThat(StandardCodecs.localDate().build(LocalDate.of(2026, 7, 28)))
          .isEqualTo("2026-07-28");
    }

    @Test
    @DisplayName("localDate(formatter): the message shows a sample in that format")
    void localDateCustomFormat() {
      var codec = StandardCodecs.localDate(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
      assertValidatedPrismLaws(codec, "28/07/2026", "2026-07-28");
      assertThat(errors(codec.parse("2026-07-28")))
          .containsExactly("not a date (expected e.g. 28/07/2026)");
    }

    @Test
    @DisplayName(
        "localDate(formatter): a formatter that renders differently than it parses rejects the"
            + " non-canonical form (the section law, per value)")
    void localDateNonCanonicalFormatRejected() {
      var codec = StandardCodecs.localDate(DateTimeFormatter.ofPattern("d/M/yyyy"));
      assertValidatedPrismLaws(codec, "1/7/2026", "01/07/2026");
      assertThatValidated(codec.parse("01/07/2026")).isInvalid();
    }

    @Test
    @DisplayName("localDate(formatter): fails at construction when the formatter cannot format")
    void localDateUnusableFormatterFailsFast() {
      assertThatNullPointerException()
          .isThrownBy(() -> StandardCodecs.localDate(null))
          .withMessage("formatter must not be null");
      assertThatExceptionOfType(DateTimeException.class)
          .isThrownBy(() -> StandardCodecs.localDate(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    @Test
    @DisplayName("instant: UTC canonical form only; an offset form belongs to offsetDateTime")
    void instantCodec() {
      assertValidatedPrismLaws(StandardCodecs.instant(), "2026-07-28T12:34:56Z", "not-an-instant");
      assertThatValidated(StandardCodecs.instant().parse("2026-07-28T13:34:56+01:00")).isInvalid();
      assertThat(errors(StandardCodecs.instant().parse("nope")))
          .containsExactly("not an ISO-8601 instant (expected e.g. 2026-07-28T12:34:56Z)");
    }

    @Test
    @DisplayName("instant: fractions parse in the three-digit groups Instant.toString renders")
    void instantFractionCanonicality() {
      assertValidatedPrismLaws(
          StandardCodecs.instant(), "2026-07-28T12:34:56.500Z", "2026-07-28T12:34:56.5Z");
      assertThatValidated(StandardCodecs.instant().parse("2026-07-28T12:34:56.000Z")).isInvalid();
    }

    @Test
    @DisplayName("offsetDateTime: a zero offset must be spelled Z, never +00:00")
    void offsetDateTimeZeroOffsetSpelledZ() {
      assertValidatedPrismLaws(
          StandardCodecs.offsetDateTime(), "2026-07-28T12:34:56Z", "2026-07-28T12:34:56+00:00");
      assertThatValidated(StandardCodecs.offsetDateTime().parse("2026-07-28T12:34:56.000Z"))
          .isInvalid();
    }

    @Test
    @DisplayName(
        "a formatter failing to render a value its lenient parse produced is a located"
            + " rejection, not an exception")
    void lenientFormatterRenderFailureIsLocated() {
      var lenientYear =
          new DateTimeFormatterBuilder()
              .parseLenient()
              .appendValue(ChronoField.YEAR, 4)
              .appendLiteral('-')
              .appendValue(ChronoField.MONTH_OF_YEAR, 2)
              .appendLiteral('-')
              .appendValue(ChronoField.DAY_OF_MONTH, 2)
              .toFormatter(Locale.ROOT);
      var codec = StandardCodecs.localDate(lenientYear);
      // The lenient parse accepts the signed year; rendering it throws inside the guard, so the
      // wire string is a located rejection.
      assertThatValidated(codec.parse("-0500-01-01")).isInvalid();
    }

    @Test
    @DisplayName("offsetDateTime: seconds always present, zero seconds included")
    void offsetDateTimeCodec() {
      assertValidatedPrismLaws(
          StandardCodecs.offsetDateTime(), "2026-07-28T12:34:56+01:00", "2026-07-28");
      // ISO_OFFSET_DATE_TIME would render zero seconds away; the codec keeps them canonical.
      assertValidatedPrismLaws(
          StandardCodecs.offsetDateTime(), "2026-07-28T12:00:00+01:00", "2026-07-28T12:00+01:00");
      assertValidatedPrismLaws(
          StandardCodecs.offsetDateTime(), "2026-07-28T12:34:56.5Z", "2026-07-28T12:34:56.50Z");
      assertThat(errors(StandardCodecs.offsetDateTime().parse("nope")))
          .containsExactly(
              "not an ISO-8601 date-time with offset (expected e.g. 2026-07-28T12:34:56+01:00)");
      assertThat(
              StandardCodecs.offsetDateTime()
                  .build(OffsetDateTime.of(2026, 7, 28, 12, 0, 0, 0, ZoneOffset.UTC)))
          .isEqualTo("2026-07-28T12:00:00Z");
    }

    @Test
    @DisplayName("offsetDateTime(formatter): the message shows a sample in that format")
    void offsetDateTimeCustomFormat() {
      var codec =
          StandardCodecs.offsetDateTime(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
      assertValidatedPrismLaws(codec, "2026-07-28T12:34:56+01:00", "nope");
      assertThat(errors(codec.parse("nope")))
          .containsExactly("not a date-time (expected e.g. 2026-07-28T12:34:56+01:00)");
      assertThatNullPointerException()
          .isThrownBy(() -> StandardCodecs.offsetDateTime(null))
          .withMessage("formatter must not be null");
    }
  }

  @Nested
  @DisplayName("enums")
  class Enums {

    enum OrderStatus {
      NEW,
      PAID,
      CANCELLED {
        // A constant with a body compiles to an anonymous subclass, whose Class token has no
        // enum constants: the factory guard names the problem instead of failing on first parse.
      }
    }

    @Test
    @DisplayName("enumByName: exact names parse; the message lists the permitted constants")
    void enumCodec() {
      assertValidatedPrismLaws(StandardCodecs.enumByName(OrderStatus.class), "PAID", "paid");
      assertThat(errors(StandardCodecs.enumByName(OrderStatus.class).parse("SHIPPED")))
          .containsExactly("unknown OrderStatus (expected one of NEW, PAID, CANCELLED)");
      assertThat(StandardCodecs.enumByName(OrderStatus.class).build(OrderStatus.CANCELLED))
          .isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("enumByName: a class token without enum constants is rejected at construction")
    void enumWithoutConstantsRejected() {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Class<OrderStatus> constantBodyClass = (Class) OrderStatus.CANCELLED.getClass();
      assertThatIllegalArgumentException()
          .isThrownBy(() -> StandardCodecs.enumByName(constantBodyClass))
          .withMessageContaining("is not an enum type");
      assertThatNullPointerException()
          .isThrownBy(() -> StandardCodecs.enumByName(null))
          .withMessage("enumType must not be null");
    }

    enum Uninhabited {}

    @Test
    @DisplayName("enumByName: an enum with no constants fails at construction, nothing can parse")
    void emptyEnumRejectedAtConstruction() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> StandardCodecs.enumByName(Uninhabited.class))
          .withMessageContaining("has no constants");
    }
  }

  @Nested
  @DisplayName("numbers and booleans")
  class NumbersAndBooleans {

    @Test
    @DisplayName("bigDecimal: plain notation, scale preserved; scientific notation rejected")
    void bigDecimalCodec() {
      assertValidatedPrismLaws(StandardCodecs.bigDecimal(), "123.45", "1E+3");
      assertValidatedPrismLaws(StandardCodecs.bigDecimal(), "0.010", "abc");
      assertThat(errors(StandardCodecs.bigDecimal().parse("1E+3")))
          .containsExactly("not a number in plain notation (expected e.g. 123.45)");
      assertThat(StandardCodecs.bigDecimal().build(new BigDecimal("0.010"))).isEqualTo("0.010");
    }

    @Test
    @DisplayName(
        "bigDecimal: an astronomical exponent is a located rejection before any rendering,"
            + " never an OutOfMemoryError")
    void bigDecimalHugeExponentRejectedCheaply() {
      assertThatValidated(StandardCodecs.bigDecimal().parse("1E+2147483647")).isInvalid();
      assertThatValidated(StandardCodecs.bigDecimal().parse("1E-2147483647")).isInvalid();
      assertThatValidated(StandardCodecs.bigDecimal().parse("-1E+1000000")).isInvalid();
    }

    @Test
    @DisplayName("intFromString: canonical integers; leading zeros and + are rejections")
    void intCodec() {
      assertValidatedPrismLaws(StandardCodecs.intFromString(), "42", "007");
      assertValidatedPrismLaws(StandardCodecs.intFromString(), "-7", "+7");
      assertThat(errors(StandardCodecs.intFromString().parse("nope")))
          .containsExactly("not a 32-bit integer (expected e.g. 42)");
    }

    @Test
    @DisplayName("longFromString: as intFromString with a wider range")
    void longCodec() {
      assertValidatedPrismLaws(StandardCodecs.longFromString(), "9223372036854775807", "0x10");
      assertThat(errors(StandardCodecs.longFromString().parse("nope")))
          .containsExactly("not a 64-bit integer (expected e.g. 42)");
    }

    @Test
    @DisplayName("doubleFromString: the canonical Double.toString form only")
    void doubleCodec() {
      assertValidatedPrismLaws(StandardCodecs.doubleFromString(), "3.14", "1e3");
      assertValidatedPrismLaws(StandardCodecs.doubleFromString(), "NaN", "nan");
      assertThat(errors(StandardCodecs.doubleFromString().parse("1e3")))
          .containsExactly("not a double in canonical form (expected e.g. 3.14)");
    }

    @Test
    @DisplayName("booleanStrict: exactly true or false, never lenient")
    void booleanCodec() {
      assertValidatedPrismLaws(StandardCodecs.booleanStrict(), "true", "TRUE");
      assertValidatedPrismLaws(StandardCodecs.booleanStrict(), "false", "no");
      assertThat(errors(StandardCodecs.booleanStrict().parse("TRUE")))
          .containsExactly("not a boolean (expected true or false)");
    }
  }

  @Nested
  @DisplayName("currency and locale")
  class CurrencyAndLocale {

    @Test
    @DisplayName("currency: ISO 4217 codes, uppercase as the standard defines them")
    void currencyCodec() {
      assertValidatedPrismLaws(StandardCodecs.currency(), "GBP", "gbp");
      assertThat(errors(StandardCodecs.currency().parse("notacurrency")))
          .containsExactly("not an ISO 4217 currency code (expected e.g. GBP)");
      assertThat(StandardCodecs.currency().build(Currency.getInstance("EUR"))).isEqualTo("EUR");
    }

    @Test
    @DisplayName("locale: BCP 47 tags in canonical case; case-folded tags are rejections")
    void localeCodec() {
      assertValidatedPrismLaws(StandardCodecs.locale(), "en-GB", "en-gb");
      assertThat(errors(StandardCodecs.locale().parse("not a tag!")))
          .containsExactly("not a BCP 47 language tag (expected e.g. en-GB)");
      assertThat(StandardCodecs.locale().build(Locale.forLanguageTag("en-GB"))).isEqualTo("en-GB");
    }
  }

  @Nested
  @DisplayName("as mapping leaves")
  class AsMappingLeaves {

    @Test
    @DisplayName("codecs slot into sibling accumulation: every bad field reports at once")
    void codecsAccumulateAsSiblings() {
      var result =
          Validated.fields()
              .field("placedOn", StandardCodecs.localDate().parse("28/07/2026"))
              .field("total", StandardCodecs.bigDecimal().parse("1E+3"))
              .field("id", StandardCodecs.uuid().parse("123e4567-e89b-12d3-a456-426614174000"))
              .apply((date, total, id) -> List.of(date, total, id));

      assertThatValidated(result).isInvalid();
      assertThat(errors(result))
          .containsExactly(
              "placedOn: not an ISO-8601 date (expected e.g. 2026-07-28)",
              "total: not a number in plain notation (expected e.g. 123.45)");
    }

    @Test
    @DisplayName("a valid instant survives the identity check exactly once parsed")
    void instantValidPath() {
      assertThatValidated(StandardCodecs.instant().parse("2026-07-28T12:34:56Z"))
          .isValid()
          .hasValue(Instant.parse("2026-07-28T12:34:56Z"));
    }
  }
}
