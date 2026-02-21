// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: VStreamPath - Fluent Streaming with Effect Path API
 *
 * <p>VStreamPath wraps VStream to provide a fluent, chainable API for lazy streaming computations
 * on virtual threads. All intermediate operations are lazy; execution is deferred until a terminal
 * operation is called.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>Path factory methods create VStreamPath instances
 *   <li>map, filter, flatMap compose lazy pipelines
 *   <li>Terminal operations (toList, fold, count) return VTaskPath
 *   <li>zipWith pairs streams positionally
 *   <li>Focus bridge navigates into stream elements with optics
 *   <li>Conversions bridge to StreamPath, ListPath, NonDetPath
 * </ul>
 *
 * <p>Prerequisites: Complete VStream Basics and VStream HKT tutorials first.
 *
 * <p>Estimated time: 15 minutes
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial: VStreamPath")
public class TutorialVStreamPath {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: Creating VStreamPath Instances
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating VStreamPath Instances")
  class CreatingInstances {

    /**
     * Exercise 1: Create a VStreamPath from varargs
     *
     * <p>The Path factory class provides methods for creating VStreamPath instances. The simplest
     * is Path.vstreamOf() which takes varargs.
     *
     * <p>Task: Create a VStreamPath containing the numbers 1, 2, 3 and collect to a list
     */
    @Test
    @DisplayName("Exercise 1: Create from varargs")
    void exercise1_createFromVarargs() {
      // TODO: Replace answerRequired() with Path.vstreamOf(1, 2, 3)
      VStreamPath<Integer> path = answerRequired();

      assertThat(path.toList().unsafeRun()).containsExactly(1, 2, 3);
    }

    /**
     * Exercise 2: Create a VStreamPath from a range
     *
     * <p>Path.vstreamRange(start, end) creates an integer range [start, end).
     *
     * <p>Task: Create a VStreamPath for the range [1, 6) and collect to a list
     */
    @Test
    @DisplayName("Exercise 2: Create from range")
    void exercise2_createFromRange() {
      // TODO: Replace answerRequired() with Path.vstreamRange(1, 6)
      VStreamPath<Integer> path = answerRequired();

      assertThat(path.toList().unsafeRun()).containsExactly(1, 2, 3, 4, 5);
    }

    /**
     * Exercise 3: Create an infinite stream and take elements
     *
     * <p>Path.vstreamIterate(seed, f) creates an infinite stream. Use take(n) to limit it.
     *
     * <p>Task: Create an infinite stream starting at 10, adding 10 each step, and take the first 4
     */
    @Test
    @DisplayName("Exercise 3: Infinite stream with take")
    void exercise3_infiniteStreamWithTake() {
      // TODO: Replace answerRequired() with Path.vstreamIterate(10, n -> n + 10).take(4)
      VStreamPath<Integer> path = answerRequired();

      assertThat(path.toList().unsafeRun()).containsExactly(10, 20, 30, 40);
    }

    /**
     * Exercise 4: Effectful unfold
     *
     * <p>Path.vstreamUnfold(seed, f) creates a stream by repeatedly applying an effectful function
     * to a state. The function returns Optional.empty() to signal completion.
     *
     * <p>Task: Unfold from seed 1, emitting state and incrementing, stopping when state > 3
     */
    @Test
    @DisplayName("Exercise 4: Effectful unfold")
    void exercise4_effectfulUnfold() {
      // TODO: Replace answerRequired() with Path.vstreamUnfold(1, state -> ...)
      // Hint: Return VTask.succeed(state > 3 ? Optional.empty()
      //         : Optional.of(new VStream.Seed<>(state, state + 1)))
      VStreamPath<Integer> path = answerRequired();

      assertThat(path.toList().unsafeRun()).containsExactly(1, 2, 3);
    }
  }

  // ===========================================================================
  // Part 2: Composing Operations
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Composing Operations")
  class ComposingOperations {

    /**
     * Exercise 5: Map elements
     *
     * <p>map() transforms each element lazily. Nothing executes until a terminal operation.
     *
     * <p>Task: Map the stream of names to uppercase
     */
    @Test
    @DisplayName("Exercise 5: Map elements")
    void exercise5_mapElements() {
      VStreamPath<String> names = Path.vstreamOf("alice", "bob");

      // TODO: Replace answerRequired() with names.map(String::toUpperCase)
      VStreamPath<String> upper = answerRequired();

      assertThat(upper.toList().unsafeRun()).containsExactly("ALICE", "BOB");
    }

    /**
     * Exercise 6: Filter elements
     *
     * <p>filter() keeps only elements matching the predicate.
     *
     * <p>Task: Filter the range to keep only even numbers
     */
    @Test
    @DisplayName("Exercise 6: Filter elements")
    void exercise6_filterElements() {
      VStreamPath<Integer> range = Path.vstreamRange(1, 11);

      // TODO: Replace answerRequired() with range.filter(n -> n % 2 == 0)
      VStreamPath<Integer> evens = answerRequired();

      assertThat(evens.toList().unsafeRun()).containsExactly(2, 4, 6, 8, 10);
    }

    /**
     * Exercise 7: FlatMap to sub-streams
     *
     * <p>flatMap() (or via()) expands each element into a sub-stream, then flattens the results.
     *
     * <p>Task: Expand each number into a pair [n, n*10]
     */
    @Test
    @DisplayName("Exercise 7: FlatMap to sub-streams")
    void exercise7_flatMapToSubStreams() {
      VStreamPath<Integer> nums = Path.vstreamOf(1, 2, 3);

      // TODO: Replace answerRequired() with nums.flatMap(n -> Path.vstreamOf(n, n * 10))
      VStreamPath<Integer> expanded = answerRequired();

      assertThat(expanded.toList().unsafeRun()).containsExactly(1, 10, 2, 20, 3, 30);
    }

    /**
     * Exercise 8: Compose a pipeline
     *
     * <p>Chain multiple operations together. All are lazy until the terminal toList().
     *
     * <p>Task: From range [1,100), keep evens, map to strings, take 3
     */
    @Test
    @DisplayName("Exercise 8: Compose a pipeline")
    void exercise8_composePipeline() {
      // TODO: Replace answerRequired() with a pipeline:
      //   Path.vstreamRange(1, 100).filter(n -> n % 2 == 0).map(n -> "Even:" + n).take(3)
      VStreamPath<String> pipeline = answerRequired();

      assertThat(pipeline.toList().unsafeRun()).containsExactly("Even:2", "Even:4", "Even:6");
    }
  }

  // ===========================================================================
  // Part 3: Terminal Operations and Zipping
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Terminal Operations and Zipping")
  class TerminalAndZipping {

    /**
     * Exercise 9: Fold to a sum
     *
     * <p>Terminal operations return VTaskPath. fold() reduces elements with a seed and operator.
     *
     * <p>Task: Fold the stream to compute the sum
     */
    @Test
    @DisplayName("Exercise 9: Fold to sum")
    void exercise9_foldToSum() {
      VStreamPath<Integer> stream = Path.vstreamOf(1, 2, 3, 4, 5);

      // TODO: Replace answerRequired() with stream.fold(0, Integer::sum).unsafeRun()
      Integer sum = answerRequired();

      assertThat(sum).isEqualTo(15);
    }

    /**
     * Exercise 10: Check existence
     *
     * <p>exists() short-circuits; it returns true as soon as one element matches.
     *
     * <p>Task: Check if any element is greater than 4
     */
    @Test
    @DisplayName("Exercise 10: Check existence")
    void exercise10_checkExistence() {
      VStreamPath<Integer> stream = Path.vstreamOf(1, 2, 3, 4, 5);

      // TODO: Replace answerRequired() with stream.exists(n -> n > 4).unsafeRun()
      Boolean found = answerRequired();

      assertThat(found).isTrue();
    }

    /**
     * Exercise 11: Zip two streams
     *
     * <p>zipWith() pairs elements from two streams positionally, stopping at the shortest.
     *
     * <p>Task: Zip numbers with labels using (n, s) -> n + s
     */
    @Test
    @DisplayName("Exercise 11: Zip two streams")
    void exercise11_zipTwoStreams() {
      VStreamPath<Integer> nums = Path.vstreamOf(1, 2, 3);
      VStreamPath<String> labels = Path.vstreamOf("a", "b", "c");

      // TODO: Replace answerRequired() with nums.zipWith(labels, (n, s) -> n + s)
      VStreamPath<String> zipped = answerRequired();

      assertThat(zipped.toList().unsafeRun()).containsExactly("1a", "2b", "3c");
    }

    /**
     * Exercise 12: Focus bridge with lens
     *
     * <p>focus(FocusPath) extracts a field from every element using a lens.
     *
     * <p>Task: Extract names from a stream of Person records
     */
    @Test
    @DisplayName("Exercise 12: Focus bridge with lens")
    void exercise12_focusBridge() {
      record Person(String name, int age) {}

      Lens<Person, String> nameLens = Lens.of(Person::name, (p, n) -> new Person(n, p.age));
      FocusPath<Person, String> nameFocus = FocusPath.of(nameLens);

      VStreamPath<Person> people = Path.vstreamOf(new Person("Alice", 30), new Person("Bob", 25));

      // TODO: Replace answerRequired() with people.focus(nameFocus)
      VStreamPath<String> names = answerRequired();

      assertThat(names.toList().unsafeRun()).containsExactly("Alice", "Bob");
    }
  }

  /**
   * Congratulations! You've completed the VStreamPath tutorial.
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to create VStreamPath instances via Path factory methods
   *   <li>Composing lazy pipelines with map, filter, flatMap, take
   *   <li>Terminal operations that bridge to VTaskPath (toList, fold, exists)
   *   <li>Zipping streams positionally with zipWith
   *   <li>Using the optics focus bridge to navigate into stream elements
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>VStreamPath operations are lazy; nothing runs until a terminal operation
   *   <li>Terminal operations return VTaskPath, bridging stream to single-value effect
   *   <li>VStreamPath implements Chainable, fitting into the Effect Path hierarchy
   * </ul>
   */
}
