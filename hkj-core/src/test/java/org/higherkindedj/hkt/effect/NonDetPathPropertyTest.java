// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for NonDetPath using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs. NonDetPath represents
 * non-deterministic computations with Cartesian product zipWith semantics (different from
 * ListPath's positional semantics).
 */
@Label("NonDetPath Property-Based Tests")
class NonDetPathPropertyTest {

  @Provide
  Arbitrary<NonDetPath<Integer>> nonDetPaths() {
    return Arbitraries.oneOf(
        // Empty
        Arbitraries.just(NonDetPath.empty()),
        // Single element
        Arbitraries.integers().between(-100, 100).map(NonDetPath::pure),
        // Multiple elements (keep small to avoid combinatorial explosion)
        Arbitraries.integers()
            .between(-10, 10)
            .list()
            .ofMinSize(1)
            .ofMaxSize(5)
            .map(NonDetPath::of));
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToStringFunctions() {
    return Arbitraries.of(
        i -> "value:" + i, i -> String.valueOf(i * 2), i -> "n" + i, Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToIntFunctions() {
    return Arbitraries.of(String::length, String::hashCode, s -> s.isEmpty() ? 0 : 1);
  }

  @Provide
  Arbitrary<Function<Integer, NonDetPath<String>>> intToNonDetStringFunctions() {
    return Arbitraries.of(
        i -> NonDetPath.pure("value:" + i),
        i -> NonDetPath.of(List.of("a:" + i, "b:" + i)),
        i -> i % 2 == 0 ? NonDetPath.of(List.of("even")) : NonDetPath.empty());
  }

  @Provide
  Arbitrary<Function<String, NonDetPath<String>>> stringToNonDetStringFunctions() {
    return Arbitraries.of(
        s -> NonDetPath.pure(s.toUpperCase()),
        s -> NonDetPath.of(List.of(s + "!", s + "?")),
        s -> s.isEmpty() ? NonDetPath.empty() : NonDetPath.pure("non-empty:" + s));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("nonDetPaths") NonDetPath<Integer> path) {
    NonDetPath<Integer> result = path.map(Function.identity());
    assertThat(result.run()).isEqualTo(path.run());
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("nonDetPaths") NonDetPath<Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    NonDetPath<Integer> leftSide = path.map(f).map(g);
    NonDetPath<Integer> rightSide = path.map(f.andThen(g));

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: NonDetPath.pure(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToNonDetStringFunctions") Function<Integer, NonDetPath<String>> f) {

    NonDetPath<String> leftSide = NonDetPath.pure(value).via(f);
    NonDetPath<String> rightSide = f.apply(value);

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  @Property
  @Label("Monad Right Identity Law: path.via(NonDetPath::pure) == path")
  void rightIdentityLaw(@ForAll("nonDetPaths") NonDetPath<Integer> path) {
    NonDetPath<Integer> result = path.via(NonDetPath::pure);
    assertThat(result.run()).isEqualTo(path.run());
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("nonDetPaths") NonDetPath<Integer> path,
      @ForAll("intToNonDetStringFunctions") Function<Integer, NonDetPath<String>> f,
      @ForAll("stringToNonDetStringFunctions") Function<String, NonDetPath<String>> g) {

    NonDetPath<String> leftSide = path.via(f).via(g);
    NonDetPath<String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  // ===== Derived Properties =====

  @Property
  @Label("empty returns empty list")
  void emptyReturnsEmptyList() {
    NonDetPath<Integer> path = NonDetPath.empty();
    assertThat(path.run()).isEmpty();
  }

  @Property
  @Label("pure creates single-element list")
  void pureCreatesSingleElementList(@ForAll @IntRange(min = -100, max = 100) int value) {
    NonDetPath<Integer> path = NonDetPath.pure(value);
    assertThat(path.run()).containsExactly(value);
  }

  @Property
  @Label("map over empty returns empty")
  void mapOverEmptyReturnsEmpty(@ForAll("intToStringFunctions") Function<Integer, String> f) {
    NonDetPath<Integer> empty = NonDetPath.empty();
    NonDetPath<String> result = empty.map(f);
    assertThat(result.run()).isEmpty();
  }

  @Property
  @Label("via over empty returns empty")
  void viaOverEmptyReturnsEmpty(
      @ForAll("intToNonDetStringFunctions") Function<Integer, NonDetPath<String>> f) {
    NonDetPath<Integer> empty = NonDetPath.empty();
    NonDetPath<String> result = empty.via(f);
    assertThat(result.run()).isEmpty();
  }

  @Property
  @Label("zipWith uses Cartesian product semantics")
  void zipWithUsesCartesianProductSemantics() {
    NonDetPath<Integer> a = NonDetPath.of(List.of(1, 2));
    NonDetPath<String> b = NonDetPath.of(List.of("a", "b"));

    NonDetPath<String> result = a.zipWith(b, (i, s) -> i + s);

    // Cartesian product: all combinations
    assertThat(result.run()).containsExactly("1a", "1b", "2a", "2b");
  }

  @Property
  @Label("zipWith with empty produces empty")
  void zipWithEmptyProducesEmpty() {
    NonDetPath<Integer> a = NonDetPath.of(List.of(1, 2, 3));
    NonDetPath<String> b = NonDetPath.empty();

    NonDetPath<String> result = a.zipWith(b, (i, s) -> i + s);

    assertThat(result.run()).isEmpty();
  }

  @Property
  @Label("Cartesian product size is multiplication of input sizes")
  void cartesianProductSizeIsMultiplication() {
    NonDetPath<Integer> a = NonDetPath.of(List.of(1, 2, 3));
    NonDetPath<String> b = NonDetPath.of(List.of("a", "b"));

    NonDetPath<String> result = a.zipWith(b, (i, s) -> i + s);

    assertThat(result.run()).hasSize(3 * 2);
  }

  @Property
  @Label("filter removes non-matching elements")
  void filterRemovesNonMatching() {
    NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3, 4, 5));
    NonDetPath<Integer> result = path.filter(i -> i % 2 == 0);

    assertThat(result.run()).containsExactly(2, 4);
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("nonDetPaths") NonDetPath<Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    NonDetPath<Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    NonDetPath<Integer> composed = path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(stepByStep.run()).isEqualTo(composed.run());
  }

  @Property
  @Label("headOption returns first element or empty")
  void headOptionReturnsFirstOrEmpty(@ForAll("nonDetPaths") NonDetPath<Integer> path) {
    var head = path.headOption();
    var list = path.run();

    if (list.isEmpty()) {
      assertThat(head.isEmpty()).isTrue();
    } else {
      assertThat(head.isPresent()).isTrue();
      assertThat(head.orElse(-999)).isEqualTo(list.get(0));
    }
  }

  @Property
  @Label("NonDetPath differs from ListPath in zipWith semantics")
  void nonDetPathDiffersFromListPathInZipWith() {
    // Same inputs
    List<Integer> ints = List.of(1, 2);
    List<String> strs = List.of("a", "b");

    NonDetPath<Integer> nonDetA = NonDetPath.of(ints);
    NonDetPath<String> nonDetB = NonDetPath.of(strs);
    NonDetPath<String> nonDetResult = nonDetA.zipWith(nonDetB, (i, s) -> i + s);

    ListPath<Integer> listA = ListPath.of(ints);
    ListPath<String> listB = ListPath.of(strs);
    ListPath<String> listResult = listA.zipWith(listB, (i, s) -> i + s);

    // NonDetPath: Cartesian product (4 elements)
    assertThat(nonDetResult.run()).hasSize(4);
    assertThat(nonDetResult.run()).containsExactly("1a", "1b", "2a", "2b");

    // ListPath: Positional (2 elements)
    assertThat(listResult.run()).hasSize(2);
    assertThat(listResult.run()).containsExactly("1a", "2b");
  }
}
