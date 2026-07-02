// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;

/**
 * Entry stage of a labelled accumulating {@code ValidationPath} assembly, obtained from {@link
 * Path#fields()}.
 *
 * <p>The error channel is fixed to {@code NonEmptyList<FieldError>} with {@link
 * NonEmptyList#semigroup()} for accumulation. {@code field(label, value)} prepends the label onto
 * each error's path, so nested assemblies compose ({@code "address.zip"}). All errors accumulate,
 * in field-declaration order.
 */
public final class ValidationPathFields0 {

  private static final ValidationPathFields0 INSTANCE = new ValidationPathFields0();

  private ValidationPathFields0() {}

  /**
   * The stateless entry stage.
   *
   * @return the shared instance
   */
  public static ValidationPathFields0 instance() {
    return INSTANCE;
  }

  /**
   * Adds the first validated field, prepending {@code label} onto each of its errors' paths.
   *
   * @param label the field's label; must not be null
   * @param value the validation path for the field; must not be null
   * @param <A> the field type
   * @return the arity-1 stage
   * @throws NullPointerException if {@code label} or {@code value} is null
   */
  public <A> ValidationPathFields1<A> field(
      String label, ValidationPath<NonEmptyList<FieldError>, A> value) {
    Objects.requireNonNull(label, "label must not be null");
    Objects.requireNonNull(value, "value must not be null");
    return new ValidationPathFields1<>(
        Path.validatedNel(value.run().mapError(errors -> errors.map(err -> err.at(label)))));
  }

  /**
   * Adds the first validated field without a label.
   *
   * @param value the validation path for the field; must not be null
   * @param <A> the field type
   * @return the arity-1 stage
   * @throws NullPointerException if {@code value} is null
   */
  public <A> ValidationPathFields1<A> and(ValidationPath<NonEmptyList<FieldError>, A> value) {
    Objects.requireNonNull(value, "value must not be null");
    return new ValidationPathFields1<>(Path.validatedNel(value.run()));
  }
}
