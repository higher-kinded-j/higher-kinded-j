// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import java.util.Objects;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;

/**
 * Entry stage of a labelled accumulating {@code Validated} assembly, obtained from {@link
 * Validated#fields()}.
 *
 * <p>The error channel is fixed to {@code NonEmptyList<FieldError>}. {@code field(label, value)}
 * prepends the label onto each error's path, so nested assemblies compose ({@code "address.zip"}).
 * All errors accumulate, in field-declaration order.
 */
public final class ValidatedFields0 {

  private static final ValidatedFields0 INSTANCE = new ValidatedFields0();

  private ValidatedFields0() {}

  /**
   * The stateless entry stage.
   *
   * @return the shared instance
   */
  public static ValidatedFields0 instance() {
    return INSTANCE;
  }

  /**
   * Adds the first validated field, prepending {@code label} onto each of its errors' paths.
   *
   * @param label the field's label; must not be null
   * @param value the validated field; must not be null
   * @param <A> the field type
   * @return the arity-1 stage
   * @throws NullPointerException if {@code label} or {@code value} is null
   */
  public <A> ValidatedFields1<A> field(String label, Validated<NonEmptyList<FieldError>, A> value) {
    Objects.requireNonNull(label, "label must not be null");
    Objects.requireNonNull(value, "value must not be null");
    return new ValidatedFields1<>(value.mapError(errors -> errors.map(err -> err.at(label))));
  }

  /**
   * Adds the first field without attaching a label: for values whose errors already carry their
   * paths (for example a pre-labelled sub-assembly that must not be re-prefixed) or genuinely
   * unattributable errors. Prefer {@code field(label, value)} for leaf validators.
   *
   * @param value the validated field; must not be null
   * @param <A> the field type
   * @return the arity-1 stage
   * @throws NullPointerException if {@code value} is null
   */
  public <A> ValidatedFields1<A> and(Validated<NonEmptyList<FieldError>, A> value) {
    Objects.requireNonNull(value, "value must not be null");
    return new ValidatedFields1<>(value);
  }
}
