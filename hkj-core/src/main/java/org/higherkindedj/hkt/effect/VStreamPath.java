// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.util.Traversals;

/**
 * A fluent path wrapper for {@link VStream} values.
 *
 * <p>{@code VStreamPath} provides a chainable API for composing lazy, pull-based streaming
 * computations that execute on virtual threads. It implements {@link Chainable} to support monadic
 * composition within the Effect Path ecosystem.
 *
 * <h2>Relationship to VStream</h2>
 *
 * <p>VStreamPath wraps a {@link VStream} and provides:
 *
 * <ul>
 *   <li>Fluent API consistent with other Effect Path types
 *   <li>Stream-specific operations (filter, take, drop, distinct, concat)
 *   <li>Terminal operations that bridge to {@link VTaskPath} for single-value results
 *   <li>Optics focus bridge for navigating into stream elements
 *   <li>Conversions to other path types (StreamPath, ListPath, NonDetPath)
 * </ul>
 *
 * <h2>Creating VStreamPath instances</h2>
 *
 * <p>Use the {@link Path} factory class:
 *
 * <pre>{@code
 * VStreamPath<Integer> numbers = Path.vstream(VStream.fromList(List.of(1, 2, 3)));
 * VStreamPath<String> names = Path.vstreamOf("Alice", "Bob", "Charlie");
 * VStreamPath<Integer> range = Path.vstreamRange(1, 100);
 * VStreamPath<Integer> infinite = Path.vstreamIterate(1, n -> n + 1);
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <p>Operations are lazy; nothing executes until a terminal operation is called:
 *
 * <pre>{@code
 * VStreamPath<String> pipeline = Path.vstreamRange(1, 100)
 *     .filter(n -> n % 2 == 0)
 *     .map(n -> "Even: " + n)
 *     .take(5);
 *
 * // Terminal operation triggers execution, returning VTaskPath
 * List<String> result = pipeline.toList().unsafeRun();
 * }</pre>
 *
 * <h2>Terminal operations</h2>
 *
 * <p>Terminal operations return {@link VTaskPath}, bridging from stream to single-value effect:
 *
 * <pre>{@code
 * VTaskPath<List<String>> collected = pipeline.toList();
 * VTaskPath<Long> counted = pipeline.count();
 * VTaskPath<Boolean> hasEven = pipeline.exists(s -> s.contains("Even"));
 * }</pre>
 *
 * <h2>Comparison with StreamPath</h2>
 *
 * <ul>
 *   <li>{@code VStreamPath} - Lazy, pull-based, virtual thread execution, effectful elements
 *   <li>{@code StreamPath} - Materialises internally, no virtual thread integration
 * </ul>
 *
 * @param <A> the type of elements in the stream
 * @see VStream
 * @see VTaskPath
 * @see StreamPath
 */
public sealed interface VStreamPath<A> extends Chainable<A> permits DefaultVStreamPath {

  // ===== Core access =====

  /**
   * Returns the underlying {@link VStream}.
   *
   * @return the wrapped VStream; never null
   */
  VStream<A> run();

  // ===== Composable operations (Functor) =====

  @Override
  <B> VStreamPath<B> map(Function<? super A, ? extends B> mapper);

  /**
   * Performs a side effect on each element without modifying it.
   *
   * @param consumer the action to perform on each element; must not be null
   * @return a new VStreamPath that performs the action
   * @throws NullPointerException if consumer is null
   */
  VStreamPath<A> peek(Consumer<? super A> consumer);

  /**
   * Maps all elements to {@link Unit}, discarding values but preserving stream structure.
   *
   * @return a VStreamPath that produces Unit for each element
   */
  VStreamPath<Unit> asUnit();

  // ===== Chainable operations (Monad) =====

  @Override
  <B> VStreamPath<B> via(Function<? super A, ? extends Chainable<B>> mapper);

  @Override
  default <B> VStreamPath<B> flatMap(Function<? super A, ? extends Chainable<B>> mapper) {
    return via(mapper);
  }

  @Override
  <B> VStreamPath<B> then(Supplier<? extends Chainable<B>> supplier);

  // ===== Combinable operations (Applicative) =====

  @Override
  <B, C> VStreamPath<C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner);

  /**
   * Combines this path with two others using a ternary function. Elements are paired positionally;
   * the result has length equal to the shortest input.
   *
   * @param second the second path; must not be null
   * @param third the third path; must not be null
   * @param combiner the function to combine the values; must not be null
   * @param <B> the type of the second path's value
   * @param <C> the type of the third path's value
   * @param <D> the type of the combined result
   * @return a new VStreamPath containing the combined results
   */
  <B, C, D> VStreamPath<D> zipWith3(
      VStreamPath<B> second,
      VStreamPath<C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner);

  // ===== Stream-specific operations =====

  /**
   * Filters elements using the given predicate. Non-matching elements are skipped lazily.
   *
   * @param predicate the predicate to test elements against; must not be null
   * @return a new VStreamPath with only matching elements
   * @throws NullPointerException if predicate is null
   */
  VStreamPath<A> filter(Predicate<? super A> predicate);

  /**
   * Takes at most the first {@code n} elements.
   *
   * @param n the maximum number of elements to take
   * @return a new VStreamPath limited to n elements
   */
  VStreamPath<A> take(long n);

  /**
   * Drops the first {@code n} elements.
   *
   * @param n the number of elements to drop
   * @return a new VStreamPath without the first n elements
   */
  VStreamPath<A> drop(long n);

  /**
   * Takes elements while the predicate holds, then completes.
   *
   * @param predicate the predicate to test elements against; must not be null
   * @return a new VStreamPath that completes when the predicate fails
   * @throws NullPointerException if predicate is null
   */
  VStreamPath<A> takeWhile(Predicate<? super A> predicate);

  /**
   * Drops elements while the predicate holds, then emits all remaining.
   *
   * @param predicate the predicate to test elements against; must not be null
   * @return a new VStreamPath that skips initial matching elements
   * @throws NullPointerException if predicate is null
   */
  VStreamPath<A> dropWhile(Predicate<? super A> predicate);

  /**
   * Removes duplicate elements. Elements are compared using {@link Object#equals(Object)}.
   *
   * <p><b>Warning:</b> For infinite streams, the internal set grows without bound.
   *
   * @return a new VStreamPath with duplicates removed
   */
  VStreamPath<A> distinct();

  /**
   * Appends another VStreamPath after this one.
   *
   * @param other the VStreamPath to append; must not be null
   * @return a new VStreamPath containing elements from both
   * @throws NullPointerException if other is null
   */
  VStreamPath<A> concat(VStreamPath<A> other);

  // ===== Materialisation to VTaskPath (terminal operations) =====

  /**
   * Collects all elements into a list. This is a terminal operation.
   *
   * <p><b>Warning:</b> For infinite streams, this will not terminate. Use {@link #take(long)}
   * first.
   *
   * @return a VTaskPath that produces the list of all elements
   */
  VTaskPath<List<A>> toList();

  /**
   * Left-folds all elements with the given identity and operator.
   *
   * @param identity the initial accumulator value
   * @param op the binary operator; must not be null
   * @return a VTaskPath that produces the fold result
   * @throws NullPointerException if op is null
   */
  VTaskPath<A> fold(A identity, BinaryOperator<A> op);

  /**
   * Left-folds all elements with the given initial value and accumulator function.
   *
   * @param identity the initial accumulator value
   * @param f the accumulator function; must not be null
   * @param <B> the type of the accumulator
   * @return a VTaskPath that produces the fold result
   * @throws NullPointerException if f is null
   */
  <B> VTaskPath<B> foldLeft(B identity, BiFunction<B, A, B> f);

  /**
   * Folds all elements using a monoid, mapping each element first.
   *
   * @param monoid the Monoid for combining values; must not be null
   * @param f the mapping function; must not be null
   * @param <M> the monoid type
   * @return a VTaskPath that produces the fold result
   * @throws NullPointerException if monoid or f is null
   */
  <M> VTaskPath<M> foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f);

  /**
   * Returns the first element, or empty if the stream is empty.
   *
   * @return a VTaskPath producing the first element as an Optional
   */
  VTaskPath<Optional<A>> headOption();

  /**
   * Returns the last element, or empty if the stream is empty.
   *
   * <p><b>Warning:</b> For infinite streams, this will not terminate.
   *
   * @return a VTaskPath producing the last element as an Optional
   */
  VTaskPath<Optional<A>> lastOption();

  /**
   * Counts the number of elements.
   *
   * <p><b>Warning:</b> For infinite streams, this will not terminate.
   *
   * @return a VTaskPath producing the element count
   */
  VTaskPath<Long> count();

  /**
   * Checks whether any element matches the given predicate. Short-circuits on the first match.
   *
   * @param predicate the predicate to test; must not be null
   * @return a VTaskPath producing true if any element matches
   * @throws NullPointerException if predicate is null
   */
  VTaskPath<Boolean> exists(Predicate<? super A> predicate);

  /**
   * Checks whether all elements match the given predicate. Short-circuits on the first non-match.
   *
   * @param predicate the predicate to test; must not be null
   * @return a VTaskPath producing true if all elements match
   * @throws NullPointerException if predicate is null
   */
  VTaskPath<Boolean> forAll(Predicate<? super A> predicate);

  /**
   * Finds the first element matching the given predicate.
   *
   * @param predicate the predicate to test; must not be null
   * @return a VTaskPath producing the first match as an Optional
   * @throws NullPointerException if predicate is null
   */
  VTaskPath<Optional<A>> find(Predicate<? super A> predicate);

  /**
   * Executes a side effect for each element.
   *
   * @param consumer the action to perform on each element; must not be null
   * @return a VTaskPath that completes when all elements are processed
   * @throws NullPointerException if consumer is null
   */
  VTaskPath<Unit> forEach(Consumer<? super A> consumer);

  // ===== Parallel operations =====

  /**
   * Applies an effectful function to each element with bounded concurrency, preserving input order.
   *
   * <p>Delegates to {@link org.higherkindedj.hkt.vstream.VStreamPar#parEvalMap(VStream, int,
   * java.util.function.Function)}.
   *
   * @param concurrency the maximum number of elements to process concurrently; must be positive
   * @param f the effectful function to apply to each element; must not be null
   * @param <B> the type of the transformed elements
   * @return a new VStreamPath with parallel-processed elements in input order
   * @throws IllegalArgumentException if concurrency is not positive
   * @throws NullPointerException if f is null
   */
  <B> VStreamPath<B> parEvalMap(int concurrency, Function<? super A, VTask<B>> f);

  /**
   * Applies an effectful function to each element with bounded concurrency, emitting results in
   * completion order rather than input order.
   *
   * <p>Delegates to {@link org.higherkindedj.hkt.vstream.VStreamPar#parEvalMapUnordered(VStream,
   * int, java.util.function.Function)}.
   *
   * @param concurrency the maximum number of elements to process concurrently; must be positive
   * @param f the effectful function to apply to each element; must not be null
   * @param <B> the type of the transformed elements
   * @return a new VStreamPath with parallel-processed elements in completion order
   * @throws IllegalArgumentException if concurrency is not positive
   * @throws NullPointerException if f is null
   */
  <B> VStreamPath<B> parEvalMapUnordered(int concurrency, Function<? super A, VTask<B>> f);

  // ===== Chunking operations =====

  /**
   * Groups elements into lists of at most {@code size} elements.
   *
   * <p>Delegates to {@link VStream#chunk(int)}.
   *
   * @param size the maximum number of elements per chunk; must be positive
   * @return a new VStreamPath of element lists
   * @throws IllegalArgumentException if size is not positive
   */
  VStreamPath<List<A>> chunk(int size);

  /**
   * Chunks the stream, applies a batch function to each chunk, and flattens the results.
   *
   * <p>Delegates to {@link VStream#mapChunked(int, java.util.function.Function)}.
   *
   * @param size the maximum number of elements per batch; must be positive
   * @param f the batch function to apply; must not be null
   * @param <B> the type of elements in the output
   * @return a new VStreamPath with transformed elements
   * @throws IllegalArgumentException if size is not positive
   * @throws NullPointerException if f is null
   */
  <B> VStreamPath<B> mapChunked(int size, Function<List<A>, List<B>> f);

  /**
   * Collects all elements in parallel batches. This is a terminal operation.
   *
   * <p>Delegates to {@link org.higherkindedj.hkt.vstream.VStreamPar#parCollect(VStream, int)}.
   *
   * <p><b>Warning:</b> For infinite streams, this will not terminate. Use {@link #take(long)}
   * first.
   *
   * @param batchSize the number of elements to process per batch; must be positive
   * @return a VTaskPath that produces the list of all elements
   * @throws IllegalArgumentException if batchSize is not positive
   */
  VTaskPath<List<A>> parCollect(int batchSize);

  // ===== Optics focus bridge =====

  /**
   * Applies a {@link FocusPath} to each element, extracting a focused value. Since FocusPath always
   * succeeds, every element produces a result.
   *
   * @param path the FocusPath to apply; must not be null
   * @param <B> the focused type
   * @return a new VStreamPath with focused values
   * @throws NullPointerException if path is null
   */
  <B> VStreamPath<B> focus(FocusPath<A, B> path);

  /**
   * Applies an {@link AffinePath} to each element, keeping only elements where the affine matches.
   * Elements where the affine does not match are excluded from the result stream.
   *
   * @param path the AffinePath to apply; must not be null
   * @param <B> the focused type
   * @return a new VStreamPath with focused values (non-matching elements filtered out)
   * @throws NullPointerException if path is null
   */
  <B> VStreamPath<B> focus(AffinePath<A, B> path);

  // ===== Conversions =====

  /**
   * Returns the first element as a VTaskPath. Fails with {@link java.util.NoSuchElementException}
   * if the stream is empty.
   *
   * @return a VTaskPath producing the first element
   */
  VTaskPath<A> first();

  /**
   * Returns the last element as a VTaskPath. Fails with {@link java.util.NoSuchElementException} if
   * the stream is empty.
   *
   * <p><b>Warning:</b> For infinite streams, this will not terminate.
   *
   * @return a VTaskPath producing the last element
   */
  VTaskPath<A> last();

  /**
   * Converts to a StreamPath by materialising all elements.
   *
   * <p><b>Note:</b> This executes the VStream, collecting all elements into memory.
   *
   * @return a StreamPath containing the materialised elements
   */
  StreamPath<A> toStreamPath();

  /**
   * Converts to a ListPath by materialising all elements.
   *
   * <p><b>Note:</b> This executes the VStream, collecting all elements into memory.
   *
   * @return a ListPath containing the materialised elements
   */
  ListPath<A> toListPath();

  /**
   * Converts to a NonDetPath by materialising all elements.
   *
   * <p><b>Note:</b> This executes the VStream, collecting all elements into memory.
   *
   * @return a NonDetPath containing the materialised elements
   */
  NonDetPath<A> toNonDetPath();

  // ===== Resource management =====

  /**
   * Ensures a finaliser runs when this stream completes or encounters an error.
   *
   * <p>The finaliser VTask is executed exactly once when:
   *
   * <ul>
   *   <li>The stream completes normally
   *   <li>An error occurs during pulling
   * </ul>
   *
   * <p>Delegates to {@link VStream#onFinalize(VTask)}.
   *
   * @param finalizer the VTask to execute on stream completion or error; must not be null
   * @return a new VStreamPath with the finaliser attached
   * @throws NullPointerException if finalizer is null
   */
  VStreamPath<A> onFinalize(VTask<Unit> finalizer);

  // ===== Reactive interop =====

  /**
   * Converts this VStreamPath to a {@link java.util.concurrent.Flow.Publisher}.
   *
   * <p>Each subscriber receives all elements. Backpressure is respected: elements are only pulled
   * when the subscriber requests them. Delegates to {@link
   * org.higherkindedj.hkt.vstream.VStreamReactive#toPublisher(VStream)}.
   *
   * @return a Flow.Publisher that publishes this stream's elements; never null
   */
  java.util.concurrent.Flow.Publisher<A> toPublisher();

  // ===== Static factory methods =====

  /**
   * Creates a VStreamPath from a source structure and an {@link Each} instance.
   *
   * <p>This factory method bridges from the optics domain to the effect domain by extracting all
   * elements targeted by the Each instance's traversal and wrapping them in a VStreamPath.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * Each<List<String>, String> listEach = EachInstances.listEach();
   * List<String> names = List.of("Alice", "Bob", "Charlie");
   *
   * VStreamPath<String> namePath = VStreamPath.fromEach(names, listEach);
   * List<String> upper = namePath.map(String::toUpperCase).toList().unsafeRun();
   * // ["ALICE", "BOB", "CHARLIE"]
   * }</pre>
   *
   * @param source the source structure to extract elements from; must not be null
   * @param each the Each instance providing the traversal; must not be null
   * @param <S> the type of the source structure
   * @param <A> the element type within the structure
   * @return a VStreamPath containing all traversed elements
   * @throws NullPointerException if source or each is null
   */
  static <S, A> VStreamPath<A> fromEach(S source, Each<S, A> each) {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(each, "each must not be null");
    Traversal<S, A> traversal = each.each();
    List<A> elements = Traversals.getAll(traversal, source);
    return Path.vstreamFromList(elements);
  }
}
