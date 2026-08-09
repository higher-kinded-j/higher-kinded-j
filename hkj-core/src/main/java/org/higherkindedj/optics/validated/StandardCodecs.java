// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.validated;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.validated.FieldError;

/**
 * The stock codec vocabulary: one {@link ValidatedPrism} factory per standard conversion family, so
 * a typical DTO boundary (dates, enums, money, identifiers) needs no hand-written leaves.
 *
 * <pre>{@code
 * import static org.higherkindedj.optics.validated.StandardCodecs.*;
 *
 * @GenerateMapping
 * public interface OrderMapping extends MappingSpec<Order, OrderDto> {
 *   default ValidatedPrism<String, LocalDate> placedOn() { return localDate(); }
 *   default ValidatedPrism<String, OrderStatus> status() { return enumByName(OrderStatus.class); }
 *   default ValidatedPrism<String, BigDecimal> total()   { return bigDecimal(); }
 *   default ValidatedPrism<String, UUID> id()            { return uuid(); }
 * }
 * }</pre>
 *
 * <p><b>Canonical forms only.</b> Every codec honours the {@code ValidatedPrism} build-parse
 * section law ({@code parse(s) == Valid(a)} implies {@code build(a) == s}) by accepting only the
 * form it renders: an accepted wire value always rebuilds to exactly itself. Case-folded UUIDs,
 * leading zeros, scientific notation and lowercase language tags are located rejections, never
 * silent normalisations. The outbound {@code build} direction is total formatting, matching the
 * leaf contract.
 *
 * <p><b>Located, copy-worthy errors.</b> Every parse failure is a single {@link FieldError} whose
 * message is terse and actionable ({@code not an ISO-8601 date (expected e.g. 2026-07-28)}); under
 * a mapping it arrives located by component name, so the codecs feed the 422 leg unchanged. Enum
 * failures name the permitted constants.
 *
 * <p><b>Nulls.</b> A codec never sees {@code null}: under a mapping the null guard locates the null
 * first, and standalone {@code parse(null)} is the caller's error, per the {@code ValidatedPrism}
 * contract.
 *
 * <p><b>Boxed components only.</b> The number and boolean codecs focus the box types: a {@code
 * ValidatedPrism<String, int>} cannot exist, so a record component typed {@code int} cannot take a
 * leaf — declare the component as {@code Integer} (the mapper rejects the mismatch at compile time
 * either way).
 *
 * <p><b>Under the star import, qualify a leaf that shares its component's name.</b> A mapping leaf
 * is a {@code default} method named after the domain component, and a method member beats a static
 * import: for a component named {@code currency}, {@code locale} or {@code uuid}, an unqualified
 * {@code return currency();} calls the leaf itself and overflows the stack — write {@code return
 * StandardCodecs.currency();}.
 *
 * <p>Parameterless factories return cached instances; the parameterised ones ({@link
 * #localDate(DateTimeFormatter)}, {@link #offsetDateTime(DateTimeFormatter)}, {@link
 * #enumByName(Class)}) construct per call and are cheap enough to sit in a spec's {@code default}
 * method. A custom formatter must render what it parses (the section law is enforced per value: a
 * value whose rendering differs from its source is rejected), and must be able to format the
 * temporal type — the factory formats a sample eagerly, so a formatter that can render nothing
 * fails at construction; one that fails only on particular values yields located rejections for
 * those values.
 */
public final class StandardCodecs {

  private StandardCodecs() {}

  private static final LocalDate SAMPLE_DATE = LocalDate.of(2026, 7, 28);
  private static final OffsetDateTime SAMPLE_DATE_TIME =
      OffsetDateTime.of(2026, 7, 28, 12, 34, 56, 0, ZoneOffset.ofHours(1));

  /**
   * Renders offset date-times with the seconds field always present ({@code ISO_OFFSET_DATE_TIME}
   * omits {@code :00} seconds, which would reject the ubiquitous {@code 12:00:00+01:00} wire form
   * as non-canonical); fractional seconds render only when present.
   */
  private static final DateTimeFormatter OFFSET_DATE_TIME_RENDER =
      new DateTimeFormatterBuilder()
          .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
          .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
          .appendOffsetId()
          .toFormatter(Locale.ROOT);

  private static final ValidatedPrism<String, UUID> UUID_CODEC =
      codec(
          "not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000)",
          UUID::fromString,
          UUID::toString);

  private static final ValidatedPrism<String, URI> URI_CODEC =
      codec("not a URI (expected e.g. https://example.org/orders/42)", URI::create, URI::toString);

  private static final ValidatedPrism<String, LocalDate> LOCAL_DATE_CODEC =
      codec(
          "not an ISO-8601 date (expected e.g. 2026-07-28)", LocalDate::parse, LocalDate::toString);

  private static final ValidatedPrism<String, Instant> INSTANT_CODEC =
      codec(
          "not an ISO-8601 instant (expected e.g. 2026-07-28T12:34:56Z)",
          Instant::parse,
          Instant::toString);

  private static final ValidatedPrism<String, OffsetDateTime> OFFSET_DATE_TIME_CODEC =
      codec(
          "not an ISO-8601 date-time with offset (expected e.g. 2026-07-28T12:34:56+01:00)",
          OffsetDateTime::parse,
          OFFSET_DATE_TIME_RENDER::format);

  private static final ValidatedPrism<String, BigDecimal> BIG_DECIMAL_CODEC =
      codec(
          "not a number in plain notation (expected e.g. 123.45)",
          source -> {
            BigDecimal value = new BigDecimal(source);
            // Plain notation always parses to 0 <= scale <= source length, so a value outside
            // that band cannot equal its plain rendering - and must not reach it: toPlainString
            // materialises the full plain form, which a 14-character exponent spelling
            // ("1E+2147483647") would grow past the maximum String size.
            if (value.scale() < 0 || value.scale() > source.length()) {
              throw new IllegalArgumentException(source);
            }
            return value;
          },
          BigDecimal::toPlainString);

  private static final ValidatedPrism<String, Integer> INT_CODEC =
      codec("not a 32-bit integer (expected e.g. 42)", Integer::parseInt, String::valueOf);

  private static final ValidatedPrism<String, Long> LONG_CODEC =
      codec("not a 64-bit integer (expected e.g. 42)", Long::parseLong, String::valueOf);

  private static final ValidatedPrism<String, Double> DOUBLE_CODEC =
      codec(
          "not a double in canonical form (expected e.g. 3.14)",
          Double::parseDouble,
          String::valueOf);

  private static final ValidatedPrism<String, Boolean> BOOLEAN_CODEC =
      codec(
          "not a boolean (expected true or false)",
          source -> {
            // An equals chain, not a String switch: a String switch compiles to a hashCode
            // dispatch whose hash-collision equals branches are unreachable here.
            if (source.equals("true")) {
              return Boolean.TRUE;
            }
            if (source.equals("false")) {
              return Boolean.FALSE;
            }
            throw new IllegalArgumentException(source);
          },
          String::valueOf);

  private static final ValidatedPrism<String, Currency> CURRENCY_CODEC =
      codec(
          "not an ISO 4217 currency code (expected e.g. GBP)",
          Currency::getInstance,
          Currency::getCurrencyCode);

  private static final ValidatedPrism<String, Locale> LOCALE_CODEC =
      codec(
          "not a BCP 47 language tag (expected e.g. en-GB)",
          source -> new Locale.Builder().setLanguageTag(source).build(),
          Locale::toLanguageTag);

  /**
   * Canonical {@link UUID}s: lowercase hex, as {@code UUID.toString} renders. An uppercase or
   * mixed-case UUID is a rejection, not a normalisation.
   *
   * @return the cached codec (non-null)
   */
  public static ValidatedPrism<String, UUID> uuid() {
    return UUID_CODEC;
  }

  /**
   * {@link URI}s by RFC 2396 syntax; the original text is preserved, so every accepted URI
   * round-trips exactly. Any syntactically valid reference is accepted — relative references and
   * the empty string included — so this is syntax acceptance, not URL validation; a boundary
   * needing absolute-URL semantics wants a hand-written leaf.
   *
   * @return the cached codec (non-null)
   */
  public static ValidatedPrism<String, URI> uri() {
    return URI_CODEC;
  }

  /**
   * ISO-8601 dates ({@code 2026-07-28}).
   *
   * @return the cached codec (non-null)
   */
  public static ValidatedPrism<String, LocalDate> localDate() {
    return LOCAL_DATE_CODEC;
  }

  /**
   * Dates in the given format; the error message shows a sample date rendered with it.
   *
   * <p>The formatter must render what it parses for its values to be accepted ({@code d/M/yyyy}
   * parses {@code 01/07/2026} but renders {@code 1/7/2026}, so the zero-padded form would be
   * rejected — use {@code dd/MM/yyyy} for zero-padded wires). It must also be injective on the
   * values it parses: with a two-digit-year pattern ({@code yy/MM/dd}) the guard cannot object —
   * {@code build} renders {@code 1926-07-28} as {@code 26/07/28}, which silently re-parses to
   * {@code 2026-07-28}. The eager sample format below catches a formatter that can render nothing;
   * one that fails only on particular values yields located rejections for those values.
   *
   * @param formatter the wire format; must not be null and must be able to format a {@link
   *     LocalDate}
   * @return the codec (non-null)
   * @throws NullPointerException if {@code formatter} is null
   * @throws java.time.DateTimeException if {@code formatter} cannot format a date
   */
  public static ValidatedPrism<String, LocalDate> localDate(DateTimeFormatter formatter) {
    Objects.requireNonNull(formatter, "formatter must not be null");
    String example = formatter.format(SAMPLE_DATE);
    return codec(
        "not a date (expected e.g. " + example + ")",
        source -> LocalDate.parse(source, formatter),
        formatter::format);
  }

  /**
   * ISO-8601 instants in UTC ({@code 2026-07-28T12:34:56Z}), fractional seconds in the three-digit
   * groups {@code Instant.toString} renders ({@code .500Z} parses; {@code .5Z} and {@code .000Z}
   * are rejections). A non-zero offset form belongs to {@link #offsetDateTime()}; a producer
   * spelling UTC as {@code +00:00} needs {@link #offsetDateTime(DateTimeFormatter)} with an {@code
   * xxx} offset pattern, because {@code Instant.parse} would normalise the offset away and the
   * canonical guard rejects what does not render back.
   *
   * @return the cached codec (non-null)
   */
  public static ValidatedPrism<String, Instant> instant() {
    return INSTANT_CODEC;
  }

  /**
   * ISO-8601 date-times with offset ({@code 2026-07-28T12:34:56+01:00}): seconds always present, a
   * zero offset spelled {@code Z} ({@code +00:00} is a rejection — it parses to UTC, which renders
   * back as {@code Z}), and fractional seconds only when present, without trailing zeros ({@code
   * .5Z} parses; {@code .000Z} and {@code .50Z} are rejections). A producer emitting fixed
   * three-digit milliseconds (JavaScript's {@code toISOString}) or {@code +00:00} offsets needs the
   * {@link #offsetDateTime(DateTimeFormatter)} overload: {@code
   * DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX")} accepts the former, an {@code xxx}
   * offset pattern the latter.
   *
   * @return the cached codec (non-null)
   */
  public static ValidatedPrism<String, OffsetDateTime> offsetDateTime() {
    return OFFSET_DATE_TIME_CODEC;
  }

  /**
   * Date-times in the given format; the error message shows a sample rendered with it. The
   * formatter must render what it parses and be injective on the values it parses, exactly as for
   * {@link #localDate(DateTimeFormatter)}.
   *
   * @param formatter the wire format; must not be null and must be able to format an {@link
   *     OffsetDateTime}
   * @return the codec (non-null)
   * @throws NullPointerException if {@code formatter} is null
   * @throws java.time.DateTimeException if {@code formatter} cannot format an offset date-time
   */
  public static ValidatedPrism<String, OffsetDateTime> offsetDateTime(DateTimeFormatter formatter) {
    Objects.requireNonNull(formatter, "formatter must not be null");
    String example = formatter.format(SAMPLE_DATE_TIME);
    return codec(
        "not a date-time (expected e.g. " + example + ")",
        source -> OffsetDateTime.parse(source, formatter),
        formatter::format);
  }

  /**
   * Enum constants by exact {@link Enum#name() name}; the error message lists the permitted
   * constants. For an enum with very many constants (country or currency scale) that listing is
   * rendered into every failure — a hand-written leaf with a shorter message may serve a 422
   * payload better there.
   *
   * @param enumType the enum class; must not be null
   * @param <E> the enum type
   * @return the codec (non-null)
   * @throws NullPointerException if {@code enumType} is null
   * @throws IllegalArgumentException if no enum constants are available: a raw-cast non-enum class
   *     or the class of a constant declared with a body (both have none), or an enum declaring no
   *     constants (nothing could ever parse)
   */
  public static <E extends Enum<E>> ValidatedPrism<String, E> enumByName(Class<E> enumType) {
    Objects.requireNonNull(enumType, "enumType must not be null");
    E[] constants = enumType.getEnumConstants();
    if (constants == null) {
      throw new IllegalArgumentException(enumType.getName() + " is not an enum type");
    }
    if (constants.length == 0) {
      throw new IllegalArgumentException(
          enumType.getName() + " has no constants, so nothing could ever parse");
    }
    String permitted = Arrays.stream(constants).map(Enum::name).collect(Collectors.joining(", "));
    return codec(
        "unknown " + enumType.getSimpleName() + " (expected one of " + permitted + ")",
        source -> Enum.valueOf(enumType, source),
        Enum::name);
  }

  /**
   * Plain-notation decimal numbers ({@code 123.45}), scale preserved ({@code 0.010} stays {@code
   * 0.010}); scientific notation is rejected as non-canonical, before rendering, so an astronomical
   * exponent cannot cost memory.
   *
   * <p>{@code parse} only ever produces plain-form values (scale {@code >= 0}), and the parse-build
   * law holds on exactly those. A negative-scale domain value (for example a {@code
   * stripTrailingZeros()} result such as {@code 1E+5}) still renders totally through {@code build},
   * but its rendering re-parses to the plain-form value, equal by {@code compareTo} rather than
   * {@code equals} — normalise with {@code setScale} before building if that distinction matters.
   *
   * @return the cached codec (non-null)
   */
  public static ValidatedPrism<String, BigDecimal> bigDecimal() {
    return BIG_DECIMAL_CODEC;
  }

  /**
   * Canonical base-10 integers ({@code 42}, {@code -7}); a leading {@code +} or leading zeros are
   * rejections, not normalisations.
   *
   * @return the cached codec (non-null)
   */
  public static ValidatedPrism<String, Integer> intFromString() {
    return INT_CODEC;
  }

  /**
   * Canonical base-10 long integers, exactly as {@link #intFromString()} with a wider range.
   *
   * @return the cached codec (non-null)
   */
  public static ValidatedPrism<String, Long> longFromString() {
    return LONG_CODEC;
  }

  /**
   * Doubles in the canonical form {@code Double.toString} renders ({@code 3.14}, {@code 1.0E10},
   * {@code NaN}, {@code Infinity}); other spellings of the same value are rejected as
   * non-canonical.
   *
   * @return the cached codec (non-null)
   */
  public static ValidatedPrism<String, Double> doubleFromString() {
    return DOUBLE_CODEC;
  }

  /**
   * Exactly {@code true} or {@code false}; anything else (case variants included) is a located
   * rejection — the lenient {@code Boolean.parseBoolean}, which reads every non-{@code true} string
   * as {@code false}, has no place at a validated boundary.
   *
   * @return the cached codec (non-null)
   */
  public static ValidatedPrism<String, Boolean> booleanStrict() {
    return BOOLEAN_CODEC;
  }

  /**
   * ISO 4217 currency codes ({@code GBP}), uppercase as the standard defines them.
   *
   * @return the cached codec (non-null)
   */
  public static ValidatedPrism<String, Currency> currency() {
    return CURRENCY_CODEC;
  }

  /**
   * BCP 47 language tags in canonical case ({@code en-GB}); an ill-formed or case-folded tag is a
   * rejection, not a normalisation.
   *
   * @return the cached codec (non-null)
   */
  public static ValidatedPrism<String, Locale> locale() {
    return LOCALE_CODEC;
  }

  /**
   * The per-value section-law guard, by delegation to the public {@link
   * ValidatedPrism#canonical(String, Function, Function)} factory — the pattern has exactly one
   * implementation, and a user codec gets it from the same place.
   */
  private static <A> ValidatedPrism<String, A> codec(
      String message, Function<String, A> parse, Function<A, String> render) {
    return ValidatedPrism.canonical(message, parse, render);
  }
}
