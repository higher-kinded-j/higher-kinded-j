// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic;

import module java.base;
import module org.higherkindedj.core;

import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;
import static org.higherkindedj.hkt.stream.StreamOps.*;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.stream.StreamKind;
import org.higherkindedj.hkt.stream.StreamMonad;
import org.higherkindedj.hkt.tuple.Tuple2;

/**
 * Demonstrates using Stream with Higher-Kinded-J's type class abstractions.
 *
 * <p>Streams in Java are lazy, single-use data structures. This example shows how to work with
 * Streams using the Functor, Applicative, and Monad abstractions while respecting their unique
 * single-use semantics.
 *
 * <p>See <a href="https://higher-kinded-j.github.io/usage-guide.html">Usage Guide</a>
 */
public class StreamExample {

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    System.out.println("=== Basic Stream Operations ===");
    basicStreamOperations();

    System.out.println("\n=== Stream-Specific Operations ===");
    streamSpecificOperations();

    System.out.println("\n=== Laziness and Side Effects ===");
    lazinessDemo();

    System.out.println("\n=== Combining Streams ===");
    combiningStreams();

    System.out.println("\n=== Generic Functor Example ===");
    genericFunctorExample();

    System.out.println("\n=== Important: Single-Use Semantics ===");
    singleUseSemantics();
  }

  /** Demonstrates basic Functor and Monad operations with Streams. */
  public void basicStreamOperations() {
    StreamMonad streamMonad = StreamMonad.INSTANCE;

    // Create a Stream and wrap it in Kind
    Stream<Integer> numbers = Stream.of(1, 2, 3, 4, 5);
    Kind<StreamKind.Witness, Integer> streamKind = STREAM.widen(numbers);

    // --- Using map (Functor) ---
    Function<Integer, String> toString = n -> "Number: " + n;
    Kind<StreamKind.Witness, String> mappedKind = streamMonad.map(toString, streamKind);

    // Narrow back to Stream and collect to see results
    List<String> result = toList(mappedKind);
    System.out.println("Mapped: " + result);
    // Output: [Number: 1, Number: 2, Number: 3, Number: 4, Number: 5]

    // --- Using flatMap (Monad) ---
    // Create a new stream for flatMap (remember: streams are single-use!)
    Kind<StreamKind.Witness, Integer> streamKind2 = STREAM.widen(Stream.of(1, 2, 3));

    // Function that returns a Stream Kind for each element
    Function<Integer, Kind<StreamKind.Witness, String>> duplicate =
        n -> STREAM.widen(Stream.of("A" + n, "B" + n));

    Kind<StreamKind.Witness, String> flatMappedKind = streamMonad.flatMap(duplicate, streamKind2);

    List<String> flatMapResult = toList(flatMappedKind);
    System.out.println("FlatMapped: " + flatMapResult);
    // Output: [A1, B1, A2, B2, A3, B3]

    // --- Using of (Applicative) ---
    Kind<StreamKind.Witness, Integer> singletonKind = streamMonad.of(42);
    System.out.println("Singleton: " + toList(singletonKind));
    // Output: [42]

    // --- Using zero (MonadZero) ---
    Kind<StreamKind.Witness, String> emptyKind = streamMonad.zero();
    System.out.println("Empty: " + toList(emptyKind));
    // Output: []
  }

  /** Demonstrates Stream-specific operations from StreamOps. */
  public void streamSpecificOperations() {
    // --- filter: Keep only matching elements ---
    Kind<StreamKind.Witness, Integer> numbers = range(1, 11);
    Kind<StreamKind.Witness, Integer> evens = filter(n -> n % 2 == 0, numbers);
    System.out.println("Evens: " + toList(evens));
    // Output: [2, 4, 6, 8, 10]

    // --- take: Limit to first N elements ---
    Kind<StreamKind.Witness, Integer> first5 = take(5, range(1, 100));
    System.out.println("First 5: " + toList(first5));
    // Output: [1, 2, 3, 4, 5]

    // --- drop: Skip first N elements ---
    Kind<StreamKind.Witness, Integer> after3 = drop(3, range(1, 8));
    System.out.println("After dropping 3: " + toList(after3));
    // Output: [4, 5, 6, 7]

    // --- zipWithIndex: Pair elements with their indices ---
    Kind<StreamKind.Witness, String> names = fromArray("Alice", "Bob", "Charlie");
    Kind<StreamKind.Witness, Tuple2<Integer, String>> indexed = zipWithIndex(names);

    List<Tuple2<Integer, String>> indexedResult = toList(indexed);
    System.out.println("With indices:");
    for (Tuple2<Integer, String> tuple : indexedResult) {
      System.out.println("  " + tuple._1() + ": " + tuple._2());
    }
    // Output: 0: Alice, 1: Bob, 2: Charlie
  }

  /** Demonstrates laziness and side effects with tap and forEach. */
  public void lazinessDemo() {
    List<String> log = new ArrayList<>();

    // Create a stream with side effects using tap
    Kind<StreamKind.Witness, Integer> stream =
        tap(n -> log.add("Processing: " + n), StreamMonad.INSTANCE.map(n -> n * 2, range(1, 4)));

    System.out.println("Stream created, log size: " + log.size());
    // Output: 0 (stream is lazy, tap hasn't executed yet)

    // Force evaluation by collecting
    List<Integer> result = toList(stream);
    System.out.println("After evaluation, log size: " + log.size());
    // Output: 3 (tap executed during evaluation)
    System.out.println("Log: " + log);
    // Output: [Processing: 2, Processing: 4, Processing: 6]
    System.out.println("Result: " + result);
    // Output: [2, 4, 6]

    // --- forEach: Execute side effects and consume stream ---
    List<String> captured = new ArrayList<>();
    Kind<StreamKind.Witness, String> messages = fromArray("Hello", "World");
    forEach(captured::add, messages);
    System.out.println("Captured via forEach: " + captured);
    // Output: [Hello, World]
  }

  /** Demonstrates combining streams with concat and zip. */
  public void combiningStreams() {
    // --- concat: Combine two streams sequentially ---
    Kind<StreamKind.Witness, Integer> first = range(1, 4);
    Kind<StreamKind.Witness, Integer> second = range(10, 13);
    Kind<StreamKind.Witness, Integer> combined = concat(first, second);
    System.out.println("Concatenated: " + toList(combined));
    // Output: [1, 2, 3, 10, 11, 12]

    // --- zip: Combine two streams element-wise ---
    Kind<StreamKind.Witness, String> names = fromArray("Alice", "Bob", "Charlie");
    Kind<StreamKind.Witness, Integer> ages = fromArray(25, 30, 35);

    Kind<StreamKind.Witness, String> profiles =
        zip(names, ages, (name, age) -> name + " is " + age + " years old");

    System.out.println("Zipped profiles:");
    toList(profiles).forEach(System.out::println);
    // Output:
    // Alice is 25 years old
    // Bob is 30 years old
    // Charlie is 35 years old
  }

  /** Shows using Stream with generic type class constraints. */
  public void genericFunctorExample() {
    StreamMonad streamMonad = StreamMonad.INSTANCE;

    // Generic function that works with any Functor
    Function<Integer, Integer> square = n -> n * n;

    Kind<StreamKind.Witness, Integer> numbers = range(1, 6);
    Kind<StreamKind.Witness, Integer> squared = streamMonad.map(square, numbers);

    System.out.println("Squared: " + toList(squared));
    // Output: [1, 4, 9, 16, 25]

    // Chaining operations
    Kind<StreamKind.Witness, Integer> pipeline =
        streamMonad.map(n -> n + 100, filter(n -> n > 10, streamMonad.map(square, range(1, 10))));

    System.out.println("Pipeline result: " + toList(pipeline));
    // Output: [116, 125, 136, 149, 164, 181]
  }

  /**
   * IMPORTANT: Demonstrates Stream's single-use semantics.
   *
   * <p>Unlike List or Optional, Java Streams can only be consumed once. Attempting to reuse a
   * consumed stream throws IllegalStateException.
   */
  public void singleUseSemantics() {
    Kind<StreamKind.Witness, Integer> streamKind = range(1, 4);

    // First use: collect to list
    List<Integer> result1 = toList(streamKind);
    System.out.println("First use: " + result1);

    // DANGER: Cannot reuse the same streamKind!
    // The following would throw IllegalStateException:
    // List<Integer> result2 = toList(streamKind);

    System.out.println(
        "Note: Each stream can only be used once. Create fresh streams for each operation.");

    // Correct approach: Create a new stream for each use
    Kind<StreamKind.Witness, Integer> streamKind2 = range(1, 4);
    List<Integer> result2 = toList(streamKind2);
    System.out.println("Second use (new stream): " + result2);
  }
}
