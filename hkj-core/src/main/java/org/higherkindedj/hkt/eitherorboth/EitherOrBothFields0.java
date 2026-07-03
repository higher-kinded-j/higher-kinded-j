// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import java.util.Objects;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;

/**
 * Entry stage of a labelled tolerant accumulating {@code EitherOrBoth} assembly, obtained from
 * {@link EitherOrBoth#fields()}.
 *
 * <p>The warning channel is fixed to {@code NonEmptyList<FieldError>}. {@code field(label, value)}
 * prepends the label onto each warning's path, so nested assemblies compose ({@code
 * "address.zip"}). Warnings accumulate ({@code Both}) while the value keeps flowing, in
 * field-declaration order.
 */
public final class EitherOrBothFields0 {

  private static final EitherOrBothFields0 INSTANCE = new EitherOrBothFields0();

  private EitherOrBothFields0() {}

  /**
   * The stateless entry stage.
   *
   * @return the shared instance
   */
  public static EitherOrBothFields0 instance() {
    return INSTANCE;
  }

  /**
   * Adds the first field, prepending {@code label} onto each of its warnings' paths.
   *
   * @param label the field's label; must not be null
   * @param value the field's {@code EitherOrBoth}; must not be null
   * @param <A> the field type
   * @return the arity-1 stage
   * @throws NullPointerException if {@code label} or {@code value} is null
   */
  public <A> EitherOrBothFields1<A> field(
      String label, EitherOrBoth<NonEmptyList<FieldError>, A> value) {
    Objects.requireNonNull(label, "label must not be null");
    Objects.requireNonNull(value, "value must not be null");
    return new EitherOrBothFields1<>(value.mapLeft(errors -> errors.map(err -> err.at(label))));
  }

  /**
   * Adds the first field without attaching a label: for values whose warnings already carry their
   * paths (for example a pre-labelled sub-assembly that must not be re-prefixed) or genuinely
   * unattributable warnings. Prefer {@code field(label, value)} for leaf validators.
   *
   * @param value the field's {@code EitherOrBoth}; must not be null
   * @param <A> the field type
   * @return the arity-1 stage
   * @throws NullPointerException if {@code value} is null
   */
  public <A> EitherOrBothFields1<A> and(EitherOrBoth<NonEmptyList<FieldError>, A> value) {
    Objects.requireNonNull(value, "value must not be null");
    return new EitherOrBothFields1<>(value);
  }
}
