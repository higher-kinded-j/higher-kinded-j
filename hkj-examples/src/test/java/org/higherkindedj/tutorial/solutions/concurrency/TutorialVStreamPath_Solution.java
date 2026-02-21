// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial: VStreamPath - Fluent Streaming with Effect Path API
 *
 * <p>Each exercise solution replaces the answerRequired() placeholder with the correct code.
 */
@DisplayName("Tutorial Solution: VStreamPath")
public class TutorialVStreamPath_Solution {

  // ===========================================================================
  // Part 1: Creating VStreamPath Instances
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating VStreamPath Instances")
  class CreatingInstances {

    @Test
    @DisplayName("Exercise 1: Create from varargs")
    void exercise1_createFromVarargs() {
      // SOLUTION: Use Path.vstreamOf() to create from varargs
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      assertThat(path.toList().unsafeRun()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("Exercise 2: Create from range")
    void exercise2_createFromRange() {
      // SOLUTION: Use Path.vstreamRange() to create an integer range
      VStreamPath<Integer> path = Path.vstreamRange(1, 6);

      assertThat(path.toList().unsafeRun()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("Exercise 3: Infinite stream with take")
    void exercise3_infiniteStreamWithTake() {
      // SOLUTION: Use Path.vstreamIterate() for an infinite stream and take(4)
      VStreamPath<Integer> path = Path.vstreamIterate(10, n -> n + 10).take(4);

      assertThat(path.toList().unsafeRun()).containsExactly(10, 20, 30, 40);
    }

    @Test
    @DisplayName("Exercise 4: Effectful unfold")
    void exercise4_effectfulUnfold() {
      // SOLUTION: Use Path.vstreamUnfold() with a function that stops when state > 3
      VStreamPath<Integer> path =
          Path.vstreamUnfold(
              1,
              state ->
                  VTask.succeed(
                      state > 3
                          ? Optional.empty()
                          : Optional.of(new VStream.Seed<>(state, state + 1))));

      assertThat(path.toList().unsafeRun()).containsExactly(1, 2, 3);
    }
  }

  // ===========================================================================
  // Part 2: Composing Operations
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Composing Operations")
  class ComposingOperations {

    @Test
    @DisplayName("Exercise 5: Map elements")
    void exercise5_mapElements() {
      VStreamPath<String> names = Path.vstreamOf("alice", "bob");

      // SOLUTION: Use map to transform each element to uppercase
      VStreamPath<String> upper = names.map(String::toUpperCase);

      assertThat(upper.toList().unsafeRun()).containsExactly("ALICE", "BOB");
    }

    @Test
    @DisplayName("Exercise 6: Filter elements")
    void exercise6_filterElements() {
      VStreamPath<Integer> range = Path.vstreamRange(1, 11);

      // SOLUTION: Use filter to keep only even numbers
      VStreamPath<Integer> evens = range.filter(n -> n % 2 == 0);

      assertThat(evens.toList().unsafeRun()).containsExactly(2, 4, 6, 8, 10);
    }

    @Test
    @DisplayName("Exercise 7: FlatMap to sub-streams")
    void exercise7_flatMapToSubStreams() {
      VStreamPath<Integer> nums = Path.vstreamOf(1, 2, 3);

      // SOLUTION: Use flatMap to expand each element into [n, n*10]
      VStreamPath<Integer> expanded = nums.flatMap(n -> Path.vstreamOf(n, n * 10));

      assertThat(expanded.toList().unsafeRun()).containsExactly(1, 10, 2, 20, 3, 30);
    }

    @Test
    @DisplayName("Exercise 8: Compose a pipeline")
    void exercise8_composePipeline() {
      // SOLUTION: Chain filter, map, and take in a lazy pipeline
      VStreamPath<String> pipeline =
          Path.vstreamRange(1, 100).filter(n -> n % 2 == 0).map(n -> "Even:" + n).take(3);

      assertThat(pipeline.toList().unsafeRun()).containsExactly("Even:2", "Even:4", "Even:6");
    }
  }

  // ===========================================================================
  // Part 3: Terminal Operations and Zipping
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Terminal Operations and Zipping")
  class TerminalAndZipping {

    @Test
    @DisplayName("Exercise 9: Fold to sum")
    void exercise9_foldToSum() {
      VStreamPath<Integer> stream = Path.vstreamOf(1, 2, 3, 4, 5);

      // SOLUTION: Use fold with seed 0 and Integer::sum
      Integer sum = stream.fold(0, Integer::sum).unsafeRun();

      assertThat(sum).isEqualTo(15);
    }

    @Test
    @DisplayName("Exercise 10: Check existence")
    void exercise10_checkExistence() {
      VStreamPath<Integer> stream = Path.vstreamOf(1, 2, 3, 4, 5);

      // SOLUTION: Use exists to check if any element is greater than 4
      Boolean found = stream.exists(n -> n > 4).unsafeRun();

      assertThat(found).isTrue();
    }

    @Test
    @DisplayName("Exercise 11: Zip two streams")
    void exercise11_zipTwoStreams() {
      VStreamPath<Integer> nums = Path.vstreamOf(1, 2, 3);
      VStreamPath<String> labels = Path.vstreamOf("a", "b", "c");

      // SOLUTION: Use zipWith to pair elements positionally
      VStreamPath<String> zipped = nums.zipWith(labels, (n, s) -> n + s);

      assertThat(zipped.toList().unsafeRun()).containsExactly("1a", "2b", "3c");
    }

    @Test
    @DisplayName("Exercise 12: Focus bridge with lens")
    void exercise12_focusBridge() {
      record Person(String name, int age) {}

      Lens<Person, String> nameLens = Lens.of(Person::name, (p, n) -> new Person(n, p.age));
      FocusPath<Person, String> nameFocus = FocusPath.of(nameLens);

      VStreamPath<Person> people = Path.vstreamOf(new Person("Alice", 30), new Person("Bob", 25));

      // SOLUTION: Use focus() to extract names from each Person
      VStreamPath<String> names = people.focus(nameFocus);

      assertThat(names.toList().unsafeRun()).containsExactly("Alice", "Bob");
    }
  }
}
