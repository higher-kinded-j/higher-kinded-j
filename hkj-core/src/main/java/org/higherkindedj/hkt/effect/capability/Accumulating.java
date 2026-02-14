// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.capability;

import java.util.function.BiFunction;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.function.Function3;

/**
 * A capability interface representing types that support error-accumulating combinations.
 *
 * <p>This capability is parallel to {@link Combinable} but with different semantics. While {@link
 * Combinable#zipWith(Combinable, BiFunction)} short-circuits on the first error, {@code
 * Accumulating} collects errors from multiple computations using a {@link
 * org.higherkindedj.hkt.Semigroup} for error combination.
 *
 * <h2>Operations</h2>
 *
 * <ul>
 *   <li>{@link #zipWithAccum(Accumulating, BiFunction)} - Combine two paths, accumulating errors
 *   <li>{@link #zipWith3Accum(Accumulating, Accumulating, Function3)} - Combine three paths,
 *       accumulating errors
 *   <li>{@link #andAlso(Accumulating)} - Run both validations, keeping this value
 *   <li>{@link #andThen(Accumulating)} - Run both validations, keeping other value
 * </ul>
 *
 * <h2>Key Distinction from Combinable</h2>
 *
 * <p>The semantic difference is crucial for validation scenarios:
 *
 * <pre>{@code
 * // Combinable (short-circuits): reports only first error
 * validatedPath1.zipWith(validatedPath2, combiner);
 *     // => Invalid(error1) - stops at first error
 *
 * // Accumulating (collects): reports all errors
 * validatedPath1.zipWithAccum(validatedPath2, combiner);
 *     // => Invalid(combinedErrors) - accumulates both errors
 * }</pre>
 *
 * <h2>Design Rationale</h2>
 *
 * <p>Accumulating is parallel to (not extending) Combinable because:
 *
 * <ul>
 *   <li>Semantic clarity: the operation names clearly indicate behavior
 *   <li>Types implementing both can choose the appropriate method
 *   <li>Avoids confusion about which operation does what
 *   <li>Consistent with the separate capability responsibilities pattern
 * </ul>
 *
 * @param <E> the error type
 * @param <A> the value type
 * @see Combinable
 * @see org.higherkindedj.hkt.Semigroup
 */
public sealed interface Accumulating<E, A> extends Composable<A> permits ValidationPath {

  /**
   * Combines this accumulating value with another, collecting errors from both.
   *
   * <p>If both values are valid, the combiner function is applied to produce the result. If either
   * or both values contain errors, the errors are accumulated using the type's configured {@link
   * org.higherkindedj.hkt.Semigroup}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * ValidationPath<List<Error>, User> userValidation = validateUser(userData);
   * ValidationPath<List<Error>, Address> addressValidation = validateAddress(addressData);
   *
   * ValidationPath<List<Error>, Registration> result = userValidation.zipWithAccum(
   *     addressValidation,
   *     Registration::new
   * );
   * // If both invalid, errors are combined via Semigroups.list()
   * }</pre>
   *
   * @param other the other accumulating value to combine with; must not be null
   * @param combiner the function to combine the values if both are valid; must not be null
   * @param <B> the type of the other value
   * @param <C> the type of the combined result
   * @return a new accumulating value with combined result or accumulated errors
   * @throws NullPointerException if other or combiner is null
   */
  <B, C> Accumulating<E, C> zipWithAccum(
      Accumulating<E, B> other, BiFunction<? super A, ? super B, ? extends C> combiner);

  /**
   * Combines this accumulating value with two others, collecting errors from all.
   *
   * <p>This is a convenience method for combining three values without nested calls. All errors are
   * accumulated using the type's configured {@link org.higherkindedj.hkt.Semigroup}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * ValidationPath<List<Error>, String> name = validateName(input.name());
   * ValidationPath<List<Error>, String> email = validateEmail(input.email());
   * ValidationPath<List<Error>, Integer> age = validateAge(input.age());
   *
   * ValidationPath<List<Error>, User> result = name.zipWith3Accum(
   *     email,
   *     age,
   *     User::new
   * );
   * // Accumulates errors from all three validations
   * }</pre>
   *
   * @param second the second accumulating value; must not be null
   * @param third the third accumulating value; must not be null
   * @param combiner the function to combine the values if all are valid; must not be null
   * @param <B> the type of the second value
   * @param <C> the type of the third value
   * @param <D> the type of the combined result
   * @return a new accumulating value with combined result or accumulated errors
   * @throws NullPointerException if any argument is null
   */
  <B, C, D> Accumulating<E, D> zipWith3Accum(
      Accumulating<E, B> second,
      Accumulating<E, C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner);

  /**
   * Runs both validations, accumulating errors, keeping this value if both are valid.
   *
   * <p>This is useful when you want to ensure multiple validations pass but only care about one of
   * the values. Errors from the other validation are still accumulated.
   *
   * <p>Example:
   *
   * <pre>{@code
   * ValidationPath<List<Error>, User> userValidation = validateUser(userData);
   * ValidationPath<List<Error>, Unit> termsAccepted = validateTermsAccepted(form);
   *
   * ValidationPath<List<Error>, User> result = userValidation.andAlso(termsAccepted);
   * // Returns user if both valid, accumulates errors if either invalid
   * }</pre>
   *
   * @param other the other validation to run; must not be null
   * @return this value if both valid, or accumulated errors
   * @throws NullPointerException if other is null
   */
  Accumulating<E, A> andAlso(Accumulating<E, ?> other);

  /**
   * Runs both validations, accumulating errors, keeping the other value if both are valid.
   *
   * <p>This is useful when the first validation is a prerequisite check but you want the second
   * value. Errors from both validations are accumulated.
   *
   * <p>Example:
   *
   * <pre>{@code
   * ValidationPath<List<Error>, Unit> authCheck = validateAuthenticated(request);
   * ValidationPath<List<Error>, Data> dataValidation = validateData(payload);
   *
   * ValidationPath<List<Error>, Data> result = authCheck.andThen(dataValidation);
   * // Returns data if both valid, accumulates errors if either invalid
   * }</pre>
   *
   * @param other the other validation whose value to keep; must not be null
   * @param <B> the type of the other value
   * @return the other value if both valid, or accumulated errors
   * @throws NullPointerException if other is null
   */
  <B> Accumulating<E, B> andThen(Accumulating<E, B> other);
}
