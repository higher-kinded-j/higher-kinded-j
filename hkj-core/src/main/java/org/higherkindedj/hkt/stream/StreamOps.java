// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.stream;

import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.tuple.Tuple2;

/**
 * Utility operations for {@link Stream} in a higher-kinded type context. This class provides
 * convenient methods for common stream operations while maintaining the HKT abstraction.
 *
 * <p>All methods in this class are static and can be accessed directly or via static import:
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.stream.StreamOps.*;
 *
 * Kind<StreamKind.Witness, Integer> numbers = range(1, 10);
 * List<Integer> materialized = toList(numbers);
 * }</pre>
 *
 * <p><b>Categories of Operations:</b>
 *
 * <ul>
 *   <li><b>Creation:</b> {@link #fromIterable}, {@link #fromArray}, {@link #range}
 *   <li><b>Materialization:</b> {@link #toList}, {@link #toSet}
 *   <li><b>Filtering:</b> {@link #filter}, {@link #take}, {@link #drop}
 *   <li><b>Combination:</b> {@link #concat}, {@link #zip}, {@link #zipWithIndex}
 *   <li><b>Effects:</b> {@link #tap}, {@link #forEach}
 * </ul>
 *
 * <p><b>Lazy vs Eager Operations:</b>
 *
 * <p>Most operations maintain laziness (filter, take, drop, concat, zip), while some necessarily
 * force evaluation (toList, toSet, forEach, zipWithIndex).
 *
 * @see StreamKind
 * @see StreamKindHelper
 * @see StreamMonad
 */
public final class StreamOps {

  private StreamOps() {
    // Utility class, no instantiation
  }

  // ========== Creation Operations ==========

  /**
   * Creates a stream from an {@link Iterable}.
   *
   * <p>This is a lazy operation - the iterable is not consumed until a terminal operation is
   * performed on the resulting stream.
   *
   * @param iterable The iterable to create a stream from. Must not be null.
   * @param <A> The element type.
   * @return A {@code Kind<StreamKind.Witness, A>} representing the stream.
   * @throws NullPointerException if iterable is null.
   */
  public static <A> Kind<StreamKind.Witness, A> fromIterable(Iterable<A> iterable) {
    if (iterable == null) {
      throw new NullPointerException("Iterable cannot be null");
    }
    return STREAM.widen(StreamSupport.stream(iterable.spliterator(), false));
  }

  /**
   * Creates a stream from an array.
   *
   * <p>This is a lazy operation - the array is not traversed until a terminal operation is
   * performed on the resulting stream.
   *
   * @param array The array to create a stream from. Must not be null.
   * @param <A> The element type.
   * @return A {@code Kind<StreamKind.Witness, A>} representing the stream.
   * @throws NullPointerException if array is null.
   */
  @SafeVarargs
  public static <A> Kind<StreamKind.Witness, A> fromArray(A... array) {
    if (array == null) {
      throw new NullPointerException("Array cannot be null");
    }
    return STREAM.widen(Arrays.stream(array));
  }

  /**
   * Creates a stream of integers in the range [start, end).
   *
   * <p>This is a lazy operation. The range is not materialized until a terminal operation is
   * performed.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> oneToNine = range(1, 10);
   * // Represents: 1, 2, 3, 4, 5, 6, 7, 8, 9
   * }</pre>
   *
   * @param start The starting value (inclusive).
   * @param end The ending value (exclusive).
   * @return A {@code Kind<StreamKind.Witness, Integer>} representing the range.
   */
  public static Kind<StreamKind.Witness, Integer> range(int start, int end) {
    return STREAM.widen(Stream.iterate(start, n -> n < end, n -> n + 1));
  }

  /**
   * Creates a stream of integers in the range [start, end].
   *
   * <p>This is a lazy operation. The range is not materialized until a terminal operation is
   * performed.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> oneToTen = rangeClosed(1, 10);
   * // Represents: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
   * }</pre>
   *
   * @param start The starting value (inclusive).
   * @param end The ending value (inclusive).
   * @return A {@code Kind<StreamKind.Witness, Integer>} representing the range.
   */
  public static Kind<StreamKind.Witness, Integer> rangeClosed(int start, int end) {
    return STREAM.widen(Stream.iterate(start, n -> n <= end, n -> n + 1));
  }

  // ========== Materialization Operations ==========

  /**
   * Forces evaluation of the stream and collects elements into a {@link List}.
   *
   * <p><b>Warning:</b> This is a terminal operation that consumes the stream. The stream cannot be
   * reused after this operation.
   *
   * @param stream The stream to materialize. Must not be null.
   * @param <A> The element type.
   * @return A List containing all elements from the stream. Never null.
   * @throws NullPointerException if stream is null.
   */
  public static <A> List<A> toList(Kind<StreamKind.Witness, A> stream) {
    if (stream == null) {
      throw new NullPointerException("Stream cannot be null");
    }
    return STREAM.narrow(stream).collect(Collectors.toList());
  }

  /**
   * Forces evaluation of the stream and collects elements into a {@link Set}.
   *
   * <p><b>Warning:</b> This is a terminal operation that consumes the stream. The stream cannot be
   * reused after this operation.
   *
   * <p><b>Note:</b> Duplicate elements will be removed based on their {@code equals} and {@code
   * hashCode} implementations.
   *
   * @param stream The stream to materialize. Must not be null.
   * @param <A> The element type.
   * @return A Set containing all unique elements from the stream. Never null.
   * @throws NullPointerException if stream is null.
   */
  public static <A> Set<A> toSet(Kind<StreamKind.Witness, A> stream) {
    if (stream == null) {
      throw new NullPointerException("Stream cannot be null");
    }
    return STREAM.narrow(stream).collect(Collectors.toSet());
  }

  // ========== Filtering Operations ==========

  /**
   * Filters the stream, keeping only elements that match the predicate.
   *
   * <p>This is a lazy operation - the predicate is not applied until a terminal operation is
   * performed on the resulting stream.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> numbers = range(1, 10);
   * Kind<StreamKind.Witness, Integer> evens = filter(n -> n % 2 == 0, numbers);
   * // Represents: 2, 4, 6, 8
   * }</pre>
   *
   * @param predicate The predicate to test elements. Must not be null.
   * @param stream The stream to filter. Must not be null.
   * @param <A> The element type.
   * @return A filtered stream containing only matching elements.
   * @throws NullPointerException if predicate or stream is null.
   */
  public static <A> Kind<StreamKind.Witness, A> filter(
      Predicate<? super A> predicate, Kind<StreamKind.Witness, A> stream) {
    if (predicate == null) {
      throw new NullPointerException("Predicate cannot be null");
    }
    if (stream == null) {
      throw new NullPointerException("Stream cannot be null");
    }
    return STREAM.widen(STREAM.narrow(stream).filter(predicate));
  }

  /**
   * Takes the first {@code n} elements from the stream.
   *
   * <p>This is a lazy operation - elements are not materialized until a terminal operation is
   * performed on the resulting stream.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> infinite = STREAM.widen(Stream.iterate(1, n -> n + 1));
   * Kind<StreamKind.Witness, Integer> firstTen = take(10, infinite);
   * // Represents: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
   * }</pre>
   *
   * @param n The number of elements to take. Must be non-negative.
   * @param stream The stream to limit. Must not be null.
   * @param <A> The element type.
   * @return A stream containing at most the first {@code n} elements.
   * @throws IllegalArgumentException if n is negative.
   * @throws NullPointerException if stream is null.
   */
  public static <A> Kind<StreamKind.Witness, A> take(long n, Kind<StreamKind.Witness, A> stream) {
    if (n < 0) {
      throw new IllegalArgumentException("Cannot take negative number of elements: " + n);
    }
    if (stream == null) {
      throw new NullPointerException("Stream cannot be null");
    }
    return STREAM.widen(STREAM.narrow(stream).limit(n));
  }

  /**
   * Drops the first {@code n} elements from the stream.
   *
   * <p>This is a lazy operation - elements are not skipped until a terminal operation is performed
   * on the resulting stream.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> numbers = range(1, 10);
   * Kind<StreamKind.Witness, Integer> afterFive = drop(5, numbers);
   * // Represents: 6, 7, 8, 9
   * }</pre>
   *
   * @param n The number of elements to skip. Must be non-negative.
   * @param stream The stream to skip from. Must not be null.
   * @param <A> The element type.
   * @return A stream with the first {@code n} elements skipped.
   * @throws IllegalArgumentException if n is negative.
   * @throws NullPointerException if stream is null.
   */
  public static <A> Kind<StreamKind.Witness, A> drop(long n, Kind<StreamKind.Witness, A> stream) {
    if (n < 0) {
      throw new IllegalArgumentException("Cannot drop negative number of elements: " + n);
    }
    if (stream == null) {
      throw new NullPointerException("Stream cannot be null");
    }
    return STREAM.widen(STREAM.narrow(stream).skip(n));
  }

  // ========== Combination Operations ==========

  /**
   * Concatenates two streams.
   *
   * <p>This is a lazy operation - neither stream is evaluated until a terminal operation is
   * performed on the resulting stream.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> first = range(1, 5);
   * Kind<StreamKind.Witness, Integer> second = range(5, 10);
   * Kind<StreamKind.Witness, Integer> combined = concat(first, second);
   * // Represents: 1, 2, 3, 4, 5, 6, 7, 8, 9
   * }</pre>
   *
   * @param s1 The first stream. Must not be null.
   * @param s2 The second stream. Must not be null.
   * @param <A> The element type.
   * @return A stream containing all elements from s1 followed by all elements from s2.
   * @throws NullPointerException if s1 or s2 is null.
   */
  public static <A> Kind<StreamKind.Witness, A> concat(
      Kind<StreamKind.Witness, A> s1, Kind<StreamKind.Witness, A> s2) {
    if (s1 == null || s2 == null) {
      throw new NullPointerException("Streams cannot be null");
    }
    return STREAM.widen(Stream.concat(STREAM.narrow(s1), STREAM.narrow(s2)));
  }

  /**
   * Zips two streams together using a combining function.
   *
   * <p>The resulting stream ends when either input stream ends. Elements are paired in order.
   *
   * <p>This is a lazy operation - the zipper function is not applied until a terminal operation is
   * performed on the resulting stream.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, String> names = fromArray("Alice", "Bob", "Charlie");
   * Kind<StreamKind.Witness, Integer> ages = fromArray(25, 30, 35);
   * Kind<StreamKind.Witness, String> profiles = zip(names, ages,
   *     (name, age) -> name + " is " + age + " years old");
   * // Represents: "Alice is 25 years old", "Bob is 30 years old", "Charlie is 35 years old"
   * }</pre>
   *
   * @param sa The first stream. Must not be null.
   * @param sb The second stream. Must not be null.
   * @param zipper The function to combine elements. Must not be null.
   * @param <A> The type of elements in the first stream.
   * @param <B> The type of elements in the second stream.
   * @param <C> The type of elements in the result stream.
   * @return A stream of combined elements.
   * @throws NullPointerException if any parameter is null.
   */
  public static <A, B, C> Kind<StreamKind.Witness, C> zip(
      Kind<StreamKind.Witness, A> sa,
      Kind<StreamKind.Witness, B> sb,
      BiFunction<? super A, ? super B, ? extends C> zipper) {
    if (sa == null || sb == null || zipper == null) {
      throw new NullPointerException("Parameters cannot be null");
    }

    Iterator<A> iterA = STREAM.narrow(sa).iterator();
    Iterator<B> iterB = STREAM.narrow(sb).iterator();

    // Create an iterator that zips the two streams
    Iterator<C> zippedIterator =
        new Iterator<>() {
          @Override
          public boolean hasNext() {
            return iterA.hasNext() && iterB.hasNext();
          }

          @Override
          public C next() {
            return zipper.apply(iterA.next(), iterB.next());
          }
        };

    // Convert iterator to stream
    Stream<C> zippedStream =
        StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(zippedIterator, Spliterator.ORDERED), false);

    return STREAM.widen(zippedStream);
  }

  /**
   * Pairs each element in the stream with its zero-based index.
   *
   * <p><b>Warning:</b> This implementation uses a stateful function and is **not safe** for use
   * with parallel streams. It will produce non-deterministic and incorrect indices if the stream is
   * parallel. It should only be used with sequential streams. p>The operation is lazy for
   * sequential streams.
   *
   * <p><b>Warning:</b> Due to the nature of indexing, this operation must materialize the stream
   * into a list first, then recreate a stream with indices. This is not truly lazy.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, String> names = fromArray("Alice", "Bob", "Charlie");
   * Kind<StreamKind.Witness, Tuple2<Integer, String>> indexed = zipWithIndex(names);
   * // Represents: (0, "Alice"), (1, "Bob"), (2, "Charlie")
   * }</pre>
   *
   * @param stream The stream to index. Must not be null.
   * @param <A> The element type.
   * @return A stream of tuples where each tuple contains (index, element).
   * @throws NullPointerException if stream is null.
   */
  public static <A> Kind<StreamKind.Witness, Tuple2<Integer, A>> zipWithIndex(
      Kind<StreamKind.Witness, A> stream) {
    if (stream == null) {
      throw new NullPointerException("Stream cannot be null");
    }

    AtomicInteger index = new AtomicInteger(0);
    return STREAM.widen(
        STREAM.narrow(stream).map(elem -> new Tuple2<>(index.getAndIncrement(), elem)));
  }

  // ========== Effect Operations ==========

  /**
   * Performs a side effect on each element without modifying the stream.
   *
   * <p>This is a lazy operation - the action is not performed until a terminal operation is
   * executed on the resulting stream. This is equivalent to {@code Stream.peek()}.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> numbers = range(1, 5);
   * Kind<StreamKind.Witness, Integer> logged = tap(n -> System.out.println("Processing: " + n), numbers);
   * // Nothing printed yet - lazily evaluated
   * List<Integer> result = toList(logged);
   * // Now "Processing: 1", "Processing: 2", etc. are printed
   * }</pre>
   *
   * @param action The action to perform on each element. Must not be null.
   * @param stream The stream to tap. Must not be null.
   * @param <A> The element type.
   * @return The same stream, but with the side effect registered.
   * @throws NullPointerException if action or stream is null.
   */
  public static <A> Kind<StreamKind.Witness, A> tap(
      Consumer<? super A> action, Kind<StreamKind.Witness, A> stream) {
    if (action == null) {
      throw new NullPointerException("Action cannot be null");
    }
    if (stream == null) {
      throw new NullPointerException("Stream cannot be null");
    }
    return STREAM.widen(STREAM.narrow(stream).peek(action));
  }

  /**
   * Performs an action on each element of the stream, forcing evaluation.
   *
   * <p><b>Warning:</b> This is a terminal operation that consumes the stream. The stream cannot be
   * reused after this operation. No value is returned.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, String> messages = fromArray("Hello", "World");
   * forEach(System.out::println, messages);
   * // Prints: Hello\nWorld
   * }</pre>
   *
   * @param action The action to perform on each element. Must not be null.
   * @param stream The stream to process. Must not be null.
   * @param <A> The element type.
   * @throws NullPointerException if action or stream is null.
   */
  public static <A> void forEach(Consumer<? super A> action, Kind<StreamKind.Witness, A> stream) {
    if (action == null) {
      throw new NullPointerException("Action cannot be null");
    }
    if (stream == null) {
      throw new NullPointerException("Stream cannot be null");
    }
    STREAM.narrow(stream).forEach(action);
  }
}
