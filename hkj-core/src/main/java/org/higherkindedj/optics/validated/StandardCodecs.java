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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
 * leading zeros, scientific notation and lowercase language tags are rejections, never silent
 * normalisations. The outbound {@code build} direction is total formatting, matching the leaf
 * contract.
 *
 * <p><b>Located, copy-worthy errors.</b> Every parse failure is a single {@link FieldError} whose
 * message is terse and actionable ({@code not an ISO-8601 date (expected e.g. 2026-07-28)}); under
 * a mapping it arrives located by component name, so the codecs feed the 422 leg unchanged. Enum
 * failures name the permitted constants.
 *
 * <p><b>Rejections without exceptions.</b> A codec checks a source's shape before handing it to a
 * JDK parser, so a malformed value is rejected without the exception, and the stack trace, the
 * parser would throw. A value in the right shape but out of range, such as an integer past 32 bits,
 * still reaches the parser, and the codec catches what it throws. {@link #uri()}, {@link #locale()}
 * and the formatter overloads have no such check, since neither those grammars nor a custom pattern
 * has a shape that is cheap to test.
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
 * #enumByName(Class)}) construct per call. Either sits in a spec's {@code default} method, since a
 * generated Impl reads each leaf once and keeps it. A custom formatter must render what it parses
 * (the section law is enforced per value: a value whose rendering differs from its source is
 * rejected), and must be able to format the temporal type — the factory formats a sample eagerly,
 * so a formatter that can render nothing fails at construction; one that fails only on particular
 * values yields located rejections for those values.
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
          source -> uuidShaped(source) ? UUID.fromString(source) : null,
          UUID::toString);

  private static final ValidatedPrism<String, URI> URI_CODEC =
      codec("not a URI (expected e.g. https://example.org/orders/42)", URI::create, URI::toString);

  private static final ValidatedPrism<String, LocalDate> LOCAL_DATE_CODEC =
      codec(
          "not an ISO-8601 date (expected e.g. 2026-07-28)",
          source -> isoShaped(source, "0000-00-00", true) ? LocalDate.parse(source) : null,
          LocalDate::toString);

  private static final ValidatedPrism<String, Instant> INSTANT_CODEC =
      codec(
          "not an ISO-8601 instant (expected e.g. 2026-07-28T12:34:56Z)",
          source -> isoShaped(source, "0000-00-00T00:00:00", false) ? Instant.parse(source) : null,
          Instant::toString);

  private static final ValidatedPrism<String, OffsetDateTime> OFFSET_DATE_TIME_CODEC =
      codec(
          "not an ISO-8601 date-time with offset (expected e.g. 2026-07-28T12:34:56+01:00)",
          source ->
              isoShaped(source, "0000-00-00T00:00:00", false) ? OffsetDateTime.parse(source) : null,
          OFFSET_DATE_TIME_RENDER::format);

  private static final ValidatedPrism<String, BigDecimal> BIG_DECIMAL_CODEC =
      codec(
          "not a number in plain notation (expected e.g. 123.45)",
          // Plain notation only, so an exponent spelling never reaches the render: toPlainString
          // materialises the full plain form, which a 14-character spelling such as
          // "1E+2147483647" would grow past the maximum String size.
          source -> plainDecimalShaped(source) ? new BigDecimal(source) : null,
          BigDecimal::toPlainString);

  private static final ValidatedPrism<String, Integer> INT_CODEC =
      codec(
          "not a 32-bit integer (expected e.g. 42)",
          source -> integerShaped(source, 10) ? Integer.valueOf(source) : null,
          String::valueOf);

  private static final ValidatedPrism<String, Long> LONG_CODEC =
      codec(
          "not a 64-bit integer (expected e.g. 42)",
          source -> integerShaped(source, 19) ? Long.valueOf(source) : null,
          String::valueOf);

  private static final ValidatedPrism<String, Double> DOUBLE_CODEC =
      codec(
          "not a double in canonical form (expected e.g. 3.14)",
          source -> doubleShaped(source) ? Double.valueOf(source) : null,
          String::valueOf);

  private static final ValidatedPrism<String, Boolean> BOOLEAN_CODEC =
      codec(
          "not a boolean (expected true or false)",
          source ->
              source.equals("true") || source.equals("false") ? Boolean.valueOf(source) : null,
          String::valueOf);

  private static final ValidatedPrism<String, Currency> CURRENCY_CODEC =
      codec(
          "not an ISO 4217 currency code (expected e.g. GBP)",
          // Three capital letters, the shape of every code; an unknown code still throws.
          source -> currencyShaped(source) ? Currency.getInstance(source) : null,
          Currency::getCurrencyCode);

  private static final ValidatedPrism<String, Locale> LOCALE_CODEC =
      codec(
          "not a BCP 47 language tag (expected e.g. en-GB)",
          source -> new Locale.Builder().setLanguageTag(source).build(),
          Locale::toLanguageTag);

  /**
   * Each enum type's failure message, computed once. {@link #enumByName} constructs a codec on each
   * call, so without this every call would copy the constants and join their names. An invalid type
   * throws from here, uncached, on every attempt. It holds the message, not the codec: an entry
   * lives on the enum's {@code Class}, and a {@code String} reaches nothing of this library, so an
   * enum from a parent loader (a JDK enum such as {@code DayOfWeek}) cannot keep this library's
   * loader alive.
   */
  private static final ClassValue<String> ENUM_MESSAGES =
      new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> enumType) {
          Object[] constants = enumType.getEnumConstants();
          if (constants == null) {
            throw new IllegalArgumentException(enumType.getName() + " is not an enum type");
          }
          if (constants.length == 0) {
            throw new IllegalArgumentException(
                enumType.getName() + " has no constants, so nothing could ever parse");
          }
          String permitted =
              Arrays.stream(constants)
                  .map(constant -> ((Enum<?>) constant).name())
                  .collect(Collectors.joining(", "));
          return "unknown " + enumType.getSimpleName() + " (expected one of " + permitted + ")";
        }
      };

  /**
   * Each enum type's constants by name, so a codec finds a name, or its absence, without {@code
   * Enum.valueOf}, which throws on an unknown one. Read only after {@link #ENUM_MESSAGES} has
   * accepted the type. Like that message, the map reaches only the enum's own constants and JDK
   * types, never this library.
   */
  private static final ClassValue<Map<String, Object>> ENUM_CONSTANTS =
      new ClassValue<>() {
        @Override
        protected Map<String, Object> computeValue(Class<?> enumType) {
          Map<String, Object> byName = new HashMap<>();
          for (Object constant : enumType.getEnumConstants()) {
            byName.put(((Enum<?>) constant).name(), constant);
          }
          return Map.copyOf(byName);
        }
      };

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
   * are rejections). A non-zero offset form belongs to {@link #offsetDateTime()}. A producer with
   * another canon needs a {@link ValidatedPrism#canonical} leaf that renders it: a browser's {@code
   * toISOString()} writes {@code .000Z} for whole seconds, which this codec rejects, and Python's
   * {@code isoformat()} writes {@code +00:00} with six fraction digits, or none when the
   * microseconds are zero, so no single pattern describes it.
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
   * three-digit milliseconds (JavaScript's {@code toISOString}) needs the {@link
   * #offsetDateTime(DateTimeFormatter)} overload with {@code
   * DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX")}. An {@code xxx} offset pattern
   * serves a {@code +00:00} producer only if it never writes a fraction: Python's {@code
   * isoformat()} writes six digits unless the microseconds are zero, so it needs a {@link
   * ValidatedPrism#canonical} leaf that picks the pattern by value.
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
    String message = ENUM_MESSAGES.get(enumType);
    Map<String, Object> byName = ENUM_CONSTANTS.get(enumType);
    return codec(message, source -> enumType.cast(byName.get(source)), Enum::name);
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

  /**
   * The length and hyphens of the 8-4-4-4-12 form {@code UUID.toString} writes. It checks no digit,
   * so it costs next to nothing on a valid source; a bad digit in the right shape still throws, and
   * the guard catches it.
   */
  private static boolean uuidShaped(String source) {
    return source.length() == 36
        && source.charAt(8) == '-'
        && source.charAt(13) == '-'
        && source.charAt(18) == '-'
        && source.charAt(23) == '-';
  }

  /** Three capital letters, the shape of every ISO 4217 code. */
  private static boolean currencyShaped(String source) {
    return source.length() == 3
        && isCapital(source.charAt(0))
        && isCapital(source.charAt(1))
        && isCapital(source.charAt(2));
  }

  private static boolean isCapital(char c) {
    return c >= 'A' && c <= 'Z';
  }

  /**
   * An optional minus sign, then one to {@code maxDigits} ASCII digits. Leading zeros and {@code
   * -0} pass, for the canonical check to refuse; a value past the type's range passes too, and its
   * parse throws, which the guard catches.
   */
  private static boolean integerShaped(String source, int maxDigits) {
    int start = signEnd(source, 0);
    int digits = source.length() - start;
    return digits >= 1 && digits <= maxDigits && digitsEnd(source, start) == source.length();
  }

  /** Plain notation: an optional minus sign, digits, and optionally a point and more digits. */
  private static boolean plainDecimalShaped(String source) {
    int start = signEnd(source, 0);
    int integerEnd = digitsEnd(source, start);
    if (integerEnd == start) {
      return false;
    }
    if (integerEnd == source.length()) {
      return true;
    }
    return source.charAt(integerEnd) == '.' && fractionEnds(source, integerEnd);
  }

  /**
   * What {@code Double.toString} writes: {@code NaN}, an infinity, or digits, a point and digits,
   * optionally followed by {@code E} and a signed exponent.
   */
  private static boolean doubleShaped(String source) {
    if (source.equals("NaN") || source.equals("Infinity") || source.equals("-Infinity")) {
      return true;
    }
    int start = signEnd(source, 0);
    int integerEnd = digitsEnd(source, start);
    if (integerEnd == start || integerEnd == source.length() || source.charAt(integerEnd) != '.') {
      return false;
    }
    int fractionEnd = digitsEnd(source, integerEnd + 1);
    if (fractionEnd == integerEnd + 1) {
      return false;
    }
    if (fractionEnd == source.length()) {
      return true;
    }
    if (source.charAt(fractionEnd) != 'E') {
      return false;
    }
    int exponentStart = signEnd(source, fractionEnd + 1);
    int exponentEnd = digitsEnd(source, exponentStart);
    return exponentEnd > exponentStart && exponentEnd == source.length();
  }

  /** Whether the point at {@code point} is followed by one or more digits that end the source. */
  private static boolean fractionEnds(String source, int point) {
    int fractionEnd = digitsEnd(source, point + 1);
    return fractionEnd > point + 1 && fractionEnd == source.length();
  }

  /** The index after an optional minus sign at {@code from}. */
  private static int signEnd(String source, int from) {
    return from < source.length() && source.charAt(from) == '-' ? from + 1 : from;
  }

  /** The index after the run of ASCII digits starting at {@code from}. */
  private static int digitsEnd(String source, int from) {
    int i = from;
    while (i < source.length() && source.charAt(i) >= '0' && source.charAt(i) <= '9') {
      i++;
    }
    return i;
  }

  /**
   * The length and punctuation of an ISO form with a four-digit year, where {@code template} holds
   * {@code 0} for a digit, which is not checked, and any other character for itself: the whole
   * source when {@code exact}, otherwise a prefix with more to follow (a fraction, an offset, a
   * zone). A year outside four digits is written with a sign, so a source starting {@code +} or
   * {@code -} passes for the parse to judge.
   */
  private static boolean isoShaped(String source, String template, boolean exact) {
    if (!source.isEmpty() && (source.charAt(0) == '+' || source.charAt(0) == '-')) {
      return true;
    }
    int length = template.length();
    if (exact ? source.length() != length : source.length() <= length) {
      return false;
    }
    for (int i = 0; i < length; i++) {
      char expected = template.charAt(i);
      if (expected != '0' && source.charAt(i) != expected) {
        return false;
      }
    }
    return true;
  }
}
