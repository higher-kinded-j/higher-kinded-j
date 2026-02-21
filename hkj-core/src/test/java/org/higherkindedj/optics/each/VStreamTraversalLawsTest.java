// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.each;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Verifies that the VStream Traversal satisfies the core traversal laws.
 *
 * <p>The laws verified are:
 *
 * <ul>
 *   <li><b>Identity:</b> Modifying with the identity function returns the same structure
 *   <li><b>Composition:</b> Modifying with f then g equals modifying with g.f
 *   <li><b>Structure preservation:</b> getAll(set(v, s)) returns list of v with same length as
 *       getAll(s)
 * </ul>
 *
 * <p>VStream equality is determined by materialising both streams and comparing the resulting
 * lists.
 */
@DisplayName("VStream Traversal Laws")
class VStreamTraversalLawsTest {

  private final Traversal<VStream<Integer>, Integer> traversal = VStreamTraversals.forVStream();

  /** Helper: materialise a VStream to a list for comparison. */
  private <A> List<A> toList(VStream<A> stream) {
    return stream.toList().run();
  }

  // ===== Test Data =====

  private static VStream<Integer> emptyStream() {
    return VStream.empty();
  }

  private static VStream<Integer> singleStream() {
    return VStream.of(42);
  }

  private static VStream<Integer> multiStream() {
    return VStream.fromList(List.of(1, 2, 3, 4, 5));
  }

  private static VStream<Integer> duplicateStream() {
    return VStream.fromList(List.of(1, 2, 1, 3, 2));
  }

  // ===== Identity Law =====

  @Nested
  @DisplayName("Identity Law: modify(id, s) == s")
  class IdentityLawTests {

    @TestFactory
    @DisplayName("Identity law holds for various streams")
    Stream<DynamicTest> identityLawHolds() {
      record TestCase(String name, VStream<Integer> stream) {}

      return Stream.of(
              new TestCase("empty stream", emptyStream()),
              new TestCase("single element", singleStream()),
              new TestCase("multiple elements", multiStream()),
              new TestCase("duplicates", duplicateStream()))
          .map(
              tc ->
                  DynamicTest.dynamicTest(
                      "Identity law for " + tc.name(),
                      () -> {
                        VStream<Integer> original = tc.stream();
                        VStream<Integer> result =
                            Traversals.modify(traversal, Function.identity(), original);

                        assertThat(toList(result)).isEqualTo(toList(original));
                      }));
    }
  }

  // ===== Composition Law =====

  @Nested
  @DisplayName("Composition Law: modify(g, modify(f, s)) == modify(g.f, s)")
  class CompositionLawTests {

    private final Function<Integer, Integer> f = x -> x + 10;
    private final Function<Integer, Integer> g = x -> x * 2;

    @TestFactory
    @DisplayName("Composition law holds for various streams")
    Stream<DynamicTest> compositionLawHolds() {
      record TestCase(String name, VStream<Integer> stream) {}

      return Stream.of(
              new TestCase("empty stream", emptyStream()),
              new TestCase("single element", singleStream()),
              new TestCase("multiple elements", multiStream()),
              new TestCase("duplicates", duplicateStream()))
          .map(
              tc ->
                  DynamicTest.dynamicTest(
                      "Composition law for " + tc.name(),
                      () -> {
                        VStream<Integer> original = tc.stream();

                        // Apply f then g
                        VStream<Integer> step1 = Traversals.modify(traversal, f, original);
                        VStream<Integer> twoSteps = Traversals.modify(traversal, g, step1);

                        // Apply g.f in one step
                        VStream<Integer> composed =
                            Traversals.modify(traversal, f.andThen(g), original);

                        assertThat(toList(twoSteps)).isEqualTo(toList(composed));
                      }));
    }
  }

  // ===== Structure Preservation Law =====

  @Nested
  @DisplayName("Structure Preservation: getAll(set(v, s)).size() == getAll(s).size()")
  class StructurePreservationTests {

    @TestFactory
    @DisplayName("Structure preservation holds for various streams")
    Stream<DynamicTest> structurePreservationHolds() {
      record TestCase(String name, VStream<Integer> stream) {}

      return Stream.of(
              new TestCase("empty stream", emptyStream()),
              new TestCase("single element", singleStream()),
              new TestCase("multiple elements", multiStream()),
              new TestCase("duplicates", duplicateStream()))
          .map(
              tc ->
                  DynamicTest.dynamicTest(
                      "Structure preservation for " + tc.name(),
                      () -> {
                        VStream<Integer> original = tc.stream();
                        int originalSize = Traversals.getAll(traversal, original).size();

                        // Set all to a constant value
                        VStream<Integer> replaced =
                            Traversals.modify(traversal, ignored -> 99, original);
                        int replacedSize = Traversals.getAll(traversal, replaced).size();

                        assertThat(replacedSize).isEqualTo(originalSize);
                      }));
    }

    @Test
    @DisplayName("All elements after set have the same value")
    void setAllProducesUniformValues() {
      VStream<Integer> original = multiStream();

      VStream<Integer> replaced = Traversals.modify(traversal, ignored -> 99, original);
      List<Integer> result = Traversals.getAll(traversal, replaced);

      assertThat(result).containsOnly(99);
      assertThat(result).hasSize(5);
    }
  }

  // ===== GetAll / Modify Consistency =====

  @Nested
  @DisplayName("GetAll / Modify Consistency")
  class GetAllModifyConsistencyTests {

    @Test
    @DisplayName("getAll retrieves elements that modify can transform")
    void getAllAndModifyConsistent() {
      VStream<Integer> original = multiStream();

      // getAll gives us the elements
      List<Integer> elements = Traversals.getAll(traversal, original);

      // modify transforms each one
      VStream<Integer> doubled = Traversals.modify(traversal, x -> x * 2, original);
      List<Integer> doubledElements = Traversals.getAll(traversal, doubled);

      // Each element should be doubled
      assertThat(doubledElements).hasSize(elements.size()).containsExactly(2, 4, 6, 8, 10);
    }

    @Test
    @DisplayName("modify with constant function produces uniform getAll")
    void modifyConstantProducesUniform() {
      VStream<Integer> original = VStream.fromList(List.of(1, 2, 3));

      VStream<Integer> replaced = Traversals.modify(traversal, ignored -> 0, original);
      List<Integer> result = Traversals.getAll(traversal, replaced);

      assertThat(result).containsExactly(0, 0, 0);
    }
  }
}
