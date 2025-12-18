// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * A fluent path wrapper for {@link Stream} representing lazy sequence computations.
 *
 * <p>{@code StreamPath} provides lazy evaluation of sequences. Operations are not executed until a
 * terminal operation is called. Unlike raw Streams, StreamPath uses a supplier to allow multiple
 * terminal operations.
 *
 * <h2>Important</h2>
 *
 * <p>StreamPath materializes the stream supplier into a list for reusability. For very large or
 * infinite streams, use terminal operations carefully or use {@link #take(long)} first.
 *
 * <h2>Use Cases</h2>
 *
 * <ul>
 *   <li>Large data processing
 *   <li>Infinite sequences (with limit)
 *   <li>Pipeline transformations
 *   <li>Memory-efficient processing
 * </ul>
 *
 * <h2>Creating StreamPath instances</h2>
 *
 * <pre>{@code
 * // From a stream
 * StreamPath<Integer> numbers = StreamPath.of(Stream.of(1, 2, 3));
 *
 * // From a list
 * StreamPath<String> fromList = StreamPath.fromList(myList);
 *
 * // Infinite sequence
 * StreamPath<Integer> naturals = StreamPath.iterate(1, n -> n + 1);
 *
 * // Generate values
 * StreamPath<Double> randoms = StreamPath.generate(Math::random);
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <pre>{@code
 * List<Integer> firstTenSquares = StreamPath.iterate(1, n -> n + 1)
 *     .map(n -> n * n)
 *     .take(10)
 *     .toList();
 * }</pre>
 *
 * @param <A> the element type
 */
public final class StreamPath<A> implements Chainable<A> {

  private final Supplier<Stream<A>> streamSupplier;

  /**
   * Creates a new StreamPath with the given stream supplier.
   *
   * @param streamSupplier the supplier for streams; must not be null
   */
  StreamPath(Supplier<Stream<A>> streamSupplier) {
    this.streamSupplier = Objects.requireNonNull(streamSupplier, "streamSupplier must not be null");
  }

  // ===== Factory Methods =====

  /**
   * Creates a StreamPath from a stream.
   *
   * <p>Note: The stream is materialized to a list to allow multiple terminal operations.
   *
   * @param stream the stream to wrap; must not be null
   * @param <A> the element type
   * @return a StreamPath wrapping the stream
   * @throws NullPointerException if stream is null
   */
  public static <A> StreamPath<A> of(Stream<A> stream) {
    Objects.requireNonNull(stream, "stream must not be null");
    // Materialize to allow multiple uses
    List<A> materialized = stream.collect(Collectors.toList());
    return new StreamPath<>(materialized::stream);
  }

  /**
   * Creates a StreamPath from a supplier that produces streams.
   *
   * <p>The supplier is called fresh each time a terminal operation is performed.
   *
   * @param supplier the stream supplier; must not be null
   * @param <A> the element type
   * @return a StreamPath using the supplier
   * @throws NullPointerException if supplier is null
   */
  public static <A> StreamPath<A> fromSupplier(Supplier<Stream<A>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return new StreamPath<>(supplier);
  }

  /**
   * Creates a StreamPath from a list.
   *
   * @param list the list to wrap; must not be null
   * @param <A> the element type
   * @return a StreamPath streaming the list
   * @throws NullPointerException if list is null
   */
  public static <A> StreamPath<A> fromList(List<A> list) {
    Objects.requireNonNull(list, "list must not be null");
    return new StreamPath<>(list::stream);
  }

  /**
   * Creates a StreamPath from varargs.
   *
   * @param elements the elements
   * @param <A> the element type
   * @return a StreamPath containing the elements
   */
  @SafeVarargs
  public static <A> StreamPath<A> of(A... elements) {
    List<A> list = Arrays.asList(elements);
    return new StreamPath<>(list::stream);
  }

  /**
   * Creates a StreamPath with a single element.
   *
   * @param value the single element
   * @param <A> the element type
   * @return a StreamPath containing one element
   */
  public static <A> StreamPath<A> pure(A value) {
    return new StreamPath<>(() -> Stream.of(value));
  }

  /**
   * Creates an empty StreamPath.
   *
   * @param <A> the element type
   * @return an empty StreamPath
   */
  public static <A> StreamPath<A> empty() {
    return new StreamPath<>(Stream::empty);
  }

  /**
   * Creates an infinite StreamPath by iterating a function.
   *
   * <p><b>Warning:</b> This creates an infinite stream. Use {@link #take(long)} to limit.
   *
   * @param seed the initial value
   * @param f the function to generate next values; must not be null
   * @param <A> the element type
   * @return an infinite StreamPath
   * @throws NullPointerException if f is null
   */
  public static <A> StreamPath<A> iterate(A seed, UnaryOperator<A> f) {
    Objects.requireNonNull(f, "f must not be null");
    return new StreamPath<>(() -> Stream.iterate(seed, f));
  }

  /**
   * Creates an infinite StreamPath from a supplier.
   *
   * <p><b>Warning:</b> This creates an infinite stream. Use {@link #take(long)} to limit.
   *
   * @param supplier the element supplier; must not be null
   * @param <A> the element type
   * @return an infinite StreamPath
   * @throws NullPointerException if supplier is null
   */
  public static <A> StreamPath<A> generate(Supplier<A> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return new StreamPath<>(() -> Stream.generate(supplier));
  }

  // ===== Terminal Operations =====

  /**
   * Returns a fresh stream for consumption.
   *
   * @return a new stream from the supplier
   */
  public Stream<A> run() {
    return streamSupplier.get();
  }

  /**
   * Collects to a list.
   *
   * @return a list containing all elements
   */
  public List<A> toList() {
    return run().collect(Collectors.toList());
  }

  /**
   * Returns the first element, or empty if the stream is empty.
   *
   * @return an Optional containing the first element if present
   */
  public Optional<A> headOption() {
    return run().findFirst();
  }

  /**
   * Counts elements.
   *
   * @return the number of elements
   */
  public long count() {
    return run().count();
  }

  // ===== Composable implementation =====

  @Override
  public <B> StreamPath<B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new StreamPath<>(() -> streamSupplier.get().map(mapper));
  }

  @Override
  public StreamPath<A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    return new StreamPath<>(() -> streamSupplier.get().peek(consumer));
  }

  // ===== Combinable implementation =====

  @Override
  public <B, C> StreamPath<C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof StreamPath<?> otherStream)) {
      throw new IllegalArgumentException("Cannot zipWith non-StreamPath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    StreamPath<B> typedOther = (StreamPath<B>) otherStream;

    // Cartesian product - all combinations (materializes streams)
    return new StreamPath<>(
        () -> {
          List<A> thisElements = this.toList();
          List<B> otherElements = typedOther.toList();
          return thisElements.stream()
              .flatMap(a -> otherElements.stream().map(b -> combiner.apply(a, b)));
        });
  }

  // ===== Chainable implementation =====

  @Override
  public <B> StreamPath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    return new StreamPath<>(
        () ->
            streamSupplier
                .get()
                .flatMap(
                    a -> {
                      Chainable<B> result = mapper.apply(a);
                      Objects.requireNonNull(result, "mapper must not return null");

                      if (!(result instanceof StreamPath<?> streamPath)) {
                        throw new IllegalArgumentException(
                            "via mapper must return StreamPath, got: " + result.getClass());
                      }

                      @SuppressWarnings("unchecked")
                      StreamPath<B> typedResult = (StreamPath<B>) streamPath;
                      return typedResult.run();
                    }));
  }

  @Override
  public <B> StreamPath<B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return via(ignored -> supplier.get());
  }

  // ===== Stream-Specific Operations =====

  /**
   * Filters elements based on a predicate.
   *
   * @param predicate the condition to test; must not be null
   * @return a new StreamPath with only matching elements
   * @throws NullPointerException if predicate is null
   */
  public StreamPath<A> filter(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new StreamPath<>(() -> streamSupplier.get().filter(predicate));
  }

  /**
   * Takes the first n elements.
   *
   * @param n the number of elements to take
   * @return a new StreamPath with at most n elements
   */
  public StreamPath<A> take(long n) {
    return new StreamPath<>(() -> streamSupplier.get().limit(n));
  }

  /**
   * Drops the first n elements.
   *
   * @param n the number of elements to skip
   * @return a new StreamPath without the first n elements
   */
  public StreamPath<A> drop(long n) {
    return new StreamPath<>(() -> streamSupplier.get().skip(n));
  }

  /**
   * Takes elements while predicate is true.
   *
   * @param predicate the condition; must not be null
   * @return a new StreamPath with elements taken while predicate holds
   * @throws NullPointerException if predicate is null
   */
  public StreamPath<A> takeWhile(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new StreamPath<>(() -> streamSupplier.get().takeWhile(predicate));
  }

  /**
   * Drops elements while predicate is true.
   *
   * @param predicate the condition; must not be null
   * @return a new StreamPath with elements after predicate stops holding
   * @throws NullPointerException if predicate is null
   */
  public StreamPath<A> dropWhile(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new StreamPath<>(() -> streamSupplier.get().dropWhile(predicate));
  }

  /**
   * Returns distinct elements.
   *
   * @return a new StreamPath with duplicates removed
   */
  public StreamPath<A> distinct() {
    return new StreamPath<>(() -> streamSupplier.get().distinct());
  }

  /**
   * Sorts elements (natural ordering).
   *
   * @return a new StreamPath with sorted elements
   */
  public StreamPath<A> sorted() {
    return new StreamPath<>(() -> streamSupplier.get().sorted());
  }

  /**
   * Sorts elements using a comparator.
   *
   * @param comparator the comparator to use; must not be null
   * @return a new StreamPath with sorted elements
   * @throws NullPointerException if comparator is null
   */
  public StreamPath<A> sorted(Comparator<? super A> comparator) {
    Objects.requireNonNull(comparator, "comparator must not be null");
    return new StreamPath<>(() -> streamSupplier.get().sorted(comparator));
  }

  /**
   * Concatenates with another StreamPath.
   *
   * @param other the other StreamPath; must not be null
   * @return a new StreamPath containing elements from both
   * @throws NullPointerException if other is null
   */
  public StreamPath<A> concat(StreamPath<A> other) {
    Objects.requireNonNull(other, "other must not be null");
    return new StreamPath<>(() -> Stream.concat(streamSupplier.get(), other.run()));
  }

  /**
   * Folds the stream from the left.
   *
   * @param initial the initial accumulator value
   * @param f the folding function; must not be null
   * @param <B> the accumulator and result type
   * @return the folded result
   * @throws NullPointerException if f is null
   */
  public <B> B foldLeft(B initial, BiFunction<B, A, B> f) {
    Objects.requireNonNull(f, "f must not be null");
    B result = initial;
    Iterator<A> iter = run().iterator();
    while (iter.hasNext()) {
      result = f.apply(result, iter.next());
    }
    return result;
  }

  // ===== Conversions =====

  /**
   * Converts to NonDetPath (materializes the stream).
   *
   * @return a NonDetPath containing the same elements
   */
  public NonDetPath<A> toNonDetPath() {
    return NonDetPath.of(toList());
  }

  /**
   * Converts to MaybePath with the first element.
   *
   * @return a MaybePath containing the first element if present
   */
  public MaybePath<A> toMaybePath() {
    return headOption()
        .map(a -> new MaybePath<>(Maybe.just(a)))
        .orElse(new MaybePath<>(Maybe.nothing()));
  }

  /**
   * Converts to an IOPath that returns this stream's list.
   *
   * @return an IOPath that produces this stream as a list
   */
  public IOPath<List<A>> toIOPath() {
    return new IOPath<>(this::toList);
  }

  // ===== Object methods =====

  @Override
  public String toString() {
    return "StreamPath(<stream>)";
  }

  // Note: equals and hashCode not implemented because streams are not comparable
}
