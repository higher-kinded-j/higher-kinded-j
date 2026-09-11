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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;

/**
 * A {@link Prism} whose match accumulates reasons: the smart-constructor optic for
 * parse-don't-validate boundaries.
 *
 * <p>Where a {@code Prism}'s match answers yes/no ({@code Optional}), a {@code ValidatedPrism}'s
 * {@link #parse} says <em>why not</em> — and <em>all</em> the reasons at once, as located {@link
 * FieldError}s on the {@link NonEmptyList} channel. The other direction, {@link #build}, is
 * <b>total</b>: a valid domain value always renders. That asymmetry is the "parse, don't validate"
 * pattern made into an optic:
 *
 * <pre>{@code
 * ValidatedPrism<String, EmailAddress> email = ValidatedPrism.of(
 *     EmailAddress::parse,      // String -> Validated<NEL<FieldError>, EmailAddress>
 *     EmailAddress::value);     // EmailAddress -> String   (total)
 *
 * Validated<NonEmptyList<FieldError>, EmailAddress> parsed = email.parse("  NOPE ");
 * String rendered = email.build(addr);   // always succeeds
 * }</pre>
 *
 * <p><b>Composition.</b> {@code andThen} goes <em>deeper into structure</em> and therefore
 * <b>short-circuits</b> (you cannot parse the inner value if the outer parse failed) — the same
 * split {@code ValidationPath} draws between {@code via} (sequential) and sibling accumulation. To
 * accumulate <em>sibling</em> fields, feed per-field parses to {@code Validated.fields()} / {@code
 * accumulate()} or the {@code Edits} builder. Only compositions that preserve the total {@code
 * build} yield a {@code ValidatedPrism}: another {@code ValidatedPrism}, an {@link Iso}, or a
 * {@link Prism} given a reason for its empty case. Composing with a {@link Lens} cannot — a lens
 * needs a base to write into, so no total {@code B -> S} build exists; that weaker optic is
 * deliberately not provided here.
 *
 * <p><b>Laws</b> (verified via {@code ValidatedPrismLaws} in {@code hkj-test}):
 *
 * <ul>
 *   <li>parse-build: {@code parse(build(a)) == Valid(a)};
 *   <li>build-parse (section): {@code parse(s) == Valid(a)} implies {@code build(a) == s} — which
 *       forbids a lossy "parse-normalise" that breaks the round trip.
 * </ul>
 *
 * @param <S> the source (wire) type
 * @param <A> the focused (domain) type
 */
public sealed interface ValidatedPrism<S, A> permits ValidatedPrism.Of {

  /**
   * Parses the source into the domain type, reporting every reason it cannot.
   *
   * @param source the wire value; must not be null
   * @return {@code Valid(a)} or every located failure (non-null)
   */
  Validated<NonEmptyList<FieldError>, A> parse(S source);

  /**
   * Renders the domain value back to the source type; total by construction.
   *
   * @param value the domain value; must not be null
   * @return the rendered source value (non-null)
   */
  S build(A value);

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
   * Composes deeper: parse this, then parse the result — short-circuiting on the first failure.
   *
   * @param other the inner prism; must not be null
   * @param <B> the inner domain type
   * @return the composed prism (non-null)
   * @throws NullPointerException if {@code other} is null
   */
  default <B> ValidatedPrism<S, B> andThen(ValidatedPrism<A, B> other) {
    Objects.requireNonNull(other, "other must not be null");
    return of(s -> parse(s).flatMap(other::parse), b -> build(other.build(b)));
  }

  /**
   * Composes with an {@link Iso}: the parse maps through, the build round-trips back.
   *
   * @param iso the inner iso; must not be null
   * @param <B> the inner type
   * @return the composed prism (non-null)
   * @throws NullPointerException if {@code iso} is null
   */
  default <B> ValidatedPrism<S, B> andThen(Iso<A, B> iso) {
    Objects.requireNonNull(iso, "iso must not be null");
    return of(s -> parse(s).map(iso::get), b -> build(iso.reverseGet(b)));
  }

  /**
   * Composes with a plain {@link Prism}, supplying the reason its empty match cannot express.
   *
   * @param prism the inner prism; must not be null
   * @param reason the failure for the prism's non-matching case; must not be null
   * @param <B> the inner type
   * @return the composed prism (non-null)
   * @throws NullPointerException if {@code prism} or {@code reason} is null
   */
  default <B> ValidatedPrism<S, B> andThen(Prism<A, B> prism, FieldError reason) {
    Objects.requireNonNull(prism, "prism must not be null");
    Objects.requireNonNull(reason, "reason must not be null");
    return of(
        s ->
            parse(s)
                .flatMap(
                    a ->
                        prism
                            .getOptional(a)
                            .<Validated<NonEmptyList<FieldError>, B>>map(Validated::validNel)
                            .orElseGet(() -> Validated.invalidNel(reason))),
        b -> build(prism.build(b)));
  }

  /**
   * Forgets the reasons: a plain {@link Prism} whose match is the parse's success.
   *
   * @return the prism (non-null)
   */
  default Prism<S, A> toPrism() {
    return Prism.of(s -> parse(s).fold(errors -> Optional.empty(), Optional::of), this::build);
  }

  /**
   * Forgets the reasons: an {@link Affine} whose {@code set} rewrites only sources that parse (a
   * non-parsing source is left unchanged, preserving the affine absence law).
   *
   * @return the affine (non-null)
   */
  default Affine<S, A> toAffine() {
    return Affine.of(
        s -> parse(s).fold(errors -> Optional.empty(), Optional::of),
        (s, a) -> {
          Objects.requireNonNull(a, "value must not be null");
          return parse(s).isValid() ? build(a) : s;
        });
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
   * Renders every domain value; total like {@link #build}.
   *
   * @param values the domain values; neither the list nor its elements may be null
   * @return the rendered wire values, immutable (non-null)
   * @throws NullPointerException if {@code values} or one of its elements is null (the message
   *     names the offending index)
   */
  default List<S> buildAll(List<? extends A> values) {
    Objects.requireNonNull(values, "values must not be null");
    List<S> built = new ArrayList<>(values.size());
    int i = 0;
    for (A value : values) {
      built.add(build(Objects.requireNonNull(value, "values[" + i + "] must not be null")));
      i++;
    }
    return List.copyOf(built);
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
   * one element. Nothing is lost (the survivors are equal), and normalising is exactly what an
   * element prism over a set is for, so the collapse is silent. Iteration order is preserved.
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
   * Renders every element of a set; total like {@link #build}. Iteration order is preserved, and
   * two domain values that render to the same wire value collapse to one element, mirroring {@link
   * #parseAll(Set)}.
   *
   * @param values the domain values; neither the set nor its elements may be null
   * @return the rendered wire values, immutable and in iteration order (non-null)
   * @throws NullPointerException if {@code values} or one of its elements is null
   */
  default Set<S> buildAll(Set<? extends A> values) {
    Objects.requireNonNull(values, "values must not be null");
    Set<S> built = LinkedHashSet.newLinkedHashSet(values.size());
    for (A value : values) {
      built.add(build(Objects.requireNonNull(value, "values must not contain a null element")));
    }
    // Set.copyOf does not preserve iteration order, so wrap the LinkedHashSet instead.
    return Collections.unmodifiableSet(built);
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
   * Renders every element of an array; total like {@link #build}.
   *
   * @param values the domain values; neither the array nor its elements may be null
   * @param newArray the wire array constructor, typically {@code Wire[]::new}; must not be null
   * @return the rendered wire values, a fresh array in the source's order (non-null)
   * @throws NullPointerException if {@code values}, {@code newArray}, or one of the elements is
   *     null (the message names the offending index)
   */
  default S[] buildAll(A[] values, IntFunction<S[]> newArray) {
    Objects.requireNonNull(values, "values must not be null");
    Objects.requireNonNull(newArray, "newArray must not be null");
    S[] built = newArray.apply(values.length);
    for (int i = 0; i < values.length; i++) {
      built[i] = build(Objects.requireNonNull(values[i], "values[" + i + "] must not be null"));
    }
    return built;
  }

  /**
   * Parses every value of a map, accumulating all failures across the whole map.
   *
   * <p>Keys pass through untouched - only the values map through the prism. Each entry's failures
   * are located by its key (prepended as a path segment via {@link FieldError#at}), so an outer
   * {@code field(label, ...)} call composes to dotted paths such as {@code attributes.en.email}.
   * Entry order is preserved.
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
   * Renders every value of a map; total like {@link #build}. Keys pass through untouched and entry
   * order is preserved.
   *
   * @param values the domain values by key; neither the map, its keys, nor its values may be null
   * @param <K> the key type, carried through unchanged
   * @return the rendered wire values by key, immutable and in entry order (non-null)
   * @throws NullPointerException if {@code values}, one of its keys, or one of its values is null
   *     (the message names the offending key where one exists)
   */
  default <K> Map<K, S> buildValues(Map<K, ? extends A> values) {
    Objects.requireNonNull(values, "values must not be null");
    Map<K, S> built = LinkedHashMap.newLinkedHashMap(values.size());
    for (Map.Entry<K, ? extends A> entry : values.entrySet()) {
      K key = Objects.requireNonNull(entry.getKey(), "values must not contain a null key");
      built.put(
          key,
          build(Objects.requireNonNull(entry.getValue(), "values[" + key + "] must not be null")));
    }
    // Map.copyOf does not preserve entry order, so wrap the LinkedHashMap instead.
    return Collections.unmodifiableMap(built);
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
   * value. (A {@link #parseAll(Set)} collapse is silent because the collapsed elements are equal
   * and nothing is lost; here the discarded value need not be.)
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
    NonEmptyList<FieldError> failures = null;
    for (Map.Entry<? extends S, V> entry : sources.entrySet()) {
      S source = Objects.requireNonNull(entry.getKey(), "sources must not contain a null key");
      V value = entry.getValue();
      Validated<NonEmptyList<FieldError>, A> parsedKey = parse(source);
      NonEmptyList<FieldError> located = null;
      if (!parsedKey.isValid()) {
        located = parsedKey.getError().map(err -> err.at(source.toString()));
      }
      if (value == null) {
        located =
            accumulate(
                located, NonEmptyList.of(FieldError.of("must not be null").at(source.toString())));
      }
      if (located == null) {
        located = claimKey(values, parsedKey.get(), value, source);
      }
      if (located != null) {
        failures = accumulate(failures, located);
      }
    }
    return failures == null
        ? Validated.valid(Collections.unmodifiableMap(values))
        : Validated.invalid(failures);
  }

  /**
   * Parses both sides of a map, accumulating every key <em>and</em> value failure across the whole
   * map. The receiver is the <b>key</b> prism and {@code valuePrism} converts the values, matching
   * the order a {@code Map<K, V>} reads in; where only one side converts, {@link #parseKeys(Map)}
   * and {@link #parseValues(Map)} say so directly.
   *
   * <p>Both a failing key and a failing value locate under the <em>source</em> key ({@code
   * attributes.en_GB}), so an entry that is wrong on both sides reports both reasons at that one
   * location. A {@code null} value is a located {@code must not be null}, and colliding domain keys
   * are a located failure, exactly as in {@link #parseKeys(Map)}. Entry order is preserved.
   *
   * @param sources the wire entries; neither the map nor its keys may be null
   * @param valuePrism the prism converting the values; must not be null
   * @param <SV> the wire value type
   * @param <V> the domain value type
   * @return {@code Valid(map)} or every located failure, in entry order (non-null, immutable)
   * @throws NullPointerException if {@code sources}, one of its keys, or {@code valuePrism} is null
   */
  default <SV, V> Validated<NonEmptyList<FieldError>, Map<A, V>> parseEntries(
      Map<? extends S, ? extends SV> sources, ValidatedPrism<SV, V> valuePrism) {
    Objects.requireNonNull(sources, "sources must not be null");
    Objects.requireNonNull(valuePrism, "valuePrism must not be null");
    Map<A, V> values = LinkedHashMap.newLinkedHashMap(sources.size());
    NonEmptyList<FieldError> failures = null;
    for (Map.Entry<? extends S, ? extends SV> entry : sources.entrySet()) {
      S source = Objects.requireNonNull(entry.getKey(), "sources must not contain a null key");
      SV rawValue = entry.getValue();
      Validated<NonEmptyList<FieldError>, A> parsedKey = parse(source);
      Validated<NonEmptyList<FieldError>, V> parsedValue =
          rawValue == null
              ? Validated.invalidNel(FieldError.of("must not be null"))
              : valuePrism.parse(rawValue);
      NonEmptyList<FieldError> located = null;
      if (!parsedKey.isValid()) {
        located = parsedKey.getError().map(err -> err.at(source.toString()));
      }
      if (!parsedValue.isValid()) {
        located = accumulate(located, parsedValue.getError().map(err -> err.at(source.toString())));
      }
      if (located == null) {
        located = claimKey(values, parsedKey.get(), parsedValue.get(), source);
      }
      if (located != null) {
        failures = accumulate(failures, located);
      }
    }
    return failures == null
        ? Validated.valid(Collections.unmodifiableMap(values))
        : Validated.invalid(failures);
  }

  /**
   * Renders every key of a map; total like {@link #build}. Values pass through untouched and entry
   * order is preserved. Domain keys that render to the same wire key collapse to one entry, the
   * total mirror of {@link #parseKeys(Map)}' located collision.
   *
   * @param values the domain values by key; neither the map nor its keys may be null
   * @param <V> the value type, carried through unchanged
   * @return the rendered keys' map, immutable and in entry order (non-null)
   * @throws NullPointerException if {@code values} or one of its keys is null
   */
  default <V> Map<S, V> buildKeys(Map<? extends A, V> values) {
    Objects.requireNonNull(values, "values must not be null");
    Map<S, V> built = LinkedHashMap.newLinkedHashMap(values.size());
    for (Map.Entry<? extends A, V> entry : values.entrySet()) {
      A key = Objects.requireNonNull(entry.getKey(), "values must not contain a null key");
      built.put(build(key), entry.getValue());
    }
    // Map.copyOf does not preserve entry order, so wrap the LinkedHashMap instead.
    return Collections.unmodifiableMap(built);
  }

  /**
   * Renders both sides of a map; total like {@link #build}, and the reverse of {@link
   * #parseEntries(Map, ValidatedPrism)}. The receiver renders the keys, {@code valuePrism} the
   * values; entry order is preserved.
   *
   * @param values the domain entries; neither the map, its keys, nor its values may be null
   * @param valuePrism the prism rendering the values; must not be null
   * @param <SV> the wire value type
   * @param <V> the domain value type
   * @return the rendered entries, immutable and in entry order (non-null)
   * @throws NullPointerException if {@code values}, {@code valuePrism}, one of the keys, or one of
   *     the values is null (the message names the offending key where one exists)
   */
  default <SV, V> Map<S, SV> buildEntries(
      Map<? extends A, ? extends V> values, ValidatedPrism<SV, V> valuePrism) {
    Objects.requireNonNull(values, "values must not be null");
    Objects.requireNonNull(valuePrism, "valuePrism must not be null");
    Map<S, SV> built = LinkedHashMap.newLinkedHashMap(values.size());
    for (Map.Entry<? extends A, ? extends V> entry : values.entrySet()) {
      A key = Objects.requireNonNull(entry.getKey(), "values must not contain a null key");
      built.put(
          build(key),
          valuePrism.build(
              Objects.requireNonNull(entry.getValue(), "values[" + key + "] must not be null")));
    }
    // Map.copyOf does not preserve entry order, so wrap the LinkedHashMap instead.
    return Collections.unmodifiableMap(built);
  }

  /**
   * Puts a parsed entry under its parsed key, or says why it cannot: a domain key already claimed
   * by an earlier entry would discard this one, so the collision is reported, located by the source
   * key that produced it. Null when the entry was stored.
   */
  private static <K, V> NonEmptyList<FieldError> claimKey(
      Map<K, V> target, K key, V value, Object sourceKey) {
    if (target.containsKey(key)) {
      return NonEmptyList.of(FieldError.of("duplicates an earlier key").at(sourceKey.toString()));
    }
    target.put(key, value);
    return null;
  }

  /** Folds one entry's located failures into the accumulated ones; either side may be null. */
  private static NonEmptyList<FieldError> accumulate(
      NonEmptyList<FieldError> failures, NonEmptyList<FieldError> located) {
    return failures == null
        ? located
        : NonEmptyList.<FieldError>semigroup().combine(failures, located);
  }

  /**
   * Creates a {@code ValidatedPrism} from its two directions.
   *
   * @param parse the fallible, accumulating forward direction; must not be null
   * @param build the total backward direction; must not be null
   * @param <S> the source type
   * @param <A> the domain type
   * @return the prism (non-null)
   * @throws NullPointerException if {@code parse} or {@code build} is null
   */
  static <S, A> ValidatedPrism<S, A> of(
      Function<? super S, Validated<NonEmptyList<FieldError>, A>> parse,
      Function<? super A, ? extends S> build) {
    Objects.requireNonNull(parse, "parse must not be null");
    Objects.requireNonNull(build, "build must not be null");
    return new Of<>(parse, build);
  }

  /**
   * Lifts an {@link Iso}: the parse never fails.
   *
   * @param iso the iso; must not be null
   * @param <S> the source type
   * @param <A> the domain type
   * @return the prism (non-null)
   * @throws NullPointerException if {@code iso} is null
   */
  static <S, A> ValidatedPrism<S, A> fromIso(Iso<S, A> iso) {
    Objects.requireNonNull(iso, "iso must not be null");
    return of(s -> Validated.validNel(iso.get(s)), iso::reverseGet);
  }

  /**
   * Lifts a plain {@link Prism}, supplying the reason its empty match cannot express.
   *
   * <p>(A {@code prism.toValidatedPrism(reason)} instance method cannot exist — {@code Prism} lives
   * in {@code hkj-api}, which does not see {@code Validated} — so the lift is this static factory.)
   *
   * @param prism the prism; must not be null
   * @param reason the failure for the non-matching case; must not be null
   * @param <S> the source type
   * @param <A> the domain type
   * @return the prism (non-null)
   * @throws NullPointerException if {@code prism} or {@code reason} is null
   */
  static <S, A> ValidatedPrism<S, A> fromPrism(Prism<S, A> prism, FieldError reason) {
    Objects.requireNonNull(prism, "prism must not be null");
    Objects.requireNonNull(reason, "reason must not be null");
    return of(
        s ->
            prism
                .getOptional(s)
                .<Validated<NonEmptyList<FieldError>, A>>map(Validated::validNel)
                .orElseGet(() -> Validated.invalidNel(reason)),
        prism::build);
  }

  /**
   * Creates a codec from a throwing parse and a total render, with the build-parse section law
   * guarded per value: an accepted source must render back to itself (compared by {@code equals}),
   * so a spelling the render cannot reproduce is a located rejection, never a silent normalisation.
   *
   * <p>The natural way to write a codec — wrap a throwing JDK parser, render on the way out —
   * silently violates the section law whenever the parser is more lenient than the renderer ({@code
   * UUID.fromString} accepts uppercase, {@code toString} renders lowercase). Here the lenient parse
   * is fine, because {@code render} defines the canonical form and the guard rejects every other
   * spelling:
   *
   * <pre>{@code
   * // An uppercase-UUID wire (SQL Server): the canonical form is THEIRS, lawfully
   * ValidatedPrism<String, UUID> id = ValidatedPrism.canonical(
   *     "not an uppercase UUID",
   *     UUID::fromString,                                  // throwing parse, lenient is fine
   *     uuid -> uuid.toString().toUpperCase(Locale.ROOT)); // render defines the canon
   * }</pre>
   *
   * <p>Any {@code RuntimeException} thrown inside the guard — by the parse on malformed input, by
   * the render on a value the lenient parse produced, or via a parse that returns null — is the
   * located rejection, never an exception on wire input. The outward {@link #build} direction is
   * {@code render} itself and stays total by contract; a render that throws on a domain value is
   * the caller's error there. Both functions should be pure and stateless: the render runs on every
   * parse as well as on build, and the returned prism is only as shareable across threads as the
   * functions it wraps (a captured {@code SimpleDateFormat} would race).
   *
   * <p><b>What stays your obligation.</b> The guard compares by {@code equals}, so {@code S} needs
   * value equality — with an array-typed source every parse would be rejected. The parse must
   * accept what the render produces: a mismatched pair (render {@code dd/MM/uuuu}, parse {@code
   * MM/dd/uuuu}) breaks the parse-build law loudly, as rejections. And the render must be injective
   * on the values the parse produces — the one trap the guard cannot catch: with a two-digit-year
   * date format, {@code build} renders 1926-07-28 as {@code 26/07/28}, which the guard happily
   * accepts — and which re-parses to 2026-07-28, breaking the parse-build law silently. Verify a
   * custom codec with {@code ValidatedPrismLaws} from {@code hkj-test}.
   *
   * <p>The stock {@link StandardCodecs} vocabulary is built on this factory.
   *
   * @param message the failure message for every rejection; must not be null
   * @param parse the forward direction; may be lenient and may throw — the guard handles both; must
   *     not be null
   * @param render the total backward direction, defining the canonical form; must not be null
   * @param <S> the source type
   * @param <A> the domain type
   * @return the prism (non-null)
   * @throws NullPointerException if {@code message}, {@code parse} or {@code render} is null
   */
  static <S, A> ValidatedPrism<S, A> canonical(
      String message,
      Function<? super S, ? extends A> parse,
      Function<? super A, ? extends S> render) {
    Objects.requireNonNull(message, "message must not be null");
    return canonical(FieldError.of(message), parse, render);
  }

  /**
   * Creates a codec with the section law guarded per value, rejecting with the given located
   * reason; exactly {@link #canonical(String, Function, Function)} taking a pre-built {@link
   * FieldError}, as {@link #fromPrism} does.
   *
   * @param reason the failure for every rejection; must not be null
   * @param parse the forward direction; may be lenient and may throw — the guard handles both; must
   *     not be null
   * @param render the total backward direction, defining the canonical form; must not be null
   * @param <S> the source type
   * @param <A> the domain type
   * @return the prism (non-null)
   * @throws NullPointerException if {@code reason}, {@code parse} or {@code render} is null
   */
  static <S, A> ValidatedPrism<S, A> canonical(
      FieldError reason,
      Function<? super S, ? extends A> parse,
      Function<? super A, ? extends S> render) {
    Objects.requireNonNull(reason, "reason must not be null");
    Objects.requireNonNull(parse, "parse must not be null");
    Objects.requireNonNull(render, "render must not be null");
    return of(
        source -> {
          try {
            A value = parse.apply(source);
            return render.apply(value).equals(source)
                ? Validated.validNel(value)
                : Validated.invalidNel(reason);
          } catch (RuntimeException _) {
            return Validated.invalidNel(reason);
          }
        },
        render);
  }

  /**
   * The leaf implementation wrapping the two directions.
   *
   * <p>Created by the static factories; not usually named directly.
   *
   * @param parseFn the forward direction; never null
   * @param buildFn the backward direction; never null
   * @param <S> the source type
   * @param <A> the domain type
   */
  record Of<S, A>(
      Function<? super S, Validated<NonEmptyList<FieldError>, A>> parseFn,
      Function<? super A, ? extends S> buildFn)
      implements ValidatedPrism<S, A> {

    /**
     * Canonical constructor; validates.
     *
     * @throws NullPointerException if either function is null
     */
    public Of {
      Objects.requireNonNull(parseFn, "parseFn must not be null");
      Objects.requireNonNull(buildFn, "buildFn must not be null");
    }

    @Override
    public Validated<NonEmptyList<FieldError>, A> parse(S source) {
      Objects.requireNonNull(source, "source must not be null");
      return Objects.requireNonNull(parseFn.apply(source), "parse must not return null");
    }

    @Override
    public S build(A value) {
      Objects.requireNonNull(value, "value must not be null");
      return Objects.requireNonNull(buildFn.apply(value), "build must not return null");
    }
  }
}
