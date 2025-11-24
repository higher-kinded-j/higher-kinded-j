// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fluent;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.constant.ConstApplicative;
import org.higherkindedj.hkt.constant.ConstKind;
import org.higherkindedj.hkt.constant.ConstKindHelper;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.jspecify.annotations.NullMarked;

/**
 * Fluent API for common optic operations with Java-friendly naming conventions.
 *
 * <p>This class provides two styles of optic operations:
 *
 * <ul>
 *   <li><b>Static methods</b> - Direct, concise operations for simple cases
 *   <li><b>Fluent builders</b> - Method chaining for more explicit workflows
 * </ul>
 *
 * <h2>Static Methods (Simple &amp; Direct)</h2>
 *
 * <pre>{@code
 * // Get a value
 * String name = OpticOps.get(person, PersonLenses.name());
 *
 * // Set a value
 * Person updated = OpticOps.set(person, PersonLenses.age(), 30);
 *
 * // Modify a value
 * Person modified = OpticOps.modify(person, PersonLenses.age(), age -> age + 1);
 *
 * // Get all values
 * List<String> names = OpticOps.getAll(team, TeamTraversals.playerNames());
 * }</pre>
 *
 * <h2>Fluent Builders (Explicit Workflows)</h2>
 *
 * <pre>{@code
 * // Getting values
 * String name = OpticOps.getting(person).through(PersonLenses.name());
 * Optional<Address> addr = OpticOps.getting(person).maybeThrough(addressPrism);
 * List<String> names = OpticOps.getting(team).allThrough(playerNames);
 *
 * // Setting values
 * Person updated = OpticOps.setting(person).through(PersonLenses.age(), 30);
 *
 * // Modifying values
 * Person modified = OpticOps.modifying(person).through(PersonLenses.age(), age -> age + 1);
 *
 * // Querying
 * boolean hasAdults = OpticOps.querying(team)
 *     .anyMatch(playerAges, age -> age >= 18);
 * }</pre>
 *
 * <h2>When to Use Each Style</h2>
 *
 * <ul>
 *   <li><b>Static methods</b>: Simple, one-off operations where brevity is preferred
 *   <li><b>Fluent builders</b>: Complex workflows, or when IDE autocomplete guidance is helpful
 * </ul>
 */
@NullMarked
public final class OpticOps {

  private OpticOps() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Converts a Traversal to a Fold by running it with the Id monad.
   *
   * @param traversal The traversal to convert
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Fold that represents the traversal
   */
  private static <S, A> Fold<S, A> traversalToFold(Traversal<S, A> traversal) {
    return new Fold<>() {
      @Override
      public <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f, S source) {
        // Use the Const applicative to fold over the traversal efficiently
        // This is the standard functional programming approach that avoids side effects
        ConstApplicative<M> constApp = new ConstApplicative<>(monoid);

        // Apply the traversal with the Const applicative
        // The traversal will accumulate values using the monoid whilst traversing
        Kind<ConstKind.Witness<M>, S> result =
            traversal.modifyF(
                a -> ConstKindHelper.CONST.widen(new Const<>(f.apply(a))), source, constApp);

        // Extract the accumulated result from the Const
        Const<M, S> finalConst = ConstKindHelper.CONST.narrow(result);
        return finalConst.value();
      }
    };
  }

  // ============================================================================
  // Static Methods - Direct Operations
  // ============================================================================

  /**
   * Gets the value focused by a {@link Getter} or {@link Lens}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * String name = OpticOps.get(person, PersonLenses.name());
   * }</pre>
   *
   * @param source The source structure
   * @param getter The optic to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return The focused value
   */
  public static <S, A> A get(S source, Getter<S, A> getter) {
    return getter.get(source);
  }

  /**
   * Gets the value focused by a {@link Lens}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * String name = OpticOps.get(person, PersonLenses.name());
   * }</pre>
   *
   * @param source The source structure
   * @param lens The lens to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return The focused value
   */
  public static <S, A> A get(S source, Lens<S, A> lens) {
    return lens.get(source);
  }

  /**
   * Gets the first value focused by a {@link Fold}, {@link Traversal}, or {@link
   * org.higherkindedj.optics.Prism}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Optional<Address> address = OpticOps.preview(person, addressPrism);
   * Optional<String> firstName = OpticOps.preview(team, playerNames);
   * }</pre>
   *
   * @param source The source structure
   * @param fold The optic to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return An {@link Optional} containing the first focused value, or empty if none exist
   */
  public static <S, A> Optional<A> preview(S source, Fold<S, A> fold) {
    return fold.preview(source);
  }

  /**
   * Gets the first value focused by a {@link Traversal}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Optional<String> firstName = OpticOps.preview(team, playerNames);
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return An {@link Optional} containing the first focused value, or empty if none exist
   */
  public static <S, A> Optional<A> preview(S source, Traversal<S, A> traversal) {
    return traversalToFold(traversal).preview(source);
  }

  /**
   * Gets all values focused by a {@link Fold} or {@link Traversal}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * List<String> names = OpticOps.getAll(team, TeamTraversals.playerNames());
   * List<Integer> scores = OpticOps.getAll(league, allPlayerScores);
   * }</pre>
   *
   * @param source The source structure
   * @param fold The optic to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A list of all focused values
   */
  public static <S, A> List<A> getAll(S source, Fold<S, A> fold) {
    return fold.getAll(source);
  }

  /**
   * Gets all values focused by a {@link Traversal}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * List<String> names = OpticOps.getAll(team, TeamTraversals.playerNames());
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A list of all focused values
   */
  public static <S, A> List<A> getAll(S source, Traversal<S, A> traversal) {
    return traversalToFold(traversal).getAll(source);
  }

  /**
   * Sets a new value through a {@link Lens}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Person updated = OpticOps.set(person, PersonLenses.age(), 30);
   * }</pre>
   *
   * @param source The source structure
   * @param lens The lens to focus with
   * @param value The new value to set
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A new structure with the value updated
   */
  public static <S, A> S set(S source, Lens<S, A> lens, A value) {
    return lens.set(value, source);
  }

  /**
   * Sets all values focused by a {@link Traversal}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Team allScores100 = OpticOps.setAll(team, playerScores, 100);
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param value The new value to set for all focused elements
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A new structure with all focused values updated
   */
  public static <S, A> S setAll(S source, Traversal<S, A> traversal, A value) {
    return Traversals.modify(traversal, ignored -> value, source);
  }

  /**
   * Modifies the value focused by a {@link Lens}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Person older = OpticOps.modify(person, PersonLenses.age(), age -> age + 1);
   * }</pre>
   *
   * @param source The source structure
   * @param lens The lens to focus with
   * @param modifier The function to transform the focused value
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A new structure with the value modified
   */
  public static <S, A> S modify(S source, Lens<S, A> lens, Function<A, A> modifier) {
    return lens.modify(modifier, source);
  }

  /**
   * Modifies all values focused by a {@link Traversal}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Team bonusApplied = OpticOps.modifyAll(team, playerScores, score -> score + 10);
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param modifier The function to transform each focused value
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A new structure with all focused values modified
   */
  public static <S, A> S modifyAll(S source, Traversal<S, A> traversal, Function<A, A> modifier) {
    return Traversals.modify(traversal, modifier, source);
  }

  /**
   * Modifies the value focused by a {@link Lens} with an effectful function.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<IO, Person> result = OpticOps.modifyF(
   *     person,
   *     PersonLenses.name(),
   *     name -> fetchNameFromDatabase(name),
   *     ioFunctor
   * );
   * }</pre>
   *
   * @param source The source structure
   * @param lens The lens to focus with
   * @param modifier The effectful function to transform the focused value
   * @param functor The functor instance for the effect type
   * @param <F> The effect type witness
   * @param <S> The source type
   * @param <A> The focused value type
   * @return The modified structure wrapped in the effect
   */
  public static <F, S, A> Kind<F, S> modifyF(
      S source, Lens<S, A> lens, Function<A, Kind<F, A>> modifier, Functor<F> functor) {
    return lens.modifyF(modifier, source, functor);
  }

  /**
   * Modifies all values focused by a {@link Traversal} with an effectful function.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<IO, Team> result = OpticOps.modifyAllF(
   *     team,
   *     playerNames,
   *     name -> validateNameInDatabase(name),
   *     ioApplicative
   * );
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param modifier The effectful function to transform each focused value
   * @param applicative The applicative instance for the effect type
   * @param <F> The effect type witness
   * @param <S> The source type
   * @param <A> The focused value type
   * @return The modified structure wrapped in the effect
   */
  public static <F, S, A> Kind<F, S> modifyAllF(
      S source,
      Traversal<S, A> traversal,
      Function<A, Kind<F, A>> modifier,
      Applicative<F> applicative) {
    return traversal.modifyF(modifier, source, applicative);
  }

  // ============================================================================
  // Validation-Aware Operations
  // ============================================================================

  /**
   * Modifies the value focused by a {@link Lens} with Either-based validation.
   *
   * <p>This method provides a convenient way to modify a value with validation that can fail. The
   * validator function returns {@code Either.right(validValue)} on success or {@code
   * Either.left(error)} on failure.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Function<String, Either<String, String>> validateEmail = email ->
   *     email.contains("@")
   *         ? Either.right(email.toLowerCase())
   *         : Either.left("Invalid email format");
   *
   * Either<String, User> result = OpticOps.modifyEither(
   *     user,
   *     UserLenses.email(),
   *     validateEmail
   * );
   *
   * result.fold(
   *     error -> System.out.println("Validation failed: " + error),
   *     validUser -> System.out.println("Updated user: " + validUser)
   * );
   * }</pre>
   *
   * @param source The source structure
   * @param lens The lens to focus with
   * @param validator Function that returns Either.right(validValue) or Either.left(error)
   * @param <E> The error type
   * @param <S> The source type
   * @param <A> The focused value type
   * @return Either.right(updated structure) if validation succeeds, Either.left(error) otherwise
   */
  public static <E, S, A> org.higherkindedj.hkt.either.Either<E, S> modifyEither(
      S source, Lens<S, A> lens, Function<A, org.higherkindedj.hkt.either.Either<E, A>> validator) {
    A currentValue = lens.get(source);
    return validator.apply(currentValue).map(validatedValue -> lens.set(validatedValue, source));
  }

  /**
   * Modifies the value focused by a {@link Lens} with Maybe-based validation.
   *
   * <p>This method provides a convenient way to modify a value with optional validation. The
   * validator function returns {@code Maybe.just(validValue)} on success or {@code Maybe.nothing()}
   * on failure.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Function<Integer, Maybe<Integer>> validateAge = age ->
   *     (age >= 0 && age <= 150) ? Maybe.just(age) : Maybe.nothing();
   *
   * Maybe<Person> result = OpticOps.modifyMaybe(
   *     person,
   *     PersonLenses.age(),
   *     validateAge
   * );
   *
   * result.fold(
   *     () -> System.out.println("Validation failed"),
   *     validPerson -> System.out.println("Updated person: " + validPerson)
   * );
   * }</pre>
   *
   * @param source The source structure
   * @param lens The lens to focus with
   * @param validator Function that returns Maybe.just(validValue) or Maybe.nothing()
   * @param <S> The source type
   * @param <A> The focused value type
   * @return Maybe.just(updated structure) if validation succeeds, Maybe.nothing() otherwise
   */
  public static <S, A> org.higherkindedj.hkt.maybe.Maybe<S> modifyMaybe(
      S source, Lens<S, A> lens, Function<A, org.higherkindedj.hkt.maybe.Maybe<A>> validator) {
    A currentValue = lens.get(source);
    return validator.apply(currentValue).map(validatedValue -> lens.set(validatedValue, source));
  }

  /**
   * Modifies all values focused by a {@link Traversal} with Validated-based error accumulation.
   *
   * <p>This method validates and modifies all focused values, accumulating all errors rather than
   * short-circuiting on the first failure. This is ideal when you want to collect all validation
   * errors and present them to the user at once.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Function<Double, Validated<String, Double>> validatePrice = price -> {
   *     if (price < 0) {
   *         return Validated.invalid("Price cannot be negative");
   *     } else if (price > 10000) {
   *         return Validated.invalid("Price exceeds maximum");
   *     } else {
   *         return Validated.valid(price);
   *     }
   * };
   *
   * Validated<List<String>, Order> result = OpticOps.modifyAllValidated(
   *     order,
   *     OrderTraversals.itemPrices(),
   *     validatePrice
   * );
   *
   * result.fold(
   *     errors -> System.out.println("Validation errors: " + errors),
   *     validOrder -> System.out.println("All prices valid: " + validOrder)
   * );
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param validator Function that returns Validated.valid or Validated.invalid
   * @param <E> The error type
   * @param <S> The source type
   * @param <A> The focused value type
   * @return Validated.valid(updated structure) if all validations succeed, Validated.invalid(all
   *     errors) otherwise
   */
  public static <E, S, A> org.higherkindedj.hkt.validated.Validated<List<E>, S> modifyAllValidated(
      S source,
      Traversal<S, A> traversal,
      Function<A, org.higherkindedj.hkt.validated.Validated<E, A>> validator) {
    // Create applicative for Validated with List semigroup for error accumulation

    Applicative<org.higherkindedj.hkt.validated.ValidatedKind.Witness<List<E>>> applicative =
        org.higherkindedj.hkt.validated.ValidatedMonad.instance(
            org.higherkindedj.hkt.Semigroups.list());

    // Lift the validator to work with List<E> errors
    Function<
            A,
            org.higherkindedj.hkt.Kind<
                org.higherkindedj.hkt.validated.ValidatedKind.Witness<List<E>>, A>>
        liftedValidator =
            a -> {
              org.higherkindedj.hkt.validated.Validated<E, A> validated = validator.apply(a);
              // Convert Validated<E, A> to Validated<List<E>, A>
              org.higherkindedj.hkt.validated.Validated<List<E>, A> result =
                  validated.bimap(List::of, Function.identity());
              return org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED.widen(result);
            };

    // Use traversal's modifyF with the applicative
    Kind<org.higherkindedj.hkt.validated.ValidatedKind.Witness<List<E>>, S> resultKind =
        traversal.modifyF(liftedValidator, source, applicative);

    return org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED.narrow(resultKind);
  }

  /**
   * Modifies all values focused by a {@link Traversal} with Either-based short-circuiting.
   *
   * <p>This method validates and modifies all focused values, but stops at the first validation
   * error (short-circuits). This is ideal when you want to fail fast and don't need to collect all
   * errors.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Function<String, Either<String, String>> validateUsername = username ->
   *     username.length() >= 3
   *         ? Either.right(username.toLowerCase())
   *         : Either.left("Username too short: " + username);
   *
   * Either<String, Team> result = OpticOps.modifyAllEither(
   *     team,
   *     TeamTraversals.playerUsernames(),
   *     validateUsername
   * );
   *
   * result.fold(
   *     error -> System.out.println("First error: " + error),
   *     validTeam -> System.out.println("All usernames valid: " + validTeam)
   * );
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param validator Function that returns Either.right or Either.left
   * @param <E> The error type
   * @param <S> The source type
   * @param <A> The focused value type
   * @return Either.right(updated structure) if all validations succeed, Either.left(first error)
   *     otherwise
   */
  public static <E, S, A> org.higherkindedj.hkt.either.Either<E, S> modifyAllEither(
      S source,
      Traversal<S, A> traversal,
      Function<A, org.higherkindedj.hkt.either.Either<E, A>> validator) {
    // Create applicative for Either (short-circuits on first Left)
    Applicative<org.higherkindedj.hkt.either.EitherKind.Witness<E>> applicative =
        org.higherkindedj.hkt.either.EitherMonad.instance();

    // Lift the validator to the Kind type
    Function<A, org.higherkindedj.hkt.Kind<org.higherkindedj.hkt.either.EitherKind.Witness<E>, A>>
        liftedValidator =
            a -> org.higherkindedj.hkt.either.EitherKindHelper.EITHER.widen(validator.apply(a));

    // Use traversal's modifyF with the applicative
    Kind<org.higherkindedj.hkt.either.EitherKind.Witness<E>, S> resultKind =
        traversal.modifyF(liftedValidator, source, applicative);

    return org.higherkindedj.hkt.either.EitherKindHelper.EITHER.narrow(resultKind);
  }

  // ============================================================================
  // Query Operations
  // ============================================================================

  /**
   * Checks if any focused element matches the given predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * boolean hasExpensive = OpticOps.exists(order, itemPrices, price -> price > 100);
   * }</pre>
   *
   * @param source The source structure
   * @param fold The optic to focus with
   * @param predicate The predicate to test
   * @param <S> The source type
   * @param <A> The focused value type
   * @return {@code true} if any focused element matches
   */
  public static <S, A> boolean exists(S source, Fold<S, A> fold, Predicate<A> predicate) {
    return fold.exists(predicate, source);
  }

  /**
   * Checks if any focused element matches the given predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * boolean hasExpensive = OpticOps.exists(order, itemPrices, price -> price > 100);
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param predicate The predicate to test
   * @param <S> The source type
   * @param <A> The focused value type
   * @return {@code true} if any focused element matches
   */
  public static <S, A> boolean exists(S source, Traversal<S, A> traversal, Predicate<A> predicate) {
    return traversalToFold(traversal).exists(predicate, source);
  }

  /**
   * Checks if all focused elements match the given predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * boolean allAdults = OpticOps.all(team, playerAges, age -> age >= 18);
   * }</pre>
   *
   * @param source The source structure
   * @param fold The optic to focus with
   * @param predicate The predicate to test
   * @param <S> The source type
   * @param <A> The focused value type
   * @return {@code true} if all focused elements match
   */
  public static <S, A> boolean all(S source, Fold<S, A> fold, Predicate<A> predicate) {
    return fold.all(predicate, source);
  }

  /**
   * Checks if all focused elements match the given predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * boolean allAdults = OpticOps.all(team, playerAges, age -> age >= 18);
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param predicate The predicate to test
   * @param <S> The source type
   * @param <A> The focused value type
   * @return {@code true} if all focused elements match
   */
  public static <S, A> boolean all(S source, Traversal<S, A> traversal, Predicate<A> predicate) {
    return traversalToFold(traversal).all(predicate, source);
  }

  /**
   * Finds the first focused element that matches the given predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Optional<Item> expensive = OpticOps.find(order, items, item -> item.price() > 100);
   * }</pre>
   *
   * @param source The source structure
   * @param fold The optic to focus with
   * @param predicate The predicate to test
   * @param <S> The source type
   * @param <A> The focused value type
   * @return An {@link Optional} containing the first matching element, or empty if none match
   */
  public static <S, A> Optional<A> find(S source, Fold<S, A> fold, Predicate<A> predicate) {
    return fold.find(predicate, source);
  }

  /**
   * Finds the first focused element that matches the given predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Optional<Item> expensive = OpticOps.find(order, items, item -> item.price() > 100);
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param predicate The predicate to test
   * @param <S> The source type
   * @param <A> The focused value type
   * @return An {@link Optional} containing the first matching element, or empty if none match
   */
  public static <S, A> Optional<A> find(
      S source, Traversal<S, A> traversal, Predicate<A> predicate) {
    return traversalToFold(traversal).find(predicate, source);
  }

  /**
   * Counts the number of focused elements.
   *
   * <p>Example:
   *
   * <pre>{@code
   * int playerCount = OpticOps.count(team, players);
   * }</pre>
   *
   * @param source The source structure
   * @param fold The optic to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return The number of focused elements
   */
  public static <S, A> int count(S source, Fold<S, A> fold) {
    return fold.length(source);
  }

  /**
   * Counts the number of focused elements.
   *
   * <p>Example:
   *
   * <pre>{@code
   * int playerCount = OpticOps.count(team, players);
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return The number of focused elements
   */
  public static <S, A> int count(S source, Traversal<S, A> traversal) {
    return traversalToFold(traversal).length(source);
  }

  /**
   * Checks if there are no focused elements.
   *
   * <p>Example:
   *
   * <pre>{@code
   * boolean noItems = OpticOps.isEmpty(order, items);
   * }</pre>
   *
   * @param source The source structure
   * @param fold The optic to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return {@code true} if there are no focused elements
   */
  public static <S, A> boolean isEmpty(S source, Fold<S, A> fold) {
    return fold.isEmpty(source);
  }

  /**
   * Checks if there are no focused elements.
   *
   * <p>Example:
   *
   * <pre>{@code
   * boolean noItems = OpticOps.isEmpty(order, items);
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return {@code true} if there are no focused elements
   */
  public static <S, A> boolean isEmpty(S source, Traversal<S, A> traversal) {
    return traversalToFold(traversal).isEmpty(source);
  }

  // ============================================================================
  // Fluent Builder Factories
  // ============================================================================

  /**
   * Starts a fluent workflow for getting values.
   *
   * <p>Example:
   *
   * <pre>{@code
   * String name = OpticOps.getting(person).through(PersonLenses.name());
   * Optional<Address> addr = OpticOps.getting(person).maybeThrough(addressPrism);
   * List<String> names = OpticOps.getting(team).allThrough(playerNames);
   * }</pre>
   *
   * @param source The source structure
   * @param <S> The source type
   * @return A {@link GetBuilder} for fluent method chaining
   */
  public static <S> GetBuilder<S> getting(S source) {
    return new GetBuilder<>(source);
  }

  /**
   * Starts a fluent workflow for setting values.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Person updated = OpticOps.setting(person).through(PersonLenses.age(), 30);
   * }</pre>
   *
   * @param source The source structure
   * @param <S> The source type
   * @return A {@link SetBuilder} for fluent method chaining
   */
  public static <S> SetBuilder<S> setting(S source) {
    return new SetBuilder<>(source);
  }

  /**
   * Starts a fluent workflow for modifying values.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Person older = OpticOps.modifying(person).through(PersonLenses.age(), age -> age + 1);
   * }</pre>
   *
   * @param source The source structure
   * @param <S> The source type
   * @return A {@link ModifyBuilder} for fluent method chaining
   */
  public static <S> ModifyBuilder<S> modifying(S source) {
    return new ModifyBuilder<>(source);
  }

  /**
   * Starts a fluent workflow for querying values.
   *
   * <p>Example:
   *
   * <pre>{@code
   * boolean hasAdults = OpticOps.querying(team).anyMatch(playerAges, age -> age >= 18);
   * int count = OpticOps.querying(team).count(players);
   * }</pre>
   *
   * @param source The source structure
   * @param <S> The source type
   * @return A {@link QueryBuilder} for fluent method chaining
   */
  public static <S> QueryBuilder<S> querying(S source) {
    return new QueryBuilder<>(source);
  }

  /**
   * Starts a fluent workflow for validation-aware modifications.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either<String, User> result = OpticOps.modifyingWithValidation(user)
   *     .throughEither(emailLens, this::validateEmail);
   *
   * Validated<List<String>, Order> result = OpticOps.modifyingWithValidation(order)
   *     .allThroughValidated(itemPrices, this::validatePrice);
   * }</pre>
   *
   * @param source The source structure
   * @param <S> The source type
   * @return A {@link ModifyingWithValidation} builder for fluent method chaining
   */
  public static <S> ModifyingWithValidation<S> modifyingWithValidation(S source) {
    return new ModifyingWithValidation<>(source);
  }

  // ============================================================================
  // Fluent Builders
  // ============================================================================

  /**
   * Builder for fluent get/read operations.
   *
   * @param <S> The source type
   */
  public static final class GetBuilder<S> {
    private final S source;

    GetBuilder(S source) {
      this.source = source;
    }

    /**
     * Gets the value through a {@link Getter} or {@link Lens}.
     *
     * @param getter The optic to focus with
     * @param <A> The focused value type
     * @return The focused value
     */
    public <A> A through(Getter<S, A> getter) {
      return getter.get(source);
    }

    /**
     * Gets the value through a {@link Lens}.
     *
     * @param lens The lens to focus with
     * @param <A> The focused value type
     * @return The focused value
     */
    public <A> A through(Lens<S, A> lens) {
      return lens.get(source);
    }

    /**
     * Gets the first value through a {@link Fold}, returning {@link Optional}.
     *
     * @param fold The optic to focus with
     * @param <A> The focused value type
     * @return An {@link Optional} containing the first focused value, or empty if none exist
     */
    public <A> Optional<A> maybeThrough(Fold<S, A> fold) {
      return fold.preview(source);
    }

    /**
     * Gets the first value through a {@link Traversal}, returning {@link Optional}.
     *
     * @param traversal The traversal to focus with
     * @param <A> The focused value type
     * @return An {@link Optional} containing the first focused value, or empty if none exist
     */
    public <A> Optional<A> maybeThrough(Traversal<S, A> traversal) {
      return traversalToFold(traversal).preview(source);
    }

    /**
     * Gets all values through a {@link Fold} or {@link Traversal}.
     *
     * @param fold The optic to focus with
     * @param <A> The focused value type
     * @return A list of all focused values
     */
    public <A> List<A> allThrough(Fold<S, A> fold) {
      return fold.getAll(source);
    }

    /**
     * Gets all values through a {@link Traversal}.
     *
     * @param traversal The traversal to focus with
     * @param <A> The focused value type
     * @return A list of all focused values
     */
    public <A> List<A> allThrough(Traversal<S, A> traversal) {
      return traversalToFold(traversal).getAll(source);
    }
  }

  /**
   * Builder for fluent set/write operations.
   *
   * @param <S> The source type
   */
  public static final class SetBuilder<S> {
    private final S source;

    SetBuilder(S source) {
      this.source = source;
    }

    /**
     * Sets a value through a {@link Lens}.
     *
     * @param lens The lens to focus with
     * @param value The new value to set
     * @param <A> The focused value type
     * @return A new structure with the value updated
     */
    public <A> S through(Lens<S, A> lens, A value) {
      return lens.set(value, source);
    }

    /**
     * Sets all values through a {@link Traversal}.
     *
     * @param traversal The traversal to focus with
     * @param value The new value to set for all focused elements
     * @param <A> The focused value type
     * @return A new structure with all focused values updated
     */
    public <A> S allThrough(Traversal<S, A> traversal, A value) {
      return Traversals.modify(traversal, ignored -> value, source);
    }
  }

  /**
   * Builder for fluent modify/update operations.
   *
   * @param <S> The source type
   */
  public static final class ModifyBuilder<S> {
    private final S source;

    ModifyBuilder(S source) {
      this.source = source;
    }

    /**
     * Modifies a value through a {@link Lens}.
     *
     * @param lens The lens to focus with
     * @param modifier The function to transform the focused value
     * @param <A> The focused value type
     * @return A new structure with the value modified
     */
    public <A> S through(Lens<S, A> lens, Function<A, A> modifier) {
      return lens.modify(modifier, source);
    }

    /**
     * Modifies all values through a {@link Traversal}.
     *
     * @param traversal The traversal to focus with
     * @param modifier The function to transform each focused value
     * @param <A> The focused value type
     * @return A new structure with all focused values modified
     */
    public <A> S allThrough(Traversal<S, A> traversal, Function<A, A> modifier) {
      return Traversals.modify(traversal, modifier, source);
    }

    /**
     * Modifies a value through a {@link Lens} with an effectful function.
     *
     * @param lens The lens to focus with
     * @param modifier The effectful function to transform the focused value
     * @param functor The functor instance for the effect type
     * @param <F> The effect type witness
     * @param <A> The focused value type
     * @return The modified structure wrapped in the effect
     */
    public <F, A> Kind<F, S> throughF(
        Lens<S, A> lens, Function<A, Kind<F, A>> modifier, Functor<F> functor) {
      return lens.modifyF(modifier, source, functor);
    }

    /**
     * Modifies all values through a {@link Traversal} with an effectful function.
     *
     * @param traversal The traversal to focus with
     * @param modifier The effectful function to transform each focused value
     * @param applicative The applicative instance for the effect type
     * @param <F> The effect type witness
     * @param <A> The focused value type
     * @return The modified structure wrapped in the effect
     */
    public <F, A> Kind<F, S> allThroughF(
        Traversal<S, A> traversal, Function<A, Kind<F, A>> modifier, Applicative<F> applicative) {
      return traversal.modifyF(modifier, source, applicative);
    }
  }

  /**
   * Builder for fluent query operations.
   *
   * @param <S> The source type
   */
  public static final class QueryBuilder<S> {
    private final S source;

    QueryBuilder(S source) {
      this.source = source;
    }

    /**
     * Checks if any focused element matches the given predicate.
     *
     * @param fold The optic to focus with
     * @param predicate The predicate to test
     * @param <A> The focused value type
     * @return {@code true} if any focused element matches
     */
    public <A> boolean anyMatch(Fold<S, A> fold, Predicate<A> predicate) {
      return fold.exists(predicate, source);
    }

    /**
     * Checks if any focused element matches the given predicate.
     *
     * @param traversal The traversal to focus with
     * @param predicate The predicate to test
     * @param <A> The focused value type
     * @return {@code true} if any focused element matches
     */
    public <A> boolean anyMatch(Traversal<S, A> traversal, Predicate<A> predicate) {
      return traversalToFold(traversal).exists(predicate, source);
    }

    /**
     * Checks if all focused elements match the given predicate.
     *
     * @param fold The optic to focus with
     * @param predicate The predicate to test
     * @param <A> The focused value type
     * @return {@code true} if all focused elements match
     */
    public <A> boolean allMatch(Fold<S, A> fold, Predicate<A> predicate) {
      return fold.all(predicate, source);
    }

    /**
     * Checks if all focused elements match the given predicate.
     *
     * @param traversal The traversal to focus with
     * @param predicate The predicate to test
     * @param <A> The focused value type
     * @return {@code true} if all focused elements match
     */
    public <A> boolean allMatch(Traversal<S, A> traversal, Predicate<A> predicate) {
      return traversalToFold(traversal).all(predicate, source);
    }

    /**
     * Finds the first focused element that matches the given predicate.
     *
     * @param fold The optic to focus with
     * @param predicate The predicate to test
     * @param <A> The focused value type
     * @return An {@link Optional} containing the first matching element, or empty if none match
     */
    public <A> Optional<A> findFirst(Fold<S, A> fold, Predicate<A> predicate) {
      return fold.find(predicate, source);
    }

    /**
     * Finds the first focused element that matches the given predicate.
     *
     * @param traversal The traversal to focus with
     * @param predicate The predicate to test
     * @param <A> The focused value type
     * @return An {@link Optional} containing the first matching element, or empty if none match
     */
    public <A> Optional<A> findFirst(Traversal<S, A> traversal, Predicate<A> predicate) {
      return traversalToFold(traversal).find(predicate, source);
    }

    /**
     * Counts the number of focused elements.
     *
     * @param fold The optic to focus with
     * @param <A> The focused value type
     * @return The number of focused elements
     */
    public <A> int count(Fold<S, A> fold) {
      return fold.length(source);
    }

    /**
     * Counts the number of focused elements.
     *
     * @param traversal The traversal to focus with
     * @param <A> The focused value type
     * @return The number of focused elements
     */
    public <A> int count(Traversal<S, A> traversal) {
      return traversalToFold(traversal).length(source);
    }

    /**
     * Checks if there are no focused elements.
     *
     * @param fold The optic to focus with
     * @param <A> The focused value type
     * @return {@code true} if there are no focused elements
     */
    public <A> boolean isEmpty(Fold<S, A> fold) {
      return fold.isEmpty(source);
    }

    /**
     * Checks if there are no focused elements.
     *
     * @param traversal The traversal to focus with
     * @param <A> The focused value type
     * @return {@code true} if there are no focused elements
     */
    public <A> boolean isEmpty(Traversal<S, A> traversal) {
      return traversalToFold(traversal).isEmpty(source);
    }
  }

  /**
   * Builder for fluent validation-aware modify operations.
   *
   * <p>This builder provides methods for modifying values with validation that can fail, returning
   * {@link org.higherkindedj.hkt.either.Either}, {@link org.higherkindedj.hkt.maybe.Maybe}, or
   * {@link org.higherkindedj.hkt.validated.Validated} to represent success or failure.
   *
   * <h2>When to Use Each Method</h2>
   *
   * <ul>
   *   <li><b>throughEither</b>: Single field validation with error short-circuiting
   *   <li><b>throughMaybe</b>: Single field optional validation (success/nothing)
   *   <li><b>allThroughValidated</b>: Multiple field validation with error accumulation
   *   <li><b>allThroughEither</b>: Multiple field validation with error short-circuiting
   * </ul>
   *
   * @param <S> The source type
   */
  public static final class ModifyingWithValidation<S> {
    private final S source;

    ModifyingWithValidation(S source) {
      this.source = source;
    }

    /**
     * Modifies through a {@link Lens} with Either-based validation.
     *
     * <p>Example:
     *
     * <pre>{@code
     * Either<String, User> result = OpticOps.modifyingWithValidation(user)
     *     .throughEither(emailLens, email ->
     *         email.contains("@")
     *             ? Either.right(email.toLowerCase())
     *             : Either.left("Invalid email format")
     *     );
     * }</pre>
     *
     * @param lens The lens to focus with
     * @param validator Function returning Either.right(valid) or Either.left(error)
     * @param <E> The error type
     * @param <A> The focused value type
     * @return Either containing the updated structure or an error
     */
    public <E, A> org.higherkindedj.hkt.either.Either<E, S> throughEither(
        Lens<S, A> lens, Function<A, org.higherkindedj.hkt.either.Either<E, A>> validator) {
      return modifyEither(source, lens, validator);
    }

    /**
     * Modifies through a {@link Lens} with Maybe-based validation.
     *
     * <p>Example:
     *
     * <pre>{@code
     * Maybe<Person> result = OpticOps.modifyingWithValidation(person)
     *     .throughMaybe(ageLens, age ->
     *         (age >= 0 && age <= 150)
     *             ? Maybe.just(age)
     *             : Maybe.nothing()
     *     );
     * }</pre>
     *
     * @param lens The lens to focus with
     * @param validator Function returning Maybe.just(valid) or Maybe.nothing()
     * @param <A> The focused value type
     * @return Maybe containing the updated structure or nothing
     */
    public <A> org.higherkindedj.hkt.maybe.Maybe<S> throughMaybe(
        Lens<S, A> lens, Function<A, org.higherkindedj.hkt.maybe.Maybe<A>> validator) {
      return modifyMaybe(source, lens, validator);
    }

    /**
     * Modifies all focused values with Validated accumulation.
     *
     * <p>All validation errors are accumulated rather than short-circuiting on the first error.
     *
     * <p>Example:
     *
     * <pre>{@code
     * Validated<List<String>, Order> result = OpticOps.modifyingWithValidation(order)
     *     .allThroughValidated(itemPrices, price -> {
     *         if (price < 0) {
     *             return Validated.invalid("Negative price");
     *         } else if (price > 10000) {
     *             return Validated.invalid("Price too high");
     *         } else {
     *             return Validated.valid(price);
     *         }
     *     });
     * }</pre>
     *
     * @param traversal The traversal to focus with
     * @param validator Function returning Validated.valid or Validated.invalid
     * @param <E> The error type
     * @param <A> The focused value type
     * @return Validated containing the updated structure or accumulated errors
     */
    public <E, A> org.higherkindedj.hkt.validated.Validated<List<E>, S> allThroughValidated(
        Traversal<S, A> traversal,
        Function<A, org.higherkindedj.hkt.validated.Validated<E, A>> validator) {
      return modifyAllValidated(source, traversal, validator);
    }

    /**
     * Modifies all focused values with Either short-circuiting.
     *
     * <p>Stops at the first validation error (short-circuits).
     *
     * <p>Example:
     *
     * <pre>{@code
     * Either<String, Team> result = OpticOps.modifyingWithValidation(team)
     *     .allThroughEither(playerNames, name ->
     *         name.length() >= 3
     *             ? Either.right(name.trim())
     *             : Either.left("Name too short: " + name)
     *     );
     * }</pre>
     *
     * @param traversal The traversal to focus with
     * @param validator Function returning Either.right or Either.left
     * @param <E> The error type
     * @param <A> The focused value type
     * @return Either containing the updated structure or first error
     */
    public <E, A> org.higherkindedj.hkt.either.Either<E, S> allThroughEither(
        Traversal<S, A> traversal,
        Function<A, org.higherkindedj.hkt.either.Either<E, A>> validator) {
      return modifyAllEither(source, traversal, validator);
    }
  }
}
