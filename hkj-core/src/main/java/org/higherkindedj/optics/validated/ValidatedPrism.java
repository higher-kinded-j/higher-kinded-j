// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.validated;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
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
 * <p><b>Two halves.</b> Each direction is also a type of its own: a {@code ValidatedPrism} is both
 * a {@link ValidatedParse}, carrying {@code parse} and its bulk forms over containers, and a {@link
 * ValidatedBuild}, carrying {@code build} and its bulk forms. Code that needs only one direction
 * asks for that half, so a boundary that can only be read, or only be written, plugs in beside a
 * whole prism.
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
public sealed interface ValidatedPrism<S, A> extends ValidatedParse<S, A>, ValidatedBuild<S, A>
    permits ValidatedPrism.Of {

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
