// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The stock codecs check a source's shape before parsing it, so a malformed value is rejected
 * without the exception a JDK parser would throw. A check may refuse only what its codec would
 * refuse without one, so each codec is held to an unscreened twin: the same message and render, and
 * the throwing parse the codec would use without its check. The one deliberate difference is a
 * currency code with a lower-case letter, which the JDK can misread as another code's.
 */
@DisplayName("StandardCodecs screens - each codec accepts and rejects exactly what its twin does")
class StandardCodecsScreenTest {

  enum Status {
    NEW,
    PAID,
    SHIPPED
  }

  /**
   * A stock codec, its unscreened twin, the sources the two must agree on, and the sources where
   * the codec deliberately differs.
   */
  record Twin(
      String name,
      ValidatedPrism<String, ?> screened,
      ValidatedPrism<String, ?> unscreened,
      List<String> samples,
      Predicate<String> differs) {

    Twin(
        String name,
        ValidatedPrism<String, ?> screened,
        ValidatedPrism<String, ?> unscreened,
        List<String> samples) {
      this(name, screened, unscreened, samples, source -> false);
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private static <A> ValidatedPrism<String, A> twin(
      String message, Function<String, A> parse, ValidatedPrism<String, A> screened) {
    return ValidatedPrism.canonical(message, parse, screened::build);
  }

  static final List<Twin> TWINS =
      List.of(
          new Twin(
              "uuid",
              StandardCodecs.uuid(),
              twin(
                  "not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000)",
                  UUID::fromString,
                  StandardCodecs.uuid()),
              List.of(
                  "123e4567-e89b-12d3-a456-426614174000",
                  "123E4567-E89B-12D3-A456-426614174000",
                  "123e4567-e89b-12d3-a456-42661417400g",
                  "123e4567xe89b-12d3-a456-426614174000",
                  "123e4567-e89bx12d3-a456-426614174000",
                  "123e4567-e89b-12d3xa456-426614174000",
                  "123e4567-e89b-12d3-a456x426614174000",
                  "1-2-3-4-5",
                  "NOPE",
                  "")),
          new Twin(
              "localDate",
              StandardCodecs.localDate(),
              twin(
                  "not an ISO-8601 date (expected e.g. 2026-07-28)",
                  LocalDate::parse,
                  StandardCodecs.localDate()),
              List.of(
                  "2026-07-28",
                  "+10000-01-01",
                  "-0001-01-01",
                  "2026-02-30",
                  "20x6-07-28",
                  "2026/07-28",
                  "2026-07/28",
                  "2026-07-2",
                  "28/07/2026",
                  "")),
          new Twin(
              "instant",
              StandardCodecs.instant(),
              twin(
                  "not an ISO-8601 instant (expected e.g. 2026-07-28T12:34:56Z)",
                  Instant::parse,
                  StandardCodecs.instant()),
              List.of(
                  "2026-07-28T12:34:56Z",
                  "2026-07-28T12:34:56.123Z",
                  "2026-07-28T12:34:56.120Z",
                  "2026-07-28T12:34:56+01:00",
                  "+10000-01-01T00:00:00Z",
                  "2026-07-28T12:34:56",
                  "2026-07-28 12:34:56Z",
                  "2026-07-28T12-34:56Z",
                  "28/07/2026",
                  "")),
          new Twin(
              "offsetDateTime",
              StandardCodecs.offsetDateTime(),
              twin(
                  "not an ISO-8601 date-time with offset (expected e.g. 2026-07-28T12:34:56+01:00)",
                  OffsetDateTime::parse,
                  StandardCodecs.offsetDateTime()),
              List.of(
                  "2026-07-28T12:34:56+01:00",
                  "2026-07-28T12:00:00Z",
                  "2026-07-28T12:34:56+00:00",
                  "2026-07-28T12:00+01:00",
                  "2026-07-28T12:34:56",
                  "28/07/2026T12:34:56Z",
                  "")),
          new Twin(
              "bigDecimal",
              StandardCodecs.bigDecimal(),
              twin(
                  "not a number in plain notation (expected e.g. 123.45)",
                  source -> {
                    BigDecimal value = new BigDecimal(source);
                    if (value.scale() < 0 || value.scale() > source.length()) {
                      throw new IllegalArgumentException(source);
                    }
                    return value;
                  },
                  StandardCodecs.bigDecimal()),
              List.of(
                  "123.45",
                  "0.010",
                  "-1.5",
                  "1",
                  "01.5",
                  "1.",
                  ".5",
                  "1x",
                  "1.5x",
                  "1E+3",
                  "1e3",
                  "1E-3",
                  "1E-10",
                  "\u0664\u0662",
                  "1E+2147483647",
                  "-",
                  "")),
          new Twin(
              "intFromString",
              StandardCodecs.intFromString(),
              twin(
                  "not a 32-bit integer (expected e.g. 42)",
                  Integer::parseInt,
                  StandardCodecs.intFromString()),
              List.of(
                  "42",
                  "-7",
                  "0",
                  "-0",
                  "007",
                  "+7",
                  "4x2",
                  "2147483647",
                  "2147483648",
                  "-2147483648",
                  "-2147483649",
                  "12345678901",
                  "\u0664\u0662",
                  "two",
                  "-",
                  "")),
          new Twin(
              "longFromString",
              StandardCodecs.longFromString(),
              twin(
                  "not a 64-bit integer (expected e.g. 42)",
                  Long::parseLong,
                  StandardCodecs.longFromString()),
              List.of(
                  "9223372036854775807",
                  "9223372036854775808",
                  "-9223372036854775808",
                  "12345678901234567890",
                  "-42",
                  "0x10",
                  "")),
          new Twin(
              "doubleFromString",
              StandardCodecs.doubleFromString(),
              twin(
                  "not a double in canonical form (expected e.g. 3.14)",
                  Double::parseDouble,
                  StandardCodecs.doubleFromString()),
              List.of(
                  "3.14",
                  "-0.0",
                  "1.0E10",
                  "1.5E-3",
                  "NaN",
                  "Infinity",
                  "-Infinity",
                  "nan",
                  "1",
                  "1x",
                  "1.",
                  "1.5X",
                  "1.5E",
                  "1.5E-",
                  "1.5E3x",
                  "1e3",
                  "3.140",
                  "")),
          new Twin(
              "booleanStrict",
              StandardCodecs.booleanStrict(),
              twin(
                  "not a boolean (expected true or false)",
                  source -> {
                    if (source.equals("true")) {
                      return Boolean.TRUE;
                    }
                    if (source.equals("false")) {
                      return Boolean.FALSE;
                    }
                    throw new IllegalArgumentException(source);
                  },
                  StandardCodecs.booleanStrict()),
              List.of("true", "false", "TRUE", "yes", "")),
          new Twin(
              "currency",
              StandardCodecs.currency(),
              twin(
                  "not an ISO 4217 currency code (expected e.g. GBP)",
                  Currency::getInstance,
                  StandardCodecs.currency()),
              List.of("GBP", "gbp", "GbP", "GBp", "G1P", "gBP", "ZZZ", "GB", ""),
              source -> !source.equals(source.toUpperCase(Locale.ROOT))),
          new Twin(
              "enumByName",
              StandardCodecs.enumByName(Status.class),
              twin(
                  "unknown Status (expected one of NEW, PAID, SHIPPED)",
                  source -> Enum.valueOf(Status.class, source),
                  StandardCodecs.enumByName(Status.class)),
              List.of("PAID", "paid", "DISPATCHED", "")));

  static Stream<Arguments> sources() {
    return TWINS.stream()
        .flatMap(
            twin ->
                twin.samples().stream()
                    .filter(source -> !twin.differs().test(source))
                    .map(source -> Arguments.of(twin, source)));
  }

  @ParameterizedTest(name = "{0}: \"{1}\"")
  @MethodSource("sources")
  @DisplayName("a near miss is refused, and a canonical source accepted, as the twin does")
  void agreesWithItsTwin(Twin twin, String source) {
    assertThat((Object) twin.screened().parse(source)).isEqualTo(twin.unscreened().parse(source));
  }

  @Test
  @DisplayName(
      "currency refuses a lower-case letter, which the JDK can misread as another code's, where"
          + " the twin accepted it")
  void currencyRefusesAMisreadCode() {
    Validated<NonEmptyList<FieldError>, Currency> misread =
        ValidatedPrism.canonical(
                "not an ISO 4217 currency code (expected e.g. GBP)",
                Currency::getInstance,
                Currency::getCurrencyCode)
            .parse("XPt");

    assertThat(misread.isValid()).isTrue();
    assertThat(misread.get()).isNotEqualTo(Currency.getInstance("XPT"));
    assertThatValidated(StandardCodecs.currency().parse("XPt"))
        .hasFieldErrors("not an ISO 4217 currency code (expected e.g. GBP)");
  }
}
