// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.validated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;

/**
 * The parse half of a {@link ValidatedPrism}: a fallible, accumulating conversion from a source
 * into a domain value, with no way back.
 *
 * <p>{@link #parse} reports <em>every</em> reason a source is rejected, as located {@link
 * FieldError}s on the {@link NonEmptyList} channel, and the bulk forms lift it over whole
 * containers with the same accumulation. A {@code ValidatedPrism} is a {@code ValidatedParse}, so
 * every prism serves wherever only parsing is asked for. A value with no {@code build} at all, the
 * read side of a wire the domain is never written back to, is made with {@link #of}, or exposed by
 * a parse-only {@code @GenerateMapping} as {@code asValidatedParse()}.
 *
 * <p>With no {@code build} there is no round trip, so the prism's two laws have nothing to relate.
 * What remains is the parse contract itself: a source that parses is {@code Valid}, and one that
 * does not reports every failure it has.
 *
 * @param <S> the source (wire) type
 * @param <A> the domain type
 */
public sealed interface ValidatedParse<S, A> permits ValidatedPrism, ValidatedParse.Of {

  /**
   * Parses the source into the domain type, reporting every reason it cannot.
   *
   * @param source the wire value; must not be null
   * @return {@code Valid(a)} or every located failure (non-null)
   */
  Validated<NonEmptyList<FieldError>, A> parse(S source);

  /**
   * Parses onto the railway: the {@link ValidationPath} twin of {@link #parse}.
   *
   * @param source the wire value; must not be null
   * @return the parse outcome as a {@code ValidationPath} (non-null)
   */
  default ValidationPath<NonEmptyList<FieldError>, A> parsePath(S source) {
    return Path.validatedNel(parse(source));
  }

  /**
   * Parses every element, accumulating all failures across the whole list, each located by its
   * index: a failing element at position 1 under a field labelled {@code emails} renders as {@code
   * emails.1: ...} — a plain positional segment, matching {@link #parseValues(Map)}' key segments.
   * A null element is a located {@code must not be null} at its index, never an exception,
   * completing the null doctrine inside containers.
   *
   * @param sources the wire values; the list itself must not be null
   * @return {@code Valid(list)} or every failure from every element, located by index, in list
   *     order (non-null)
   * @throws NullPointerException if {@code sources} is null
   */
  default Validated<NonEmptyList<FieldError>, List<A>> parseAll(List<? extends S> sources) {
    Objects.requireNonNull(sources, "sources must not be null");
    List<A> values = new ArrayList<>(sources.size());
    NonEmptyList<FieldError> failures = null;
    int i = 0;
    for (S source : sources) {
      int index = i++;
      NonEmptyList<FieldError> located;
      if (source == null) {
        located = NonEmptyList.of(FieldError.of("must not be null").at(String.valueOf(index)));
      } else {
        Validated<NonEmptyList<FieldError>, A> parsed = parse(source);
        if (parsed.isValid()) {
          values.add(parsed.get());
          continue;
        }
        located = parsed.getError().map(err -> err.at(String.valueOf(index)));
      }
      failures = accumulate(failures, located);
    }
    return failures == null ? Validated.valid(List.copyOf(values)) : Validated.invalid(failures);
  }

  /**
   * Parses every element of a set, accumulating all failures across the whole set, each located by
   * the failing <em>source element's</em> rendering: a bad {@code "nope"} under a field labelled
   * {@code emails} renders as {@code emails.nope: not an email address}. A set has no index, so the
   * thing that identifies an element is its value — the same choice {@link #parseValues(Map)} makes
   * for keys, and it carries the same caveat: elements are located via {@code toString()}, so in
   * the RENDERED {@code pathString} an element containing a dot is indistinguishable from deeper
   * nesting, while the structured {@link FieldError#path()} keeps the whole rendering as one
   * segment. Elements whose {@code toString()} collides share a rendered location; every failure is
   * still reported.
   *
   * <p>A {@code null} element has no rendering to locate by, and a set holds at most one, so it is
   * an <em>unlocated</em> {@code must not contain a null element} under the component's own label —
   * distinct from {@code must not be null}, which says the set itself is absent.
   *
   * <p>Mapping a set can <b>collapse</b> it: two sources that parse to equal domain values leave
   * one element, and the two source spellings ({@code "1"} and {@code "01"}, say) can no longer be
   * told apart. That needs a non-injective parse, which already breaks the section law a {@link
   * ValidatedPrism} documents, so no lawful prism collapses. Where one does, the duplicate is
   * dropped <em>silently</em>: the element that remains is equal to the one dropped, so the set
   * still holds every distinct value it was given. Iteration order is preserved.
   *
   * @param sources the wire values; the set itself must not be null
   * @return {@code Valid(set)} or every failure from every element, in iteration order (non-null,
   *     immutable)
   * @throws NullPointerException if {@code sources} is null
   */
  default Validated<NonEmptyList<FieldError>, Set<A>> parseAll(Set<? extends S> sources) {
    Objects.requireNonNull(sources, "sources must not be null");
    Set<A> values = LinkedHashSet.newLinkedHashSet(sources.size());
    NonEmptyList<FieldError> failures = null;
    for (S source : sources) {
      NonEmptyList<FieldError> located;
      if (source == null) {
        located = NonEmptyList.of(FieldError.of("must not contain a null element"));
      } else {
        Validated<NonEmptyList<FieldError>, A> parsed = parse(source);
        if (parsed.isValid()) {
          values.add(parsed.get());
          continue;
        }
        located = parsed.getError().map(err -> err.at(source.toString()));
      }
      failures = accumulate(failures, located);
    }
    return failures == null
        ? Validated.valid(Collections.unmodifiableSet(values))
        : Validated.invalid(failures);
  }

  /**
   * Parses every element of an array, accumulating all failures, each located by its index exactly
   * as {@link #parseAll(List)} locates a list's. A {@code null} element is a located {@code must
   * not be null} at its index.
   *
   * <p>{@code newArray} supplies the result array — a generic array cannot be created otherwise —
   * and is called once, with the source's length, in the mould of {@code Stream#toArray}.
   *
   * @param sources the wire values; the array itself must not be null
   * @param newArray the domain array constructor, typically {@code Domain[]::new}; must not be null
   * @return {@code Valid(array)} or every failure from every element, located by index, in array
   *     order (non-null)
   * @throws NullPointerException if {@code sources} or {@code newArray} is null
   */
  default Validated<NonEmptyList<FieldError>, A[]> parseAll(
      S[] sources, IntFunction<A[]> newArray) {
    Objects.requireNonNull(sources, "sources must not be null");
    Objects.requireNonNull(newArray, "newArray must not be null");
    A[] values = newArray.apply(sources.length);
    NonEmptyList<FieldError> failures = null;
    for (int i = 0; i < sources.length; i++) {
      S source = sources[i];
      String index = String.valueOf(i);
      NonEmptyList<FieldError> located;
      if (source == null) {
        located = NonEmptyList.of(FieldError.of("must not be null").at(index));
      } else {
        Validated<NonEmptyList<FieldError>, A> parsed = parse(source);
        if (parsed.isValid()) {
          values[i] = parsed.get();
          continue;
        }
        located = parsed.getError().map(err -> err.at(index));
      }
      failures = accumulate(failures, located);
    }
    return failures == null ? Validated.valid(values) : Validated.invalid(failures);
  }

  /**
   * Parses every value of a map, accumulating all failures across the whole map.
   *
   * <p>Keys pass through untouched - only the values are parsed. Each entry's failures are located
   * by its key (prepended as a path segment via {@link FieldError#at}), so an outer {@code
   * field(label, ...)} call composes to dotted paths such as {@code attributes.en.email}. Entry
   * order is preserved.
   *
   * <p>Keys are located via {@code toString()}. In the RENDERED {@code pathString} a key containing
   * a dot is therefore indistinguishable from deeper nesting ({@code attributes.en.gb} could be key
   * {@code en.gb} or key {@code en} nesting {@code gb}); the structured {@link FieldError#path()}
   * list stays exact, with the whole key as one segment. Distinct keys whose {@code toString()}
   * collide share a rendered location, but every error is still reported.
   *
   * @param sources the wire values by key; neither the map nor its keys may be null — a null
   *     <em>value</em> is a located {@code must not be null} under its key, never an exception,
   *     completing the null doctrine inside containers
   * @param <K> the key type, carried through unchanged
   * @return {@code Valid(map)} or every located failure from every entry, in entry order (non-null,
   *     immutable)
   * @throws NullPointerException if {@code sources} or one of its keys is null (a null key is a
   *     structurally broken map, not a wrong value)
   */
  default <K> Validated<NonEmptyList<FieldError>, Map<K, A>> parseValues(
      Map<K, ? extends S> sources) {
    Objects.requireNonNull(sources, "sources must not be null");
    Map<K, A> values = LinkedHashMap.newLinkedHashMap(sources.size());
    NonEmptyList<FieldError> failures = null;
    for (Map.Entry<K, ? extends S> entry : sources.entrySet()) {
      K key = Objects.requireNonNull(entry.getKey(), "sources must not contain a null key");
      S source = entry.getValue();
      NonEmptyList<FieldError> located;
      if (source == null) {
        located = NonEmptyList.of(FieldError.of("must not be null").at(key.toString()));
      } else {
        Validated<NonEmptyList<FieldError>, A> parsed = parse(source);
        if (parsed.isValid()) {
          values.put(key, parsed.get());
          continue;
        }
        located = parsed.getError().map(err -> err.at(key.toString()));
      }
      failures = accumulate(failures, located);
    }
    // Map.copyOf does not preserve entry order, so wrap the LinkedHashMap instead.
    return failures == null
        ? Validated.valid(Collections.unmodifiableMap(values))
        : Validated.invalid(failures);
  }

  /**
   * Parses every <em>key</em> of a map, accumulating all failures across the whole map. The mirror
   * of {@link #parseValues(Map)}: values pass through untouched, and each failure is located by the
   * failing <em>source</em> key's rendering, so a bad key {@code en_GB} under a field labelled
   * {@code attributes} renders as {@code attributes.en_GB: not a locale}. Locating by the source
   * key (not the parsed one) means the location names what the caller sent, and it carries the same
   * {@code toString()} caveat {@link #parseValues(Map)} documents. Entry order is preserved.
   *
   * <p>Two source keys that parse to <b>equal</b> domain keys are a located failure, not a silent
   * collapse: a map holds one entry per key, so the second entry would be dropped along with its
   * value. A key counts as claimed as soon as it parses, even where the rest of its entry is wrong,
   * so a collision is reported in the same pass as the other failures rather than surfacing only
   * after they are fixed.
   *
   * <p>Values pass through, but a {@code null} one is still a located {@code must not be null}
   * under its source key — the null doctrine reaches inside a container whether that container's
   * contents convert or are copied.
   *
   * @param sources the wire values by key; neither the map nor its keys may be null
   * @param <V> the value type, carried through unchanged
   * @return {@code Valid(map)} or every located failure, in entry order (non-null, immutable)
   * @throws NullPointerException if {@code sources} or one of its keys is null (a null key is a
   *     structurally broken map, not a wrong value)
   */
  default <V> Validated<NonEmptyList<FieldError>, Map<A, V>> parseKeys(
      Map<? extends S, V> sources) {
    Objects.requireNonNull(sources, "sources must not be null");
    Map<A, V> values = LinkedHashMap.newLinkedHashMap(sources.size());
    Set<A> claimed = LinkedHashSet.newLinkedHashSet(sources.size());
    NonEmptyList<FieldError> failures = null;
    for (Map.Entry<? extends S, V> entry : sources.entrySet()) {
      S source = Objects.requireNonNull(entry.getKey(), "sources must not contain a null key");
      V value = entry.getValue();
      Validated<NonEmptyList<FieldError>, A> parsedKey = parse(source);
      NonEmptyList<FieldError> located = null;
      if (!parsedKey.isValid()) {
        located = parsedKey.getError().map(err -> err.at(source.toString()));
      } else if (!claimed.add(parsedKey.get())) {
        located = NonEmptyList.of(collision(source));
      }
      if (value == null) {
        located =
            accumulate(
                located, NonEmptyList.of(FieldError.of("must not be null").at(source.toString())));
      }
      if (located == null) {
        values.put(parsedKey.get(), value);
      } else {
        failures = accumulate(failures, located);
      }
    }
    return failures == null
        ? Validated.valid(Collections.unmodifiableMap(values))
        : Validated.invalid(failures);
  }

  /**
   * Parses both sides of a map, accumulating every key <em>and</em> value failure across the whole
   * map. The receiver parses the <b>keys</b> and {@code valueParse} the values, matching the order
   * a {@code Map<K, V>} reads in; where only one side converts, {@link #parseKeys(Map)} and {@link
   * #parseValues(Map)} say so directly. Only the parse direction of either side is used, so any
   * {@code ValidatedPrism} serves as {@code valueParse} too.
   *
   * <p>Both a failing key and a failing value locate under the <em>source</em> key ({@code
   * attributes.en_GB}), so an entry that is wrong on both sides reports both reasons at that one
   * location. A {@code null} value is a located {@code must not be null}, and colliding domain keys
   * are a located failure, exactly as in {@link #parseKeys(Map)}. Entry order is preserved.
   *
   * @param sources the wire entries; neither the map nor its keys may be null
   * @param valueParse the parse converting the values; must not be null
   * @param <SV> the wire value type
   * @param <V> the domain value type
   * @return {@code Valid(map)} or every located failure, in entry order (non-null, immutable)
   * @throws NullPointerException if {@code sources}, one of its keys, or {@code valueParse} is null
   */
  default <SV, V> Validated<NonEmptyList<FieldError>, Map<A, V>> parseEntries(
      Map<? extends S, ? extends SV> sources, ValidatedParse<SV, V> valueParse) {
    Objects.requireNonNull(sources, "sources must not be null");
    Objects.requireNonNull(valueParse, "valueParse must not be null");
    Map<A, V> values = LinkedHashMap.newLinkedHashMap(sources.size());
    Set<A> claimed = LinkedHashSet.newLinkedHashSet(sources.size());
    NonEmptyList<FieldError> failures = null;
    for (Map.Entry<? extends S, ? extends SV> entry : sources.entrySet()) {
      S source = Objects.requireNonNull(entry.getKey(), "sources must not contain a null key");
      SV rawValue = entry.getValue();
      Validated<NonEmptyList<FieldError>, A> parsedKey = parse(source);
      Validated<NonEmptyList<FieldError>, V> parsedValue =
          rawValue == null
              ? Validated.invalidNel(FieldError.of("must not be null"))
              : valueParse.parse(rawValue);
      NonEmptyList<FieldError> located = null;
      if (!parsedKey.isValid()) {
        located = parsedKey.getError().map(err -> err.at(source.toString()));
      } else if (!claimed.add(parsedKey.get())) {
        located = NonEmptyList.of(collision(source));
      }
      if (!parsedValue.isValid()) {
        located = accumulate(located, parsedValue.getError().map(err -> err.at(source.toString())));
      }
      if (located == null) {
        values.put(parsedKey.get(), parsedValue.get());
      } else {
        failures = accumulate(failures, located);
      }
    }
    return failures == null
        ? Validated.valid(Collections.unmodifiableMap(values))
        : Validated.invalid(failures);
  }

  /**
   * The collision failure for a domain key an earlier entry already claimed, located by the source
   * key that produced it.
   *
   * <p>A key is claimed as soon as it <em>parses</em>, not once its whole entry is valid: an entry
   * whose value is also wrong would otherwise reserve nothing, hiding the collision until the value
   * was fixed and the map resubmitted - two round trips where accumulation promises one.
   */
  private static FieldError collision(Object sourceKey) {
    return FieldError.of("duplicates an earlier key").at(sourceKey.toString());
  }

  /** Folds one entry's located failures into the accumulated ones; either side may be null. */
  private static NonEmptyList<FieldError> accumulate(
      NonEmptyList<FieldError> failures, NonEmptyList<FieldError> located) {
    return failures == null
        ? located
        : NonEmptyList.<FieldError>semigroup().combine(failures, located);
  }

  /**
   * Creates a {@code ValidatedParse} from its one direction, for a source the domain is never
   * rendered back into.
   *
   * @param parse the fallible, accumulating direction; must not be null
   * @param <S> the source type
   * @param <A> the domain type
   * @return the parse (non-null)
   * @throws NullPointerException if {@code parse} is null
   */
  static <S, A> ValidatedParse<S, A> of(
      Function<? super S, Validated<NonEmptyList<FieldError>, A>> parse) {
    Objects.requireNonNull(parse, "parse must not be null");
    return new Of<>(parse);
  }

  /**
   * The one-directional implementation wrapping a parse function.
   *
   * <p>Created by {@link ValidatedParse#of}; not usually named directly.
   *
   * @param parseFn the parse direction; never null
   * @param <S> the source type
   * @param <A> the domain type
   */
  record Of<S, A>(Function<? super S, Validated<NonEmptyList<FieldError>, A>> parseFn)
      implements ValidatedParse<S, A> {

    /**
     * Canonical constructor; validates.
     *
     * @throws NullPointerException if {@code parseFn} is null
     */
    public Of {
      Objects.requireNonNull(parseFn, "parseFn must not be null");
    }

    @Override
    public Validated<NonEmptyList<FieldError>, A> parse(S source) {
      Objects.requireNonNull(source, "source must not be null");
      return Objects.requireNonNull(parseFn.apply(source), "parse must not return null");
    }
  }
}
