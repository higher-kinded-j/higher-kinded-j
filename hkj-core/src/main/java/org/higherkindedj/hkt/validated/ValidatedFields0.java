// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import java.util.Objects;
import java.util.function.Supplier;
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

  /**
   * Completes an assembly of no fields: with nothing to fail, the result is {@code f}'s value.
   *
   * <p>{@code f} runs whatever it is handed, so an exception it throws escapes the assembly. Where
   * it may refuse, {@code construct} guards the call instead.
   *
   * @param f supplies the assembled value; must not be null
   * @param <R> the assembled type
   * @return {@code f}'s value, as a valid result
   * @throws NullPointerException if {@code f} is null
   */
  public <R> Validated<NonEmptyList<FieldError>, R> apply(Supplier<? extends R> f) {
    Objects.requireNonNull(f, "f must not be null");
    return Validated.validNel(f.get());
  }

  /**
   * Completes an assembly of no fields like {@code apply}, for a supplier that may refuse,
   * typically the canonical constructor of a record with no components enforcing an invariant. A
   * {@code RuntimeException} {@code f} throws becomes an unlabelled {@code FieldError} carrying its
   * message, or {@code fallbackMessage} when that is missing or blank, so an enclosing {@code
   * field(label, ...)} locates it. Only {@code f} runs inside the guard, so a {@code null} it
   * returns still throws as it does from {@code apply}.
   *
   * @param f supplies the assembled value; must not be null
   * @param fallbackMessage the message when the exception carries none, such as {@code "not a valid
   *     Deleted"}; must not be null or blank
   * @param <R> the assembled type
   * @return the assembled value, or the refusal
   * @throws NullPointerException if {@code f} or {@code fallbackMessage} is null
   * @throws IllegalArgumentException if {@code fallbackMessage} is blank
   */
  public <R> Validated<NonEmptyList<FieldError>, R> construct(
      Supplier<? extends R> f, String fallbackMessage) {
    Objects.requireNonNull(f, "f must not be null");
    Objects.requireNonNull(fallbackMessage, "fallbackMessage must not be null");
    if (fallbackMessage.isBlank()) {
      throw new IllegalArgumentException("fallbackMessage must not be blank");
    }
    R constructed;
    try {
      constructed = f.get();
    } catch (RuntimeException refused) {
      String message = refused.getMessage();
      return Validated.invalidNel(
          FieldError.of(message == null || message.isBlank() ? fallbackMessage : message));
    }
    return Validated.validNel(constructed);
  }
}
