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
 * <h2>Static Methods (Simple & Direct)</h2>
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
}
