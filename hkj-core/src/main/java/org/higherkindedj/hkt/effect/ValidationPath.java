// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.annotation.Generated;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.effect.capability.Accumulating;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.effect.capability.Recoverable;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Invalid;
import org.higherkindedj.hkt.validated.Valid;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;

/**
 * A fluent path wrapper for {@link Validated} values with error accumulation support.
 *
 * <p>{@code ValidationPath} provides a chainable API for composing validation operations. It
 * uniquely implements both {@link Chainable} (for sequential operations that short-circuit) and
 * {@link Accumulating} (for parallel validations that accumulate errors).
 *
 * <h2>Dual Nature: Short-Circuit vs Accumulating</h2>
 *
 * <p>ValidationPath supports two modes of composition:
 *
 * <ul>
 *   <li><b>Short-circuit</b> ({@link #via}, {@link #zipWith}): Stops at first error, like
 *       EitherPath
 *   <li><b>Accumulating</b> ({@link #zipWithAccum}, {@link #andAlso}): Collects all errors using
 *       Semigroup
 * </ul>
 *
 * <h2>Creating ValidationPath instances</h2>
 *
 * <p>Use the {@link Path} factory class to create instances:
 *
 * <pre>{@code
 * ValidationPath<List<Error>, User> valid = Path.valid(user, Semigroups.list());
 * ValidationPath<List<Error>, User> invalid = Path.invalid(List.of(error), Semigroups.list());
 * ValidationPath<List<Error>, User> fromValidated = Path.validated(validated, Semigroups.list());
 * }</pre>
 *
 * <h2>Short-circuit composition (via)</h2>
 *
 * <pre>{@code
 * ValidationPath<List<Error>, Order> result = Path.valid(userId, Semigroups.list())
 *     .via(id -> userService.findById(id))   // Stops if user not found
 *     .via(user -> createOrder(user));       // Only runs if user found
 * }</pre>
 *
 * <h2>Accumulating composition (zipWithAccum)</h2>
 *
 * <pre>{@code
 * ValidationPath<List<Error>, Registration> result = validateName(input.name())
 *     .zipWithAccum(validateEmail(input.email()), (name, email) ->
 *         new Registration(name, email));
 * // Collects errors from both validations
 * }</pre>
 *
 * <h2>Error handling</h2>
 *
 * <pre>{@code
 * User user = Path.valid(userData, Semigroups.list())
 *     .via(data -> validateUser(data))
 *     .recover(errors -> User.guest())
 *     .run()
 *     .fold(e -> null, u -> u);
 * }</pre>
 *
 * @param <E> the type of the error (typically a collection type like {@code List<Error>})
 * @param <A> the type of the success value
 */
public final class ValidationPath<E, A>
    implements Chainable<A>, Accumulating<E, A>, Recoverable<E, A> {

  private final Validated<E, A> value;
  private final Semigroup<E> semigroup;

  /**
   * Creates a new ValidationPath wrapping the given Validated with the specified Semigroup.
   *
   * @param value the Validated to wrap; must not be null
   * @param semigroup the Semigroup for combining errors; must not be null
   */
  ValidationPath(Validated<E, A> value, Semigroup<E> semigroup) {
    this.value = Objects.requireNonNull(value, "value must not be null");
    this.semigroup = Objects.requireNonNull(semigroup, "semigroup must not be null");
  }

  /**
   * Returns the underlying Validated value.
   *
   * @return the wrapped Validated
   */
  public Validated<E, A> run() {
    return value;
  }

  /**
   * Returns the Semigroup used for error accumulation.
   *
   * @return the semigroup
   */
  public Semigroup<E> semigroup() {
    return semigroup;
  }

  /**
   * Returns the success value if valid, otherwise returns the provided default.
   *
   * @param defaultValue the value to return if this path is invalid
   * @return the success value or the default
   */
  public A getOrElse(A defaultValue) {
    return value.fold(_ -> defaultValue, a -> a);
  }

  /**
   * Returns the success value if valid, otherwise invokes the supplier.
   *
   * @param supplier provides the default value; must not be null
   * @return the success value or the supplier's result
   * @throws NullPointerException if supplier is null
   */
  public A getOrElseGet(Supplier<? extends A> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return value.fold(_ -> supplier.get(), a -> a);
  }

  /**
   * Folds both cases of this path into a single value.
   *
   * @param invalidMapper the function to apply if invalid; must not be null
   * @param validMapper the function to apply if valid; must not be null
   * @param <B> the result type
   * @return the result of applying the appropriate function
   * @throws NullPointerException if either mapper is null
   */
  public <B> B fold(
      Function<? super E, ? extends B> invalidMapper,
      Function<? super A, ? extends B> validMapper) {
    Objects.requireNonNull(invalidMapper, "invalidMapper must not be null");
    Objects.requireNonNull(validMapper, "validMapper must not be null");
    return value.fold(invalidMapper, validMapper);
  }

  /**
   * Returns true if this path contains a valid value.
   *
   * @return true if valid, false if invalid
   */
  public boolean isValid() {
    return value.isValid();
  }

  /**
   * Returns true if this path contains an error.
   *
   * @return true if invalid, false if valid
   */
  public boolean isInvalid() {
    return value.isInvalid();
  }

  // ===== Conversions =====

  /**
   * Converts this ValidationPath to an EitherPath.
   *
   * @return an EitherPath containing either the error (left) or value (right)
   */
  public EitherPath<E, A> toEitherPath() {
    return value.fold(
        e -> new EitherPath<>(Either.left(e)), a -> new EitherPath<>(Either.right(a)));
  }

  /**
   * Converts this ValidationPath to a MaybePath, discarding the error.
   *
   * @return a MaybePath containing the value if valid, or empty if invalid
   */
  public MaybePath<A> toMaybePath() {
    return value.fold(_ -> new MaybePath<>(Maybe.nothing()), a -> new MaybePath<>(Maybe.just(a)));
  }

  /**
   * Converts this ValidationPath to a TryPath.
   *
   * @param errorToException converts the error to an exception; must not be null
   * @return a TryPath representing this path's value or the exception
   * @throws NullPointerException if errorToException is null
   */
  public TryPath<A> toTryPath(Function<? super E, ? extends Throwable> errorToException) {
    Objects.requireNonNull(errorToException, "errorToException must not be null");
    return value.fold(
        e -> new TryPath<>(Try.failure(errorToException.apply(e))),
        a -> new TryPath<>(Try.success(a)));
  }

  /**
   * Converts this ValidationPath to an OptionalPath, discarding the error.
   *
   * @return an OptionalPath containing the value if Valid, or empty if Invalid
   */
  public OptionalPath<A> toOptionalPath() {
    return value.fold(
        _ -> new OptionalPath<>(Optional.empty()), a -> new OptionalPath<>(Optional.of(a)));
  }

  // ===== Composable implementation =====

  @Override
  public <B> ValidationPath<E, B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new ValidationPath<>(value.map(mapper), semigroup);
  }

  @Override
  public ValidationPath<E, A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    value.ifValid(consumer);
    return this;
  }

  /**
   * Observes the error value without modifying it (for debugging).
   *
   * @param consumer the action to perform on the error; must not be null
   * @return this path unchanged
   * @throws NullPointerException if consumer is null
   */
  public ValidationPath<E, A> peekInvalid(Consumer<? super E> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    value.ifInvalid(consumer);
    return this;
  }

  // ===== Combinable implementation (short-circuits) =====

  @Override
  public <B, C> ValidationPath<E, C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof ValidationPath<?, ?> otherValidated)) {
      throw new IllegalArgumentException("Cannot zipWith non-ValidationPath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    ValidationPath<E, B> typedOther = (ValidationPath<E, B>) otherValidated;

    // Short-circuit: return first error encountered
    return this.value.fold(
        e -> new ValidationPath<>(Validated.invalid(e), semigroup),
        a ->
            typedOther.value.fold(
                e -> new ValidationPath<>(Validated.invalid(e), semigroup),
                b -> new ValidationPath<>(Validated.valid(combiner.apply(a, b)), semigroup)));
  }

  /**
   * Combines this path with two others using a ternary function (short-circuits).
   *
   * @param second the second path; must not be null
   * @param third the third path; must not be null
   * @param combiner the function to combine the values; must not be null
   * @param <B> the type of the second path's value
   * @param <C> the type of the third path's value
   * @param <D> the type of the combined result
   * @return a new path containing the combined result, or the first error encountered
   */
  public <B, C, D> ValidationPath<E, D> zipWith3(
      ValidationPath<E, B> second,
      ValidationPath<E, C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    // Short-circuit: return first error encountered
    return this.value.fold(
        e -> new ValidationPath<>(Validated.invalid(e), semigroup),
        a ->
            second.value.fold(
                e -> new ValidationPath<>(Validated.invalid(e), semigroup),
                b ->
                    third.value.fold(
                        e -> new ValidationPath<>(Validated.invalid(e), semigroup),
                        c ->
                            new ValidationPath<>(
                                Validated.valid(combiner.apply(a, b, c)), semigroup))));
  }

  // ===== Accumulating implementation (collects errors) =====

  @Override
  public <B, C> ValidationPath<E, C> zipWithAccum(
      Accumulating<E, B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    ValidationPath<E, B> typedOther = castAccumulatingForZipWithAccum(other);

    // Accumulate errors from both
    return switch (this.value) {
      case Invalid<E, A>(var e1) ->
          switch (typedOther.value) {
            case Invalid<E, B>(var e2) ->
                new ValidationPath<>(Validated.invalid(semigroup.combine(e1, e2)), semigroup);
            case Valid<E, B> _ -> new ValidationPath<>(Validated.invalid(e1), semigroup);
          };
      case Valid<E, A>(var a) ->
          switch (typedOther.value) {
            case Invalid<E, B>(var e2) -> new ValidationPath<>(Validated.invalid(e2), semigroup);
            case Valid<E, B>(var b) ->
                new ValidationPath<>(Validated.valid(combiner.apply(a, b)), semigroup);
          };
    };
  }

  @Override
  public <B, C, D> ValidationPath<E, D> zipWith3Accum(
      Accumulating<E, B> second,
      Accumulating<E, C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    // Implement via two zipWithAccum calls to properly accumulate all errors
    return this.zipWithAccum(second, (a, b) -> new Object[] {a, b})
        .zipWithAccum(
            third,
            (pair, c) -> {
              @SuppressWarnings("unchecked")
              A a = (A) pair[0];
              @SuppressWarnings("unchecked")
              B b = (B) pair[1];
              return combiner.apply(a, b, c);
            });
  }

  @Override
  public ValidationPath<E, A> andAlso(Accumulating<E, ?> other) {
    Objects.requireNonNull(other, "other must not be null");

    ValidationPath<E, ?> typedOther = castAccumulatingForAndAlso(other);

    return zipWithAccum(typedOther, (a, _) -> a);
  }

  @Override
  public <B> ValidationPath<E, B> andThen(Accumulating<E, B> other) {
    Objects.requireNonNull(other, "other must not be null");

    ValidationPath<E, B> typedOther = castAccumulatingForAndThen(other);

    return zipWithAccum(typedOther, (_, b) -> b);
  }

  // ===== Chainable implementation (short-circuits) =====

  @Override
  public <B> ValidationPath<E, B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    if (value.isInvalid()) {
      @SuppressWarnings("unchecked")
      ValidationPath<E, B> result = (ValidationPath<E, B>) this;
      return result;
    }

    Chainable<B> result = mapper.apply(value.get());
    Objects.requireNonNull(result, "mapper must not return null");

    if (!(result instanceof ValidationPath<?, ?> validatedPath)) {
      throw new IllegalArgumentException(
          "via mapper must return ValidationPath, got: " + result.getClass());
    }

    @SuppressWarnings("unchecked")
    ValidationPath<E, B> typedResult = (ValidationPath<E, B>) validatedPath;
    return typedResult;
  }

  @Override
  public <B> ValidationPath<E, B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    if (value.isInvalid()) {
      @SuppressWarnings("unchecked")
      ValidationPath<E, B> result = (ValidationPath<E, B>) this;
      return result;
    }

    Chainable<B> result = supplier.get();
    Objects.requireNonNull(result, "supplier must not return null");

    if (!(result instanceof ValidationPath<?, ?> validatedPath)) {
      throw new IllegalArgumentException(
          "then supplier must return ValidationPath, got: " + result.getClass());
    }

    @SuppressWarnings("unchecked")
    ValidationPath<E, B> typedResult = (ValidationPath<E, B>) validatedPath;
    return typedResult;
  }

  // ===== Recoverable implementation =====

  @Override
  public ValidationPath<E, A> recover(Function<? super E, ? extends A> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    if (value.isValid()) {
      return this;
    }
    return new ValidationPath<>(Validated.valid(recovery.apply(value.getError())), semigroup);
  }

  @Override
  public ValidationPath<E, A> recoverWith(
      Function<? super E, ? extends Recoverable<E, A>> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    if (value.isValid()) {
      return this;
    }
    Recoverable<E, A> result = recovery.apply(value.getError());
    Objects.requireNonNull(result, "recovery must not return null");
    if (!(result instanceof ValidationPath<?, ?> validatedPath)) {
      throw new IllegalArgumentException(
          "recovery must return ValidationPath, got: " + result.getClass());
    }
    @SuppressWarnings("unchecked")
    ValidationPath<E, A> typedResult = (ValidationPath<E, A>) validatedPath;
    return typedResult;
  }

  @Override
  public ValidationPath<E, A> orElse(Supplier<? extends Recoverable<E, A>> alternative) {
    Objects.requireNonNull(alternative, "alternative must not be null");
    if (value.isValid()) {
      return this;
    }
    Recoverable<E, A> result = alternative.get();
    Objects.requireNonNull(result, "alternative must not return null");
    if (!(result instanceof ValidationPath<?, ?> validatedPath)) {
      throw new IllegalArgumentException(
          "alternative must return ValidationPath, got: " + result.getClass());
    }
    @SuppressWarnings("unchecked")
    ValidationPath<E, A> typedResult = (ValidationPath<E, A>) validatedPath;
    return typedResult;
  }

  /**
   * Transforms the error type.
   *
   * <p><strong>Note:</strong> This method throws {@link UnsupportedOperationException} for invalid
   * values because error type transformation requires a new {@link org.higherkindedj.hkt.Semigroup}
   * for the transformed error type, which cannot be inferred.
   *
   * <h2>Recommended Alternatives</h2>
   *
   * <p>Use one of these approaches instead:
   *
   * <ul>
   *   <li>{@link #mapErrorWith(Function, Semigroup)} - Transform errors with a new Semigroup
   *   <li>Convert to EitherPath, transform, then convert back:
   *       <pre>{@code
   * ValidationPath<NewError, A> result = validationPath
   *     .toEitherPath()
   *     .mapError(oldError -> newError)
   *     .toValidationPath(newErrorSemigroup);
   * }</pre>
   * </ul>
   *
   * <p>For valid values, this method returns a ValidationPath with a placeholder Semigroup that
   * will throw if accumulation is attempted. This allows the value to flow through pipelines where
   * no accumulation occurs.
   *
   * @param mapper the function to transform the error; must not be null
   * @param <E2> the new error type
   * @return a new ValidationPath with the transformed error type (for valid values only)
   * @throws UnsupportedOperationException if this ValidationPath contains an invalid value
   * @throws NullPointerException if mapper is null
   * @see #mapErrorWith(Function, Semigroup)
   * @see #toEitherPath()
   */
  @Override
  public <E2> ValidationPath<E2, A> mapError(Function<? super E, ? extends E2> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    // Note: mapError changes the error type, so we can't preserve the semigroup directly
    // The user must provide a new ValidationPath with appropriate semigroup when needed
    return value.fold(
        e -> {
          // We can't create a proper ValidationPath<E2, A> without a Semigroup<E2>
          // Return with a null semigroup - operations requiring accumulation will fail
          // This matches the semantic that error transformation changes the domain
          throw new UnsupportedOperationException(
              "mapError on ValidationPath requires creating a new ValidationPath with "
                  + "an appropriate Semigroup for the new error type. Use toEitherPath().mapError() "
                  + "then convert back with a new Semigroup instead.");
        },
        a -> {
          // For valid values, we need a Semigroup<E2> which we don't have
          // Use a placeholder that will fail if accumulation is attempted
          @SuppressWarnings("unchecked")
          Semigroup<E2> placeholderSemigroup =
              (e1, e2) -> {
                throw new UnsupportedOperationException(
                    "Cannot accumulate errors after mapError. Create a new ValidationPath with proper Semigroup.");
              };
          return new ValidationPath<>(Validated.valid(a), placeholderSemigroup);
        });
  }

  /**
   * Transforms the error type with a new Semigroup for the transformed error type.
   *
   * <p>This is the preferred way to transform errors while maintaining accumulation capability.
   *
   * @param mapper the function to transform the error; must not be null
   * @param newSemigroup the Semigroup for the new error type; must not be null
   * @param <E2> the new error type
   * @return a ValidationPath with the transformed error type and new Semigroup
   */
  public <E2> ValidationPath<E2, A> mapErrorWith(
      Function<? super E, ? extends E2> mapper, Semigroup<E2> newSemigroup) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    Objects.requireNonNull(newSemigroup, "newSemigroup must not be null");
    return new ValidationPath<>(value.mapError(mapper), newSemigroup);
  }

  // ===== Focus Bridge Methods =====

  /**
   * Applies a {@link FocusPath} to navigate within the Valid value.
   *
   * <p>This bridges from the effect domain to the optics domain, allowing structural navigation
   * inside a Validated context.
   *
   * @param path the FocusPath to apply; must not be null
   * @param <B> the focused type
   * @return a new ValidationPath containing the focused value if Valid
   * @throws NullPointerException if path is null
   */
  public <B> ValidationPath<E, B> focus(FocusPath<A, B> path) {
    Objects.requireNonNull(path, "path must not be null");
    return map(path::get);
  }

  /**
   * Applies an {@link AffinePath} to navigate within the Valid value.
   *
   * <p>This bridges from the effect domain to the optics domain. If the AffinePath doesn't match,
   * an Invalid is returned with the provided error value. This allows converting partial optics
   * failures to validation errors.
   *
   * @param path the AffinePath to apply; must not be null
   * @param errorIfAbsent the error to use if the path doesn't match; must not be null
   * @param <B> the focused type
   * @return a new ValidationPath containing the focused value or error
   * @throws NullPointerException if path or errorIfAbsent is null
   */
  public <B> ValidationPath<E, B> focus(AffinePath<A, B> path, E errorIfAbsent) {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(errorIfAbsent, "errorIfAbsent must not be null");
    return via(
        a ->
            path.getOptional(a)
                .<ValidationPath<E, B>>map(b -> Path.valid(b, semigroup))
                .orElseGet(() -> Path.invalid(errorIfAbsent, semigroup)));
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof ValidationPath<?, ?> other)) return false;
    return value.equals(other.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return "ValidationPath(" + value + ")";
  }

  // ===== Defensive checks for sealed interface (excluded from coverage) =====
  // These methods contain instanceof checks that are always true in normal usage because
  // Accumulating is a sealed interface that only permits ValidationPath. The checks exist
  // as defensive measures and are excluded from coverage via @Generated annotation.

  @Generated("sealed-interface-defensive-check")
  @SuppressWarnings("unchecked")
  private static <E, B> ValidationPath<E, B> castAccumulatingForZipWithAccum(
      Accumulating<E, B> other) {
    if (!(other instanceof ValidationPath<?, ?> otherValidated)) {
      throw new IllegalArgumentException(
          "Cannot zipWithAccum non-ValidationPath: " + other.getClass());
    }
    return (ValidationPath<E, B>) otherValidated;
  }

  @Generated("sealed-interface-defensive-check")
  @SuppressWarnings("unchecked")
  private static <E> ValidationPath<E, ?> castAccumulatingForAndAlso(Accumulating<E, ?> other) {
    if (!(other instanceof ValidationPath<?, ?> otherValidated)) {
      throw new IllegalArgumentException("Cannot andAlso non-ValidationPath: " + other.getClass());
    }
    return (ValidationPath<E, ?>) otherValidated;
  }

  @Generated("sealed-interface-defensive-check")
  @SuppressWarnings("unchecked")
  private static <E, B> ValidationPath<E, B> castAccumulatingForAndThen(Accumulating<E, B> other) {
    if (!(other instanceof ValidationPath<?, ?> otherValidated)) {
      throw new IllegalArgumentException("Cannot andThen non-ValidationPath: " + other.getClass());
    }
    return (ValidationPath<E, B>) otherValidated;
  }
}
