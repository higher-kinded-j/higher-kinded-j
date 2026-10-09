// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.extensions;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Static utility methods for using {@link Traversal} with hkj-core types (Maybe, Either,
 * Validated).
 *
 * <p>This class provides ergonomic helpers for common patterns when working with traversals and
 * operations that can fail or accumulate errors.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * import static org.higherkindedj.optics.extensions.TraversalExtensions.*;
 *
 * Traversal<List<User>, String> allEmails =
 *     Traversals.forList().andThen(UserLenses.email().asTraversal());
 *
 * // Get all values as Maybe (empty if no targets)
 * Maybe<List<String>> emails = getAllMaybe(allEmails, users);
 *
 * // Modify with validation (keep only the first error)
 * Either<String, List<User>> result = modifyAllEither(
 *     allEmails,
 *     email -> email.contains("@")
 *         ? Either.right(email)
 *         : Either.left("Invalid email: " + email),
 *     users
 * );
 *
 * // Modify with validation (accumulate all errors)
 * Validated<List<String>, List<User>> results = modifyAllValidated(
 *     allEmails,
 *     email -> email.contains("@")
 *         ? Validated.valid(email)
 *         : Validated.invalid("Invalid: " + email),
 *     users
 * );
 * }</pre>
 */
@NullMarked
public final class TraversalExtensions {
  /** Private constructor to prevent instantiation. */
  private TraversalExtensions() {}

  /**
   * Gets all targets from a {@link Traversal} wrapped in a {@link Maybe}.
   *
   * <p>Returns {@code Maybe.just(list)} if the list is non-empty, {@code Maybe.nothing()} if empty.
   *
   * @param traversal The traversal to extract from
   * @param source The source structure
   * @param <S> The type of the source structure
   * @param <A> The type of the focused parts
   * @return {@code Maybe.just(list)} if non-empty, {@code Maybe.nothing()} otherwise
   */
  public static <S extends @Nullable Object, A extends @Nullable Object> Maybe<List<A>> getAllMaybe(
      Traversal<S, A> traversal, S source) {
    List<A> results = Traversals.getAll(traversal, source);
    return results.isEmpty() ? Maybe.nothing() : Maybe.just(results);
  }

  /**
   * Modifies all targets with a function that returns {@link Maybe}.
   *
   * <p>This is "all or nothing" - if any modification returns {@code Maybe.nothing()}, the entire
   * operation returns {@code Maybe.nothing()}.
   *
   * <p>The function sees each focus as it is, a null one included, and must give back a non-null
   * value to write.
   *
   * @param traversal The traversal to modify through
   * @param f The modification function returning {@code Maybe}
   * @param source The source structure
   * @param <S> The type of the source structure
   * @param <A> The type of the focused parts
   * @return {@code Maybe.just(updatedSource)} if all modifications succeeded, {@code
   *     Maybe.nothing()} if any failed
   */
  public static <S, A extends @Nullable Object> Maybe<S> modifyAllMaybe(
      Traversal<S, A> traversal, Function<A, Maybe<@NonNull A>> f, S source) {
    Kind<MaybeKind.Witness, S> result =
        traversal.modifyF(a -> MAYBE.widen(f.apply(a)), source, MaybeMonad.INSTANCE);
    return MAYBE.narrow(result);
  }

  /**
   * Modifies all targets with a function that returns {@link Either}.
   *
   * <p>The result keeps only the first error, in traversal order. The function still runs on every
   * target: the {@code Either} shapes the answer, not the work.
   *
   * <p>The function sees each focus as it is, a null one included, and must give back a non-null
   * value to write.
   *
   * @param traversal The traversal to modify through
   * @param f The modification function returning {@code Either}
   * @param source The source structure
   * @param <E> The type of the error value
   * @param <S> The type of the source structure
   * @param <A> The type of the focused parts
   * @return {@code Either.right(updatedSource)} if all modifications succeeded, {@code
   *     Either.left(firstError)} otherwise
   */
  public static <E, S, A extends @Nullable Object> Either<E, S> modifyAllEither(
      Traversal<S, A> traversal, Function<A, Either<E, @NonNull A>> f, S source) {
    Kind<EitherKind.Witness<E>, S> result =
        traversal.modifyF(a -> EITHER.widen(f.apply(a)), source, EitherMonad.instance());
    return EITHER.narrow(result);
  }

  /**
   * Modifies all targets with a function that returns {@link Validated}.
   *
   * <p>This accumulates all errors: if multiple modifications fail, all error messages are
   * collected into a list.
   *
   * <p>The function sees each focus as it is, a null one included, and must give back a non-null
   * value to write.
   *
   * @param traversal The traversal to modify through
   * @param f The modification function returning {@code Validated}
   * @param source The source structure
   * @param <E> The type of the error value
   * @param <S> The type of the source structure
   * @param <A> The type of the focused parts
   * @return {@code Validated.valid(updatedSource)} if all modifications succeeded, {@code
   *     Validated.invalid(listOfErrors)} otherwise
   */
  public static <E, S, A extends @Nullable Object> Validated<List<E>, S> modifyAllValidated(
      Traversal<S, A> traversal, Function<A, Validated<E, @NonNull A>> f, S source) {
    Kind<ValidatedKind.Witness<List<E>>, S> result =
        traversal.modifyF(
            a -> VALIDATED.widen(f.apply(a).mapError(List::of)),
            source,
            ValidatedMonad.instance(Semigroups.list()));
    return VALIDATED.narrow(result);
  }

  /**
   * Filters and modifies targets with a function that returns {@link Maybe}.
   *
   * <p>Only targets where the function returns {@code Maybe.just(newValue)} are modified. Targets
   * where the function returns {@code Maybe.nothing()} are left unchanged.
   *
   * <p>The function sees each focus as it is, a null one included, and must give back a non-null
   * value to write.
   *
   * @param traversal The traversal to modify through
   * @param f The modification function returning {@code Maybe}
   * @param source The source structure
   * @param <S> The type of the source structure
   * @param <A> The type of the focused parts
   * @return The updated source structure with selective modifications
   */
  public static <S, A extends @Nullable Object> S modifyWherePossible(
      Traversal<S, A> traversal, Function<A, Maybe<@NonNull A>> f, S source) {
    return Traversals.modify(
        traversal,
        a -> {
          Maybe<@NonNull A> replacement = f.apply(a);
          return replacement.isJust() ? replacement.get() : a;
        },
        source);
  }

  /**
   * Counts how many targets match a validation function.
   *
   * <p>The validator sees each focus as it is, a null one included, and must give back a non-null
   * value in a Right.
   *
   * @param traversal The traversal to check
   * @param validator Validation function that returns {@code Either.right} for valid targets
   * @param source The source structure
   * @param <E> The type of the error value
   * @param <S> The type of the source structure
   * @param <A> The type of the focused parts
   * @return The number of targets that passed validation
   */
  public static <E, S, A extends @Nullable Object> int countValid(
      Traversal<S, A> traversal, Function<A, Either<E, @NonNull A>> validator, S source) {
    return (int)
        Traversals.getAll(traversal, source).stream()
            .filter(a -> validator.apply(a).isRight())
            .count();
  }

  /**
   * Collects all validation errors from targets.
   *
   * <p>The validator sees each focus as it is, a null one included, and must give back a non-null
   * value in a Right.
   *
   * @param traversal The traversal to validate
   * @param validator Validation function
   * @param source The source structure
   * @param <E> The type of the error value
   * @param <S> The type of the source structure
   * @param <A> The type of the focused parts
   * @return List of all validation errors
   */
  public static <E, S, A extends @Nullable Object> List<E> collectErrors(
      Traversal<S, A> traversal, Function<A, Either<E, @NonNull A>> validator, S source) {
    return Traversals.getAll(traversal, source).stream()
        .map(validator)
        .filter(Either::isLeft)
        .map(Either::getLeft)
        .toList();
  }
}
