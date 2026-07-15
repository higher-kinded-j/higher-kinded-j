// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.edit;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Combinators over {@linkplain Edit edits}: fold N independent edits into one reusable operation.
 *
 * <p>Two entry points, selected by the edits themselves:
 *
 * <ul>
 *   <li>{@link #combine(Edit[]) combine} — pure edits only (compile-time), folded into a single
 *       {@link Update} via {@link Monoids#update()};
 *   <li>{@link #accumulate(FallibleEdit[]) accumulate} — pure and fallible edits mixed; every
 *       edit's incoming value is validated independently, all errors are reported at once, and the
 *       writes are applied only if every edit validated.
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
 * (validation never sees a source, so accumulation is sound and an accumulated patch is reusable
 * across sources). Phase two applies the writes by a single sequential left-to-right fold onto the
 * source. Application order is observable only when paths overlap: disjoint paths commute, while an
 * edit at an overlapping path sees the previous edit's result. Genuinely coupled fields should be
 * one atomic edit via {@code Lens.paired}.
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
   * @param edits the edits, validated independently and applied in the given order; must not be
   *     null or contain null
   * @param <S> the type of the value being edited
   * @return the accumulated patch (non-null)
   * @throws NullPointerException if {@code edits} is null or contains null
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
   * @return the accumulated patch (non-null)
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
   * An accumulated patch: either one folded {@link Update} ready to apply, or every validation
   * failure, located and in edit order.
   *
   * <p>Validation happened when the patch was built, independently of any source, so one
   * accumulated patch can be applied to many sources.
   *
   * @param <S> the type of the value being edited
   */
  public static final class Accumulated<S> {

    private final Validated<NonEmptyList<FieldError>, Update<S>> folded;

    private Accumulated(Validated<NonEmptyList<FieldError>, Update<S>> folded) {
      this.folded = folded;
    }

    /**
     * Applies the patch: {@code Valid(updated)} with every write performed left to right, or {@code
     * Invalid} carrying every validation failure.
     *
     * @param source the value to edit; must not be null
     * @return the outcome (non-null)
     * @throws NullPointerException if {@code source} is null
     */
    public Validated<NonEmptyList<FieldError>, S> apply(S source) {
      Objects.requireNonNull(source, "source must not be null");
      return folded.map(update -> update.apply(source));
    }

    /**
     * Applies the patch onto the railway: the {@link ValidationPath} twin of {@link #apply}.
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
     * updates.
     *
     * @return the validated folded update (non-null)
     */
    public Validated<NonEmptyList<FieldError>, Update<S>> toValidated() {
      return folded;
    }
  }
}
