// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.edit;

import java.util.Objects;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;

/**
 * A single edit whose incoming value may fail validation: either a {@linkplain Edit pure edit}
 * (which always succeeds) or a parsed edit created by {@link Edit#parseIfPresent}.
 *
 * <p>A fallible edit separates the two phases of a validated multi-edit: phase one validates the
 * incoming value independently of any source (this is what {@link #toValidated()} exposes — a
 * {@link Validated} carrying the {@linkplain Update write} to perform); phase two applies the
 * accumulated writes to a source, which {@link Edits#accumulate} performs only if <em>every</em>
 * edit validated.
 *
 * <p>Failures are located: {@link #at(String)} tags this edit's errors with a field label, exactly
 * as {@link FieldError#at(String)} composes paths outward-in:
 *
 * <pre>{@code
 * Edits.accumulate(
 *         Edit.setIfPresent(ORDER_NUMBER, req.orderNumber()),
 *         Edit.parseIfPresent(EMAIL, req.email(), Email::parse).at("email"))
 *     .apply(order);
 * // Invalid(NEL[ "email: not an address" ]) — or Valid(order') with only present fields changed
 * }</pre>
 *
 * @param <S> the type of the value being edited
 * @see Edit
 * @see Edits
 */
public sealed interface FallibleEdit<S> permits Edit, FallibleEdit.Parsed {

  /**
   * The outcome of validating this edit's incoming value: the write to perform if valid, or the
   * located errors if not.
   *
   * <p>Pure edits always return {@code Valid}; parsed edits return whatever the parser produced.
   * Validation is independent of any source — an edit can be validated once and applied to many.
   *
   * @return the validated write (non-null)
   */
  Validated<NonEmptyList<FieldError>, Update<S>> toValidated();

  /**
   * Returns an edit whose failures are located under {@code label}.
   *
   * <p>The label is prepended to each error's path via {@link FieldError#at(String)}, so an outer
   * label composes around inner ones ({@code "zip"} becomes {@code "address.zip"}). Pure edits
   * carry no errors, so labelling them is a no-op.
   *
   * @param label the field label to attach; must not be null
   * @return a located edit (non-null)
   * @throws NullPointerException if {@code label} is null
   */
  FallibleEdit<S> at(String label);

  /**
   * A fallible leaf edit: the result of parsing an incoming value into a write.
   *
   * <p>Created by {@link Edit#parseIfPresent}; not usually named directly.
   *
   * @param validated the validated write; never null
   * @param <S> the type of the value being edited
   */
  record Parsed<S>(Validated<NonEmptyList<FieldError>, Update<S>> validated)
      implements FallibleEdit<S> {

    /**
     * Canonical constructor; validates.
     *
     * @throws NullPointerException if {@code validated} is null
     */
    public Parsed {
      Objects.requireNonNull(validated, "validated must not be null");
    }

    @Override
    public Validated<NonEmptyList<FieldError>, Update<S>> toValidated() {
      return validated;
    }

    @Override
    public FallibleEdit<S> at(String label) {
      Objects.requireNonNull(label, "label must not be null");
      return new Parsed<>(validated.mapError(errors -> errors.map(error -> error.at(label))));
    }
  }
}
