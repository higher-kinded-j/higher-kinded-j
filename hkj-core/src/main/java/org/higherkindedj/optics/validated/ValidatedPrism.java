// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.validated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
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
   * Parses every element, accumulating all failures across the whole list.
   *
   * @param sources the wire values; neither the list nor its elements may be null
   * @return {@code Valid(list)} or every located failure from every element, in list order
   *     (non-null)
   * @throws NullPointerException if {@code sources} or one of its elements is null (the message
   *     names the offending index)
   */
  default Validated<NonEmptyList<FieldError>, List<A>> parseAll(List<? extends S> sources) {
    Objects.requireNonNull(sources, "sources must not be null");
    List<A> values = new ArrayList<>(sources.size());
    NonEmptyList<FieldError> failures = null;
    int i = 0;
    for (S source : sources) {
      Objects.requireNonNull(source, "sources[" + i + "] must not be null");
      i++;
      Validated<NonEmptyList<FieldError>, A> parsed = parse(source);
      if (parsed.isValid()) {
        values.add(parsed.get());
      } else if (failures == null) {
        failures = parsed.getError();
      } else {
        failures = NonEmptyList.<FieldError>semigroup().combine(failures, parsed.getError());
      }
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
   * @param sources the wire values by key; neither the map, its keys, nor its values may be null
   * @param <K> the key type, carried through unchanged
   * @return {@code Valid(map)} or every located failure from every entry, in entry order (non-null,
   *     immutable)
   * @throws NullPointerException if {@code sources}, one of its keys, or one of its values is null
   *     (the message names the offending key where one exists)
   */
  default <K> Validated<NonEmptyList<FieldError>, Map<K, A>> parseValues(
      Map<K, ? extends S> sources) {
    Objects.requireNonNull(sources, "sources must not be null");
    Map<K, A> values = LinkedHashMap.newLinkedHashMap(sources.size());
    NonEmptyList<FieldError> failures = null;
    for (Map.Entry<K, ? extends S> entry : sources.entrySet()) {
      K key = Objects.requireNonNull(entry.getKey(), "sources must not contain a null key");
      S source = Objects.requireNonNull(entry.getValue(), "sources[" + key + "] must not be null");
      Validated<NonEmptyList<FieldError>, A> parsed = parse(source);
      if (parsed.isValid()) {
        values.put(key, parsed.get());
      } else {
        NonEmptyList<FieldError> located = parsed.getError().map(err -> err.at(key.toString()));
        failures =
            failures == null
                ? located
                : NonEmptyList.<FieldError>semigroup().combine(failures, located);
      }
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
