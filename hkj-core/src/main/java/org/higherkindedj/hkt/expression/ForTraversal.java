// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;

/**
 * Provides a fluent builder for traversal-based comprehensions, enabling bulk operations over
 * structures using {@link Traversal} optics within an {@link Applicative} context.
 *
 * <p>This class bridges the gap between for-comprehensions and traversals, allowing declarative
 * bulk updates, transformations, and queries over multiple elements focused by a traversal.
 *
 * <h3>Key Features</h3>
 *
 * <ul>
 *   <li><b>Bulk transformations:</b> Apply functions to all focused elements
 *   <li><b>Effectful operations:</b> Transform elements within an applicative context
 *   <li><b>Filtering:</b> Skip elements that don't match a predicate
 *   <li><b>Lens integration:</b> Modify specific fields within each focused element
 * </ul>
 *
 * <h3>Example Usage</h3>
 *
 * <pre>{@code
 * record Player(String name, int score) {}
 *
 * Traversal<List<Player>, Player> playersTraversal = Traversals.forList();
 * Lens<Player, Integer> scoreLens = Lens.of(Player::score, (p, s) -> new Player(p.name(), s));
 *
 * List<Player> players = List.of(new Player("Alice", 100), new Player("Bob", 200));
 *
 * // Double all scores
 * List<Player> updated = ForTraversal.over(playersTraversal, players, idApplicative)
 *     .modify(scoreLens, score -> score * 2)
 *     .run();
 *
 * // Filter and modify only high scorers
 * List<Player> updated2 = ForTraversal.over(playersTraversal, players, idApplicative)
 *     .filter(p -> p.score() >= 150)
 *     .modify(scoreLens, score -> score + 50)
 *     .run();
 * }</pre>
 *
 * @see Traversal
 * @see Applicative
 */
public final class ForTraversal {

  private ForTraversal() {} // Static access only

  /**
   * Starts a traversal-based comprehension over elements of a structure.
   *
   * @param traversal The {@link Traversal} that focuses on the elements to operate on.
   * @param source The source structure containing the elements.
   * @param applicative The {@link Applicative} instance for the effect context.
   * @param <F> The witness type for the applicative context.
   * @param <S> The type of the source structure.
   * @param <A> The type of the focused elements.
   * @return A {@link TraversalSteps} builder for chaining operations.
   * @throws NullPointerException if any argument is null.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, S, A> TraversalSteps<F, S, A> over(
      Traversal<S, A> traversal, S source, Applicative<F> applicative) {
    Objects.requireNonNull(traversal, "traversal must not be null");
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(applicative, "applicative must not be null");
    return new TraversalStepsImpl<>(traversal, source, applicative, applicative::of);
  }

  /**
   * A builder interface for chaining operations on traversal-focused elements.
   *
   * @param <F> The witness type for the applicative context.
   * @param <S> The type of the source structure.
   * @param <A> The type of the focused elements.
   */
  public interface TraversalSteps<F extends WitnessArity<TypeArity.Unary>, S, A> {

    /**
     * Filters elements, only applying subsequent operations to those that match the predicate.
     *
     * <p>Elements that don't match are preserved unchanged in the structure during modifications.
     *
     * @param predicate The predicate to test elements against.
     * @return A new builder with the filter applied.
     * @throws NullPointerException if {@code predicate} is null.
     */
    TraversalSteps<F, S, A> filter(Predicate<A> predicate);

    /**
     * Modifies a specific field within each focused element using a lens.
     *
     * @param lens The lens focusing on the field to modify.
     * @param modifier The function to apply to the field.
     * @param <B> The type of the field being modified.
     * @return A new builder with the modification applied.
     * @throws NullPointerException if any argument is null.
     */
    <B> TraversalSteps<F, S, A> modify(Lens<A, B> lens, Function<B, B> modifier);

    /**
     * Sets a specific field within each focused element using a lens.
     *
     * @param lens The lens focusing on the field to set.
     * @param value The new value for the field.
     * @param <B> The type of the field being set.
     * @return A new builder with the field set.
     * @throws NullPointerException if {@code lens} is null.
     */
    <B> TraversalSteps<F, S, A> set(Lens<A, B> lens, B value);

    /**
     * Completes the traversal and returns the modified structure wrapped in the applicative
     * context.
     *
     * @return The modified structure in the applicative context.
     */
    Kind<F, S> run();

    /**
     * Collects all focused elements into a list.
     *
     * <p>Note: This operation extracts the current state of elements and collects them. It does not
     * apply any pending transformations that would modify the structure.
     *
     * @return A list of all focused elements in the applicative context.
     */
    Kind<F, List<A>> toList();
  }

  /** Implementation of the traversal steps builder. */
  private static final class TraversalStepsImpl<F extends WitnessArity<TypeArity.Unary>, S, A>
      implements TraversalSteps<F, S, A> {
    private final Traversal<S, A> traversal;
    private final S source;
    private final Applicative<F> applicative;
    private final Function<A, Kind<F, A>> transformation;
    private final Predicate<A> filterPredicate;

    TraversalStepsImpl(
        Traversal<S, A> traversal,
        S source,
        Applicative<F> applicative,
        Function<A, Kind<F, A>> transformation) {
      this(traversal, source, applicative, transformation, a -> true);
    }

    TraversalStepsImpl(
        Traversal<S, A> traversal,
        S source,
        Applicative<F> applicative,
        Function<A, Kind<F, A>> transformation,
        Predicate<A> filterPredicate) {
      this.traversal = traversal;
      this.source = source;
      this.applicative = applicative;
      this.transformation = transformation;
      this.filterPredicate = filterPredicate;
    }

    @Override
    public TraversalSteps<F, S, A> filter(Predicate<A> predicate) {
      Objects.requireNonNull(predicate, "predicate must not be null");
      // Combine predicates: both must pass
      return new TraversalStepsImpl<>(
          traversal, source, applicative, transformation, filterPredicate.and(predicate));
    }

    @Override
    public <B> TraversalSteps<F, S, A> modify(Lens<A, B> lens, Function<B, B> modifier) {
      Objects.requireNonNull(lens, "lens must not be null");
      Objects.requireNonNull(modifier, "modifier must not be null");

      // Only modify elements that pass the filter predicate
      Function<A, Kind<F, A>> modifiedTransformation =
          a ->
              filterPredicate.test(a)
                  ? applicative.map(newA -> lens.modify(modifier, newA), transformation.apply(a))
                  : transformation.apply(a);

      return new TraversalStepsImpl<>(
          traversal, source, applicative, modifiedTransformation, filterPredicate);
    }

    @Override
    public <B> TraversalSteps<F, S, A> set(Lens<A, B> lens, B value) {
      Objects.requireNonNull(lens, "lens must not be null");

      // Only set on elements that pass the filter predicate
      Function<A, Kind<F, A>> setTransformation =
          a ->
              filterPredicate.test(a)
                  ? applicative.map(newA -> lens.set(value, newA), transformation.apply(a))
                  : transformation.apply(a);

      return new TraversalStepsImpl<>(
          traversal, source, applicative, setTransformation, filterPredicate);
    }

    @Override
    public Kind<F, S> run() {
      return traversal.modifyF(transformation, source, applicative);
    }

    @Override
    public Kind<F, List<A>> toList() {
      // Collect all elements using a list-building traversal
      List<A> collected = new ArrayList<>();
      traversal.modifyF(
          a -> {
            collected.add(a);
            return applicative.of(a);
          },
          source,
          applicative);
      return applicative.of(collected);
    }
  }
}
