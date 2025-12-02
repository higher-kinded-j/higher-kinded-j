// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.indexed.Pair;

/**
 * Provides a fluent builder for indexed traversal-based comprehensions, enabling position-aware
 * bulk operations over structures using {@link IndexedTraversal} optics within an {@link
 * Applicative} context.
 *
 * <p>This class extends the For comprehension pattern to include index/position awareness, allowing
 * transformations that depend on element position.
 *
 * <h3>Key Features</h3>
 *
 * <ul>
 *   <li><b>Position-aware transformations:</b> Access both index and value in operations
 *   <li><b>Index-based filtering:</b> Filter elements by their position
 *   <li><b>Combined filtering:</b> Filter based on both index and value
 *   <li><b>Lens integration:</b> Modify specific fields with index awareness
 * </ul>
 *
 * <h3>Example Usage</h3>
 *
 * <pre>{@code
 * record Player(String name, int score) {}
 *
 * IndexedTraversal<Integer, List<Player>, Player> iplayersTraversal = IndexedTraversals.forList();
 * Lens<Player, Integer> scoreLens = Lens.of(Player::score, (p, s) -> new Player(p.name(), s));
 *
 * List<Player> players = List.of(new Player("Alice", 100), new Player("Bob", 200));
 *
 * // Add bonus based on position (first place gets more)
 * List<Player> updated = ForIndexed.overIndexed(iplayersTraversal, players, idApplicative)
 *     .modify(scoreLens, (index, score) -> score + (100 - index * 10))
 *     .run();
 *
 * // Filter by position and modify
 * List<Player> topThree = ForIndexed.overIndexed(iplayersTraversal, players, idApplicative)
 *     .filterIndex(i -> i < 3)
 *     .modify(scoreLens, (i, s) -> s * 2)
 *     .run();
 * }</pre>
 *
 * @see IndexedTraversal
 * @see Applicative
 */
public final class ForIndexed {

  private ForIndexed() {} // Static access only

  /**
   * Starts an indexed traversal-based comprehension over elements of a structure.
   *
   * @param traversal The {@link IndexedTraversal} that focuses on elements with their indices.
   * @param source The source structure containing the elements.
   * @param applicative The {@link Applicative} instance for the effect context.
   * @param <F> The witness type for the applicative context.
   * @param <I> The index type.
   * @param <S> The type of the source structure.
   * @param <A> The type of the focused elements.
   * @return An {@link IndexedSteps} builder for chaining operations.
   * @throws NullPointerException if any argument is null.
   */
  public static <F, I, S, A> IndexedSteps<F, I, S, A> overIndexed(
      IndexedTraversal<I, S, A> traversal, S source, Applicative<F> applicative) {
    Objects.requireNonNull(traversal, "traversal must not be null");
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(applicative, "applicative must not be null");
    return new IndexedStepsImpl<>(traversal, source, applicative, (i, a) -> applicative.of(a));
  }

  /**
   * A builder interface for chaining operations on indexed traversal-focused elements.
   *
   * @param <F> The witness type for the applicative context.
   * @param <I> The index type.
   * @param <S> The type of the source structure.
   * @param <A> The type of the focused elements.
   */
  public interface IndexedSteps<F, I, S, A> {

    /**
     * Filters elements based on their index only.
     *
     * <p>Elements whose indices don't match the predicate are preserved unchanged.
     *
     * @param predicate The predicate to test indices against.
     * @return A new builder with the index filter applied.
     * @throws NullPointerException if {@code predicate} is null.
     */
    IndexedSteps<F, I, S, A> filterIndex(Predicate<I> predicate);

    /**
     * Filters elements based on both their index and value.
     *
     * <p>Elements that don't match the predicate are preserved unchanged.
     *
     * @param predicate The predicate to test index-value pairs against.
     * @return A new builder with the filter applied.
     * @throws NullPointerException if {@code predicate} is null.
     */
    IndexedSteps<F, I, S, A> filter(BiPredicate<I, A> predicate);

    /**
     * Modifies a specific field within each focused element using a lens, with index awareness.
     *
     * @param lens The lens focusing on the field to modify.
     * @param modifier A function taking index and current field value, returning new value.
     * @param <B> The type of the field being modified.
     * @return A new builder with the modification applied.
     * @throws NullPointerException if any argument is null.
     */
    <B> IndexedSteps<F, I, S, A> modify(Lens<A, B> lens, BiFunction<I, B, B> modifier);

    /**
     * Sets a specific field within each focused element using a lens, with index-based values.
     *
     * @param lens The lens focusing on the field to set.
     * @param valueFunction A function taking index and returning the value to set.
     * @param <B> The type of the field being set.
     * @return A new builder with the field set.
     * @throws NullPointerException if any argument is null.
     */
    <B> IndexedSteps<F, I, S, A> set(Lens<A, B> lens, Function<I, B> valueFunction);

    /**
     * Completes the traversal and returns the modified structure wrapped in the applicative
     * context.
     *
     * @return The modified structure in the applicative context.
     */
    Kind<F, S> run();

    /**
     * Collects all focused elements along with their indices into a list.
     *
     * @return A list of index-value pairs in the applicative context.
     */
    Kind<F, List<Pair<I, A>>> toIndexedList();
  }

  /** Implementation of the indexed traversal steps builder. */
  private static final class IndexedStepsImpl<F, I, S, A> implements IndexedSteps<F, I, S, A> {
    private final IndexedTraversal<I, S, A> traversal;
    private final S source;
    private final Applicative<F> applicative;
    private final BiFunction<I, A, Kind<F, A>> transformation;
    private final BiPredicate<I, A> filterPredicate;

    IndexedStepsImpl(
        IndexedTraversal<I, S, A> traversal,
        S source,
        Applicative<F> applicative,
        BiFunction<I, A, Kind<F, A>> transformation) {
      this(traversal, source, applicative, transformation, (i, a) -> true);
    }

    IndexedStepsImpl(
        IndexedTraversal<I, S, A> traversal,
        S source,
        Applicative<F> applicative,
        BiFunction<I, A, Kind<F, A>> transformation,
        BiPredicate<I, A> filterPredicate) {
      this.traversal = traversal;
      this.source = source;
      this.applicative = applicative;
      this.transformation = transformation;
      this.filterPredicate = filterPredicate;
    }

    @Override
    public IndexedSteps<F, I, S, A> filterIndex(Predicate<I> predicate) {
      Objects.requireNonNull(predicate, "predicate must not be null");
      // Combine predicates: adapt index-only predicate to BiPredicate
      BiPredicate<I, A> indexPredicate = (i, a) -> predicate.test(i);
      return new IndexedStepsImpl<>(
          traversal, source, applicative, transformation, filterPredicate.and(indexPredicate));
    }

    @Override
    public IndexedSteps<F, I, S, A> filter(BiPredicate<I, A> predicate) {
      Objects.requireNonNull(predicate, "predicate must not be null");
      return new IndexedStepsImpl<>(
          traversal, source, applicative, transformation, filterPredicate.and(predicate));
    }

    @Override
    public <B> IndexedSteps<F, I, S, A> modify(Lens<A, B> lens, BiFunction<I, B, B> modifier) {
      Objects.requireNonNull(lens, "lens must not be null");
      Objects.requireNonNull(modifier, "modifier must not be null");

      // Only modify elements that pass the filter predicate
      BiFunction<I, A, Kind<F, A>> modifiedTransformation =
          (i, a) ->
              filterPredicate.test(i, a)
                  ? applicative.map(
                      newA -> lens.modify(b -> modifier.apply(i, b), newA),
                      transformation.apply(i, a))
                  : transformation.apply(i, a);

      return new IndexedStepsImpl<>(
          traversal, source, applicative, modifiedTransformation, filterPredicate);
    }

    @Override
    public <B> IndexedSteps<F, I, S, A> set(Lens<A, B> lens, Function<I, B> valueFunction) {
      Objects.requireNonNull(lens, "lens must not be null");
      Objects.requireNonNull(valueFunction, "valueFunction must not be null");

      // Only set on elements that pass the filter predicate
      BiFunction<I, A, Kind<F, A>> setTransformation =
          (i, a) ->
              filterPredicate.test(i, a)
                  ? applicative.map(
                      newA -> lens.set(valueFunction.apply(i), newA), transformation.apply(i, a))
                  : transformation.apply(i, a);

      return new IndexedStepsImpl<>(
          traversal, source, applicative, setTransformation, filterPredicate);
    }

    @Override
    public Kind<F, S> run() {
      return traversal.imodifyF(transformation, source, applicative);
    }

    @Override
    public Kind<F, List<Pair<I, A>>> toIndexedList() {
      // Collect all elements with their indices
      List<Pair<I, A>> collected = new ArrayList<>();
      traversal.imodifyF(
          (i, a) -> {
            collected.add(new Pair<>(i, a));
            return applicative.of(a);
          },
          source,
          applicative);
      return applicative.of(collected);
    }
  }
}
