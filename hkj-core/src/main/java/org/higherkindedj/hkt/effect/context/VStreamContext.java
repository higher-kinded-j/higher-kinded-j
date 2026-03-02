// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamPar;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Effect context for lazy, pull-based streaming on virtual threads.
 *
 * <p>VStreamContext provides a user-friendly Layer 2 API for working with {@link VStreamPath}. It
 * wraps VStream computations with convenient factory methods and chainable operations, hiding the
 * complexity of the underlying HKT machinery.
 *
 * <h2>Key Features</h2>
 *
 * <ul>
 *   <li><b>Virtual Thread Execution:</b> Stream elements are produced on virtual threads
 *   <li><b>Lazy Evaluation:</b> No elements are produced until a terminal operation is called
 *   <li><b>Bounded Parallel Processing:</b> {@link #parEvalMap} for concurrent element
 *       transformation
 *   <li><b>No HKT Exposure:</b> All types in the public API are standard Java types
 * </ul>
 *
 * <h2>Factory Methods</h2>
 *
 * <ul>
 *   <li>{@link #of(VStream)} - Wrap an existing VStream
 *   <li>{@link #fromList(List)} - Create from a list
 *   <li>{@link #pure(Object)} - Single-element stream
 *   <li>{@link #empty()} - Empty stream
 *   <li>{@link #range(int, int)} - Integer range
 * </ul>
 *
 * <h2>Terminal Operations</h2>
 *
 * <p>Terminal operations execute synchronously, blocking until the stream is fully consumed:
 *
 * <ul>
 *   <li>{@link #toList()} - Collect all elements
 *   <li>{@link #headOption()} - First element
 *   <li>{@link #count()} - Element count
 *   <li>{@link #fold(Object, BinaryOperator)} - Binary fold
 *   <li>{@link #exists(Predicate)} - Any match (short-circuits)
 *   <li>{@link #forAll(Predicate)} - All match (short-circuits)
 *   <li>{@link #find(Predicate)} - First match
 *   <li>{@link #forEach(Consumer)} - Side effect per element
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * List<String> names = VStreamContext.fromList(users)
 *     .filter(User::isActive)
 *     .map(User::name)
 *     .distinct()
 *     .toList();
 * }</pre>
 *
 * @param <A> the element type
 * @see VStreamPath
 * @see VStream
 */
public final class VStreamContext<A> {

  private final VStreamPath<A> path;

  private VStreamContext(VStreamPath<A> path) {
    this.path = Objects.requireNonNull(path, "path must not be null");
  }

  // ===== Factory Methods =====

  /**
   * Creates a VStreamContext wrapping an existing VStream.
   *
   * @param stream the VStream to wrap; must not be null
   * @param <A> the element type
   * @return a new VStreamContext
   * @throws NullPointerException if stream is null
   */
  public static <A> VStreamContext<A> of(VStream<A> stream) {
    Objects.requireNonNull(stream, "stream must not be null");
    return new VStreamContext<>(Path.vstream(stream));
  }

  /**
   * Creates a VStreamContext from a list. Elements are produced lazily.
   *
   * @param list the list of elements; must not be null
   * @param <A> the element type
   * @return a new VStreamContext
   * @throws NullPointerException if list is null
   */
  public static <A> VStreamContext<A> fromList(List<A> list) {
    Objects.requireNonNull(list, "list must not be null");
    return new VStreamContext<>(Path.vstreamFromList(list));
  }

  /**
   * Creates a VStreamContext containing a single element.
   *
   * @param value the value to wrap
   * @param <A> the element type
   * @return a new VStreamContext with one element
   */
  public static <A> VStreamContext<A> pure(A value) {
    return new VStreamContext<>(Path.vstreamPure(value));
  }

  /**
   * Creates an empty VStreamContext.
   *
   * @param <A> the element type
   * @return an empty VStreamContext
   */
  public static <A> VStreamContext<A> empty() {
    return new VStreamContext<>(Path.vstreamEmpty());
  }

  /**
   * Creates a VStreamContext producing integers in the given range.
   *
   * @param startInclusive the start of the range (inclusive)
   * @param endExclusive the end of the range (exclusive)
   * @return a VStreamContext of integers
   */
  public static VStreamContext<Integer> range(int startInclusive, int endExclusive) {
    return new VStreamContext<>(Path.vstreamRange(startInclusive, endExclusive));
  }

  /**
   * Creates a VStreamContext from an existing VStreamPath.
   *
   * <p>This is an escape hatch from Layer 1 for users who need to bridge from VStreamPath.
   *
   * @param path the VStreamPath to wrap; must not be null
   * @param <A> the element type
   * @return a new VStreamContext
   * @throws NullPointerException if path is null
   */
  public static <A> VStreamContext<A> fromPath(VStreamPath<A> path) {
    Objects.requireNonNull(path, "path must not be null");
    return new VStreamContext<>(path);
  }

  // ===== Transformation Operations =====

  /**
   * Transforms each element using the provided function.
   *
   * @param mapper the function to apply; must not be null
   * @param <B> the type of the transformed elements
   * @return a new VStreamContext with transformed elements
   * @throws NullPointerException if mapper is null
   */
  public <B> VStreamContext<B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new VStreamContext<>(path.map(mapper));
  }

  /**
   * Chains a dependent computation that returns a VStreamContext.
   *
   * <p>This is the monadic bind operation. Each element is substituted with a sub-stream produced
   * by the function, and all sub-streams are flattened into the result.
   *
   * @param fn the function to apply, returning a new context; must not be null
   * @param <B> the type of elements in the returned context
   * @return a flattened VStreamContext
   * @throws NullPointerException if fn is null or returns null
   */
  public <B> VStreamContext<B> via(Function<? super A, ? extends VStreamContext<B>> fn) {
    Objects.requireNonNull(fn, "fn must not be null");
    return new VStreamContext<>(
        Path.vstream(
            path.run()
                .flatMap(
                    a -> {
                      VStreamContext<B> next = fn.apply(a);
                      Objects.requireNonNull(next, "fn must not return null");
                      return next.path.run();
                    })));
  }

  /**
   * Chains a dependent computation using flatMap.
   *
   * <p>This is an alias for {@link #via(Function)}.
   *
   * @param fn the function to apply, returning a new context; must not be null
   * @param <B> the type of elements in the returned context
   * @return a flattened VStreamContext
   * @throws NullPointerException if fn is null or returns null
   */
  public <B> VStreamContext<B> flatMap(Function<? super A, ? extends VStreamContext<B>> fn) {
    return via(fn);
  }

  /**
   * Sequences an independent computation, discarding this context's elements.
   *
   * @param supplier provides the next context; must not be null
   * @param <B> the type of elements in the returned context
   * @return the context from the supplier
   * @throws NullPointerException if supplier is null or returns null
   */
  public <B> VStreamContext<B> then(Supplier<? extends VStreamContext<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return via(ignored -> supplier.get());
  }

  // ===== Filtering Operations =====

  /**
   * Filters elements using the given predicate.
   *
   * @param predicate the predicate to test elements against; must not be null
   * @return a new VStreamContext with only matching elements
   * @throws NullPointerException if predicate is null
   */
  public VStreamContext<A> filter(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new VStreamContext<>(path.filter(predicate));
  }

  /**
   * Takes at most the first {@code n} elements.
   *
   * @param n the maximum number of elements to take
   * @return a new VStreamContext limited to n elements
   */
  public VStreamContext<A> take(long n) {
    return new VStreamContext<>(path.take(n));
  }

  /**
   * Drops the first {@code n} elements.
   *
   * @param n the number of elements to drop
   * @return a new VStreamContext without the first n elements
   */
  public VStreamContext<A> drop(long n) {
    return new VStreamContext<>(path.drop(n));
  }

  /**
   * Takes elements while the predicate holds, then completes.
   *
   * @param predicate the predicate to test; must not be null
   * @return a new VStreamContext that completes when the predicate fails
   * @throws NullPointerException if predicate is null
   */
  public VStreamContext<A> takeWhile(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new VStreamContext<>(path.takeWhile(predicate));
  }

  /**
   * Drops elements while the predicate holds, then emits all remaining.
   *
   * @param predicate the predicate to test; must not be null
   * @return a new VStreamContext that skips initial matching elements
   * @throws NullPointerException if predicate is null
   */
  public VStreamContext<A> dropWhile(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new VStreamContext<>(path.dropWhile(predicate));
  }

  /**
   * Removes duplicate elements.
   *
   * <p><b>Warning:</b> For infinite streams, the internal set grows without bound.
   *
   * @return a new VStreamContext with duplicates removed
   */
  public VStreamContext<A> distinct() {
    return new VStreamContext<>(path.distinct());
  }

  /**
   * Appends another VStreamContext after this one.
   *
   * @param other the context to append; must not be null
   * @return a new VStreamContext containing elements from both
   * @throws NullPointerException if other is null
   */
  public VStreamContext<A> concat(VStreamContext<A> other) {
    Objects.requireNonNull(other, "other must not be null");
    return new VStreamContext<>(path.concat(other.path));
  }

  /**
   * Performs a side effect on each element without modifying it.
   *
   * @param consumer the action to perform; must not be null
   * @return a new VStreamContext that performs the action
   * @throws NullPointerException if consumer is null
   */
  public VStreamContext<A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    return new VStreamContext<>(path.peek(consumer));
  }

  // ===== Parallel Operations =====

  /**
   * Applies an effectful function to each element with bounded concurrency, preserving input order.
   *
   * @param concurrency the maximum number of elements to process concurrently; must be positive
   * @param f the effectful function to apply; must not be null
   * @param <B> the type of the transformed elements
   * @return a new VStreamContext with parallel-processed elements
   * @throws NullPointerException if f is null
   */
  public <B> VStreamContext<B> parEvalMap(int concurrency, Function<A, VTask<B>> f) {
    Objects.requireNonNull(f, "f must not be null");
    return new VStreamContext<>(Path.vstream(VStreamPar.parEvalMap(path.run(), concurrency, f)));
  }

  // ===== Chunking Operations =====

  /**
   * Groups elements into lists of at most {@code size} elements.
   *
   * @param size the maximum number of elements per chunk; must be positive
   * @return a new VStreamContext of element lists
   */
  public VStreamContext<List<A>> chunk(int size) {
    return new VStreamContext<>(path.chunk(size));
  }

  // ===== Terminal Operations (blocking) =====

  /**
   * Collects all elements into a list. Blocks until the stream is fully consumed.
   *
   * <p><b>Warning:</b> For infinite streams, this will not terminate. Use {@link #take(long)}
   * first.
   *
   * @return the list of all elements
   */
  public List<A> toList() {
    return path.run().toList().run();
  }

  /**
   * Returns the first element, or empty if the stream is empty. Blocks until the first element is
   * available.
   *
   * @return the first element as an Optional
   */
  public Optional<A> headOption() {
    return path.run().headOption().run();
  }

  /**
   * Counts the number of elements. Blocks until the stream is fully consumed.
   *
   * <p><b>Warning:</b> For infinite streams, this will not terminate.
   *
   * @return the element count
   */
  public long count() {
    return path.run().count().run();
  }

  /**
   * Left-folds all elements with the given identity and operator. Blocks until the stream is fully
   * consumed.
   *
   * @param identity the initial accumulator value
   * @param op the binary operator; must not be null
   * @return the fold result
   * @throws NullPointerException if op is null
   */
  public A fold(A identity, BinaryOperator<A> op) {
    Objects.requireNonNull(op, "op must not be null");
    return path.run().fold(identity, op).run();
  }

  /**
   * Checks whether any element matches the given predicate. Short-circuits on the first match.
   * Blocks until a match is found or the stream is exhausted.
   *
   * @param predicate the predicate to test; must not be null
   * @return true if any element matches
   * @throws NullPointerException if predicate is null
   */
  public boolean exists(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return path.run().exists(predicate).run();
  }

  /**
   * Checks whether all elements match the given predicate. Short-circuits on the first non-match.
   * Blocks until a non-match is found or the stream is exhausted.
   *
   * @param predicate the predicate to test; must not be null
   * @return true if all elements match
   * @throws NullPointerException if predicate is null
   */
  public boolean forAll(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return path.run().forAll(predicate).run();
  }

  /**
   * Finds the first element matching the given predicate. Blocks until a match is found or the
   * stream is exhausted.
   *
   * @param predicate the predicate to test; must not be null
   * @return the first match as an Optional
   * @throws NullPointerException if predicate is null
   */
  public Optional<A> find(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return path.run().find(predicate).run();
  }

  /**
   * Executes a side effect for each element. Blocks until all elements are processed.
   *
   * @param consumer the action to perform; must not be null
   * @throws NullPointerException if consumer is null
   */
  public void forEach(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    path.run().forEach(consumer).run();
  }

  // ===== Conversion / Escape Hatches =====

  /**
   * Returns the underlying VStream.
   *
   * <p>This is an escape hatch for users who need direct access to the VStream.
   *
   * @return the underlying VStream
   */
  public VStream<A> toVStream() {
    return path.run();
  }

  /**
   * Returns the underlying VStreamPath.
   *
   * <p>This is an escape hatch to Layer 1 for users who need full control over the VStreamPath
   * operations.
   *
   * @return the underlying VStreamPath
   */
  public VStreamPath<A> toPath() {
    return path;
  }

  @Override
  public String toString() {
    return "VStreamContext(<stream>)";
  }
}
