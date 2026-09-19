// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.edit;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;

/**
 * Combinators over {@linkplain Edit edits}: fold N independent edits into one reusable operation.
 *
 * <p>Two builders, selected by the edits themselves, and a third form of the second:
 *
 * <ul>
 *   <li>{@link #combine(Edit[]) combine} — pure edits only (compile-time), folded into a single
 *       {@link Update} via {@link Monoids#update()};
 *   <li>{@link #accumulate(FallibleEdit[]) accumulate} — pure and fallible edits mixed; every
 *       edit's incoming value is validated independently, all errors are reported at once, and the
 *       writes are applied only if every edit validated;
 *   <li>{@link #accumulate(Lens, FallibleEdit[]) accumulate(focus, ...)} — the same, with the
 *       writes landing on one focus of the source, which is set back once: for a record whose
 *       constructor checks its fields against each other, the record is constructed once, from the
 *       final values.
 * </ul>
 *
 * <pre>{@code
 * // Pure: reusable across sources; composes (Update<S> is a Monoid).
 * Update<Order> normalise = Edits.combine(modify(EMAIL, String::toLowerCase),
 *                                         modify(SKU,   String::trim));
 * Order a = normalise.apply(orderA);
 * Order b = normalise.andThen(applyDiscount).apply(orderB);
 *
 * // Sparse PATCH: nullable DTO fields land 1:1; every bad field reported at once, located.
 * Validated<NonEmptyList<FieldError>, Order> updated =
 *     Edits.accumulate(
 *             setIfPresent(ORDER_NUMBER, req.orderNumber()),
 *             parseIfPresent(EMAIL, req.email(), Email::parse),
 *             modifyIfPresent(QUANTITY, req.qtyDelta(), (delta, qty) -> qty + delta))
 *         .apply(order);
 * }</pre>
 *
 * <p><b>Semantics — two phases.</b> Phase one validates each edit's incoming value independently
 * (validation never sees a source, so accumulation is sound and an accumulated update is reusable
 * across sources). Phase two applies the writes by a single sequential left-to-right fold onto the
 * source. Application order is observable only when paths overlap: disjoint paths commute, while an
 * edit at an overlapping path sees the previous edit's result. Genuinely coupled fields should be
 * one atomic edit via {@code Lens.paired}.
 *
 * <p>Each write through a record's lens constructs a new record, so a constructor that checks the
 * fields against each other sees every intermediate value, and one it refuses throws from phase
 * two. When the edits set several of those fields, write them onto a focus carrying those fields
 * with {@link #accumulate(Lens, FallibleEdit[])}, which constructs the record once and reports a
 * refusal as an error. That one check sees the source, so it belongs to phase two: the same
 * accumulated update can be valid for one source and refused for another.
 *
 * <p>Errors accumulate in edit order, on the {@link NonEmptyList} channel, exactly as the {@code
 * Validated.accumulate()} assembly does — but as a homogeneous fold, so there is no arity ceiling.
 *
 * @see Edit
 * @see FallibleEdit
 */
public final class Edits {

  private Edits() {}

  /**
   * Folds pure edits into one {@link Update}, applied left to right.
   *
   * <p>Only {@link Edit} (pure) fits this signature — a {@link FallibleEdit} is rejected at compile
   * time, so validation failures can never be silently dropped. With no edits the result is the
   * identity update.
   *
   * @param edits the pure edits, applied in the given order; must not be null or contain null
   * @param <S> the type of the value being edited
   * @return one update performing all edits (non-null)
   * @throws NullPointerException if {@code edits} is null or contains null
   */
  @SafeVarargs
  public static <S> Update<S> combine(Edit<S>... edits) {
    Objects.requireNonNull(edits, "edits must not be null");
    return combine(Arrays.asList(edits));
  }

  /**
   * Folds pure edits into one {@link Update}, applied left to right.
   *
   * @param edits the pure edits, applied in list order; must not be null or contain null
   * @param <S> the type of the value being edited
   * @return one update performing all edits (non-null)
   * @throws NullPointerException if {@code edits} is null or contains null
   */
  public static <S> Update<S> combine(List<? extends Edit<S>> edits) {
    Objects.requireNonNull(edits, "edits must not be null");
    return Monoids.<S>update()
        .combineAll(
            edits.stream()
                .map(edit -> Objects.requireNonNull(edit, "edit must not be null").toUpdate())
                .toList());
  }

  /**
   * Validates every edit's incoming value independently, accumulating <em>all</em> failures.
   *
   * <p>Pure and fallible edits mix freely — a pure edit is a fallible edit that always succeeds.
   * The returned {@link Accumulated} applies the writes (left to right) only if every edit
   * validated; otherwise it carries every error, in edit order. With no edits the result is always
   * valid and applies no changes.
   *
   * <p>A write that throws, such as a record's constructor refusing a value the fold passes
   * through, propagates from {@link Accumulated#apply apply}. For fields a constructor checks
   * against each other, {@link #accumulate(Lens, FallibleEdit[])} constructs once and reports the
   * refusal instead.
   *
   * @param edits the edits, validated independently and applied in the given order; must not be
   *     null or contain null
   * @param <S> the type of the value being edited
   * @return the accumulated update (non-null)
   * @throws NullPointerException if {@code edits} is null or contains null
   * @see #accumulate(Lens, FallibleEdit[])
   */
  @SafeVarargs
  public static <S> Accumulated<S> accumulate(FallibleEdit<S>... edits) {
    Objects.requireNonNull(edits, "edits must not be null");
    return accumulate(Arrays.asList(edits));
  }

  /**
   * Validates every edit's incoming value independently, accumulating <em>all</em> failures.
   *
   * @param edits the edits, validated independently and applied in list order; must not be null or
   *     contain null
   * @param <S> the type of the value being edited
   * @return the accumulated update (non-null)
   * @throws NullPointerException if {@code edits} is null or contains null
   */
  public static <S> Accumulated<S> accumulate(List<? extends FallibleEdit<S>> edits) {
    Objects.requireNonNull(edits, "edits must not be null");
    Validated<NonEmptyList<FieldError>, Update<S>> folded = Validated.valid(Update.identity());
    for (FallibleEdit<S> edit : edits) {
      Objects.requireNonNull(edit, "edit must not be null");
      folded = edit.toValidated().ap(folded.map(prev -> prev::andThen), NonEmptyList.semigroup());
    }
    return new Accumulated<>(folded);
  }

  /**
   * Validates every edit's incoming value independently, as {@link #accumulate(FallibleEdit[])}
   * does, then writes onto one focus of the source and sets it back once.
   *
   * <p>A record whose constructor checks its fields against each other refuses some of the values a
   * field-by-field write passes through. Moving a {@code Range(1, 3)} to {@code Range(5, 10)} one
   * end at a time builds {@code Range(5, 3)} first, which the constructor refuses, although the
   * final range is valid. Here the edits write onto {@code A}, a value {@code focus} reads from the
   * source that carries the fields they set with no check of its own. Once every write is done,
   * {@code focus.set} constructs the result, so the constructor sees only the final values:
   *
   * <pre>{@code
   * record Range(int lo, int hi) {
   *   Range { if (lo > hi) throw new IllegalArgumentException("lo > hi"); }
   * }
   * @GenerateFocus
   * record Ends(int lo, int hi) {}   // the fields the edits set, unchecked
   *
   * Lens<Range, Ends> ends =
   *     Lens.of(r -> new Ends(r.lo(), r.hi()), (r, e) -> new Range(e.lo(), e.hi()));
   *
   * Edits.accumulate(ends,
   *         setIfPresent(EndsFocus.lo(), req.lo()),
   *         setIfPresent(EndsFocus.hi(), req.hi()))
   *     .apply(new Range(1, 3));
   * // lo 5, hi 10: Valid(Range[lo=5, hi=10])
   * // lo 5 alone:  Invalid(NEL[ "lo > hi" ])
   * }</pre>
   *
   * <p>A {@code RuntimeException} thrown by {@code focus.set} is the constructor refusing the final
   * values: {@link Accumulated#apply apply} reports it as an unlabelled {@link FieldError} carrying
   * its message. An exception without a message, or with a blank one, reads {@code "not a valid
   * Range"}, naming the source's runtime class. The set runs only once every edit validated, so its
   * refusal never joins the edits' own errors. Only the set is caught: an edit's own function, and
   * {@code focus.get}, still throw as they would in {@link #accumulate(FallibleEdit[])}. When the
   * edits hand back the focus exactly as they read it, every one of them absent, the source is
   * returned as it is and {@code focus.set} never runs.
   *
   * <p>The edits' errors are located relative to the focus, and the refusal is unlabelled. Where
   * the focus is a nested component rather than the source's own fields, locate them for the source
   * by adding the component's name to each edit with {@link FallibleEdit#at(String)}.
   *
   * @param focus reads the value the edits write onto from the source, and sets the edited value
   *     back; must not be null
   * @param edits the edits onto the focus, validated independently and applied in the given order;
   *     must not be null or contain null
   * @param <S> the type of the value being edited
   * @param <A> the type of the focus the edits write onto
   * @return the accumulated update (non-null)
   * @throws NullPointerException if {@code focus} or {@code edits} is null, or {@code edits}
   *     contains null
   */
  @SafeVarargs
  public static <S, A> Accumulated<S> accumulate(Lens<S, A> focus, FallibleEdit<A>... edits) {
    Objects.requireNonNull(edits, "edits must not be null");
    return accumulate(focus, Arrays.asList(edits));
  }

  /**
   * Validates every edit's incoming value independently, then writes onto one focus of the source
   * and sets it back once: {@link #accumulate(Lens, FallibleEdit[])} over a list.
   *
   * @param focus reads the value the edits write onto from the source, and sets the edited value
   *     back; must not be null
   * @param edits the edits onto the focus, validated independently and applied in list order; must
   *     not be null or contain null
   * @param <S> the type of the value being edited
   * @param <A> the type of the focus the edits write onto
   * @return the accumulated update (non-null)
   * @throws NullPointerException if {@code focus} or {@code edits} is null, or {@code edits}
   *     contains null
   */
  public static <S, A> Accumulated<S> accumulate(
      Lens<S, A> focus, List<? extends FallibleEdit<A>> edits) {
    Objects.requireNonNull(focus, "focus must not be null");
    Validated<NonEmptyList<FieldError>, Update<A>> onFocus = accumulate(edits).folded;
    return new Accumulated<>(
        onFocus.map(
            update ->
                source -> {
                  A read = focus.get(source);
                  A edited = update.apply(read);
                  return edited == read ? source : focus.set(edited, source);
                }),
        source -> onFocus.flatMap(update -> setOnce(focus, update, source)));
  }

  /**
   * Applies the update to the source's focus and sets the result back once, reporting a refusal as
   * an unlabelled {@link FieldError}. A focus the edits handed back unchanged leaves the source as
   * it is, without a set.
   */
  private static <S, A> Validated<NonEmptyList<FieldError>, S> setOnce(
      Lens<S, A> focus, Update<A> update, S source) {
    A read = focus.get(source);
    A edited = update.apply(read);
    if (edited == read) {
      return Validated.validNel(source);
    }
    S result;
    try {
      result = focus.set(edited, source);
    } catch (RuntimeException refused) {
      String message = refused.getMessage();
      return Validated.invalidNel(
          FieldError.of(message == null || message.isBlank() ? unexplained(source) : message));
    }
    return Validated.validNel(result);
  }

  /**
   * {@code "not a valid Range"}, naming the source's runtime class, or its binary name where the
   * class has no simple name.
   */
  private static String unexplained(Object source) {
    Class<?> type = source.getClass();
    return "not a valid "
        + (type.getSimpleName().isEmpty() ? type.getName() : type.getSimpleName());
  }

  /**
   * An accumulated update: either one folded {@link Update} ready to apply, or every validation
   * failure, located and in edit order.
   *
   * <p>Validation happened when the patch was built, independently of any source, so one
   * accumulated update can be applied to many sources. Built {@linkplain #accumulate(Lens,
   * FallibleEdit[]) onto a focus}, it also checks each source's result once, when the focus is set
   * back, so a valid update can still be refused for one source.
   *
   * @param <S> the type of the value being edited
   */
  public static final class Accumulated<S> {

    private final Validated<NonEmptyList<FieldError>, Update<S>> folded;
    private final Function<S, Validated<NonEmptyList<FieldError>, S>> applied;

    private Accumulated(Validated<NonEmptyList<FieldError>, Update<S>> folded) {
      this(folded, source -> folded.map(update -> update.apply(source)));
    }

    private Accumulated(
        Validated<NonEmptyList<FieldError>, Update<S>> folded,
        Function<S, Validated<NonEmptyList<FieldError>, S>> applied) {
      this.folded = folded;
      this.applied = applied;
    }

    /**
     * Applies the update: {@code Valid(updated)} with every write performed left to right, or
     * {@code Invalid} carrying every validation failure. Built {@linkplain #accumulate(Lens,
     * FallibleEdit[]) onto a focus}, it is also {@code Invalid} when setting the edited focus back
     * refuses it.
     *
     * @param source the value to edit; must not be null
     * @return the outcome (non-null)
     * @throws NullPointerException if {@code source} is null
     */
    public Validated<NonEmptyList<FieldError>, S> apply(S source) {
      Objects.requireNonNull(source, "source must not be null");
      return applied.apply(source);
    }

    /**
     * Applies the update onto the railway: the {@link ValidationPath} twin of {@link #apply}.
     *
     * @param source the value to edit; must not be null
     * @return the outcome as a {@code ValidationPath} (non-null)
     * @throws NullPointerException if {@code source} is null
     */
    public ValidationPath<NonEmptyList<FieldError>, S> applyPath(S source) {
      return Path.validatedNel(apply(source));
    }

    /**
     * The folded write itself: {@code Valid(update)} if every edit validated, else the errors.
     *
     * <p>Useful for inspecting the outcome before choosing a source, or for composing with other
     * updates. Built {@linkplain #accumulate(Lens, FallibleEdit[]) onto a focus}, the update writes
     * onto the focus and sets it back once; an {@code Update} has no error channel, so a
     * constructor refusing the final values throws from it, where {@link #apply} reports the
     * refusal.
     *
     * @return the validated folded update (non-null)
     */
    public Validated<NonEmptyList<FieldError>, Update<S>> toValidated() {
      return folded;
    }
  }
}
