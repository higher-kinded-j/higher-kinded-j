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
 * Solution for TutorialVStreamPath — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
@DisplayName("Tutorial Solution: VStreamPath")
public class TutorialVStreamPath_Solution {

  // ===========================================================================
  // Part 1: Creating VStreamPath Instances
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating VStreamPath Instances")
  class CreatingInstances {

    /**
     * Why this is idiomatic: {@code Path.vstreamOf(...)} is the canonical varargs constructor for a
     * {@code VStreamPath}. Composes with the rest of the path vocabulary ({@code map}, {@code
     * filter}, {@code flatMap}).
     *
     * <p>Alternative: {@code VStream.of(...).toPath()} when the underlying stream is needed first.
     * Same answer; the path-first form is shorter when the source is literal values.
     *
     * <p>Common wrong attempt: forget {@code unsafeRun} on the {@code toList} result. VStreamPath
     * stays lazy until the run.
     */
    @Test
    @DisplayName("Exercise 1: Create from varargs")
    void exercise1_createFromVarargs() {
      // SOLUTION: Use Path.vstreamOf() to create from varargs
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      assertThat(path.toList().unsafeRun()).containsExactly(1, 2, 3);
    }

    /**
     * Why this is idiomatic: {@code Path.vstreamRange(start, end)} produces a half-open integer
     * range as a path. Standard upper-exclusive convention.
     *
     * <p>Alternative: {@code IntStream.range} bridged to a path. The path-first form keeps the
     * chain inside the path API.
     *
     * <p>Common wrong attempt: assume the upper bound is inclusive. The convention matches {@code
     * IntStream.range}.
     */
    @Test
    @DisplayName("Exercise 2: Create from range")
    void exercise2_createFromRange() {
      // SOLUTION: Use Path.vstreamRange() to create an integer range
      VStreamPath<Integer> path = Path.vstreamRange(1, 6);

      assertThat(path.toList().unsafeRun()).containsExactly(1, 2, 3, 4, 5);
    }

    /**
     * Why this is idiomatic: {@code vstreamIterate(seed, fn).take(n)} pairs an infinite generator
     * with a bound. The pair stays in the path API for further composition.
     *
     * <p>Alternative: a manual loop appending to a list. Works once; the path version composes.
     *
     * <p>Common wrong attempt: omit {@code take}. The unbounded iterate hangs the test; always pair
     * iterate with a limit.
     */
    @Test
    @DisplayName("Exercise 3: Infinite stream with take")
    void exercise3_infiniteStreamWithTake() {
      // SOLUTION: Use Path.vstreamIterate() for an infinite stream and take(4)
      VStreamPath<Integer> path = Path.vstreamIterate(10, n -> n + 10).take(4);

      assertThat(path.toList().unsafeRun()).containsExactly(10, 20, 30, 40);
    }

    /**
     * Why this is idiomatic: {@code vstreamUnfold(seed, stateFn)} drives a path from a stateful
     * generator. Each step returns either a {@code Seed} or {@code empty} to terminate.
     *
     * <p>Alternative: {@code iterate} with a sentinel and {@code takeWhile}. Same answer for
     * monotonic sequences; {@code unfold} handles arbitrary termination directly.
     *
     * <p>Common wrong attempt: forget the termination branch. Without {@code Optional.empty} the
     * unfold runs forever.
     */
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

    /**
     * Why this is idiomatic: {@code path.map(fn)} transforms each element lazily, just like the
     * underlying VStream. The path stays a description.
     *
     * <p>Alternative: {@code stream.map} on the underlying VStream. Same answer; the path-level API
     * is for staying inside the path vocabulary.
     *
     * <p>Common wrong attempt: assume {@code map} runs eagerly. Path is lazy until {@code
     * unsafeRun}.
     */
    @Test
    @DisplayName("Exercise 5: Map elements")
    void exercise5_mapElements() {
      VStreamPath<String> names = Path.vstreamOf("alice", "bob");

      // SOLUTION: Use map to transform each element to uppercase
      VStreamPath<String> upper = names.map(String::toUpperCase);

      assertThat(upper.toList().unsafeRun()).containsExactly("ALICE", "BOB");
    }

    /**
     * Why this is idiomatic: {@code path.filter(predicate)} keeps elements matching the predicate.
     * Filtering composes with subsequent path stages.
     *
     * <p>Alternative: a manual stream consumer with an {@code if}. Same answer; the path filter
     * stays declarative.
     *
     * <p>Common wrong attempt: chain multiple filters when one composite predicate would do.
     * Functional, but verbose; combine with {@code &&} for single-pass predicates when sensible.
     */
    @Test
    @DisplayName("Exercise 6: Filter elements")
    void exercise6_filterElements() {
      VStreamPath<Integer> range = Path.vstreamRange(1, 11);

      // SOLUTION: Use filter to keep only even numbers
      VStreamPath<Integer> evens = range.filter(n -> n % 2 == 0);

      assertThat(evens.toList().unsafeRun()).containsExactly(2, 4, 6, 8, 10);
    }

    /**
     * Why this is idiomatic: {@code path.flatMap(fn)} expands each element into a sub-path; the
     * result concatenates the sub-paths. The function returns {@code VStreamPath}, not {@code
     * List}, to stay in the path API.
     *
     * <p>Alternative: nested loops returning a list. Equivalent; the {@code flatMap} keeps the lazy
     * pipeline.
     *
     * <p>Common wrong attempt: return a {@code List} from the lambda. The path needs another path;
     * lift with {@code Path.vstreamOf} when the data is a literal list.
     */
    @Test
    @DisplayName("Exercise 7: FlatMap to sub-streams")
    void exercise7_flatMapToSubStreams() {
      VStreamPath<Integer> nums = Path.vstreamOf(1, 2, 3);

      // SOLUTION: Use flatMap to expand each element into [n, n*10]
      VStreamPath<Integer> expanded = nums.flatMap(n -> Path.vstreamOf(n, n * 10));

      assertThat(expanded.toList().unsafeRun()).containsExactly(1, 10, 2, 20, 3, 30);
    }

    /**
     * Why this is idiomatic: chain {@code filter}, {@code map}, and {@code take} into one lazy
     * pipeline. The take limits work even though range goes to 100.
     *
     * <p>Alternative: pre-compute the bounded list and process it eagerly. Wastes work; the lazy
     * pipeline only does what is needed.
     *
     * <p>Common wrong attempt: collect to a list mid-pipeline. Each intermediate materialisation
     * costs memory; stay lazy.
     */
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

    /**
     * Why this is idiomatic: {@code stream.fold(seed, combiner)} reduces the path to a single
     * value, returning a {@code VTask}. Run with {@code unsafeRun} at the boundary.
     *
     * <p>Alternative: collect to list and sum. Works; the fold avoids the intermediate list.
     *
     * <p>Common wrong attempt: forget {@code unsafeRun}. The fold returns a task, not the value;
     * run it.
     */
    @Test
    @DisplayName("Exercise 9: Fold to sum")
    void exercise9_foldToSum() {
      VStreamPath<Integer> stream = Path.vstreamOf(1, 2, 3, 4, 5);

      // SOLUTION: Use fold with seed 0 and Integer::sum
      Integer sum = stream.fold(0, Integer::sum).unsafeRun();

      assertThat(sum).isEqualTo(15);
    }

    /**
     * Why this is idiomatic: {@code stream.exists(predicate)} short-circuits at the first match and
     * returns a {@code VTask<Boolean>}.
     *
     * <p>Alternative: collect and {@code anyMatch}. Same answer; {@code exists} stops as soon as it
     * finds a match — important for large or infinite paths.
     *
     * <p>Common wrong attempt: use {@code filter(p).toList().isEmpty()}. That traverses the entire
     * path; {@code exists} stops early.
     */
    @Test
    @DisplayName("Exercise 10: Check existence")
    void exercise10_checkExistence() {
      VStreamPath<Integer> stream = Path.vstreamOf(1, 2, 3, 4, 5);

      // SOLUTION: Use exists to check if any element is greater than 4
      Boolean found = stream.exists(n -> n > 4).unsafeRun();

      assertThat(found).isTrue();
    }

    /**
     * Why this is idiomatic: {@code zipWith(other, combiner)} pairs corresponding elements and
     * combines them. Stops at the shorter input — standard zip semantics.
     *
     * <p>Alternative: index both streams and rebuild manually. Same answer; {@code zipWith} keeps
     * the path lazy and is parallel-safe in structured-concurrency settings.
     *
     * <p>Common wrong attempt: assume the result has the longer length. Zip truncates; pad
     * explicitly if uniform length is required.
     */
    @Test
    @DisplayName("Exercise 11: Zip two streams")
    void exercise11_zipTwoStreams() {
      VStreamPath<Integer> nums = Path.vstreamOf(1, 2, 3);
      VStreamPath<String> labels = Path.vstreamOf("a", "b", "c");

      // SOLUTION: Use zipWith to pair elements positionally
      VStreamPath<String> zipped = nums.zipWith(labels, (n, s) -> n + s);

      assertThat(zipped.toList().unsafeRun()).containsExactly("1a", "2b", "3c");
    }

    /**
     * Why this is idiomatic: {@code path.focus(focusPath)} navigates each element through a lens.
     * The optic-driven extraction keeps the navigation declarative and survives schema changes.
     *
     * <p>Alternative: {@code path.map(person -> person.name())}. Same answer; the focus bridge
     * stays composable for deeper optic paths.
     *
     * <p>Common wrong attempt: read each element, then build a new path. The focus bridge wires the
     * optic into the path API directly — no manual rebuild needed.
     */
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
