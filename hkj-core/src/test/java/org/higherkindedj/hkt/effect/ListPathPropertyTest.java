// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for ListPath using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs. ListPath uses positional
 * zipWith semantics (stops at shortest list).
 */
@Label("ListPath Property-Based Tests")
class ListPathPropertyTest {

  @Provide
  Arbitrary<ListPath<Integer>> listPaths() {
    return Arbitraries.oneOf(
        // Empty list
        Arbitraries.just(ListPath.empty()),
        // Single element
        Arbitraries.integers().between(-100, 100).map(ListPath::pure),
        // Multiple elements
        Arbitraries.integers()
            .between(-100, 100)
            .list()
            .ofMinSize(1)
            .ofMaxSize(10)
            .map(ListPath::of));
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
  Arbitrary<Function<Integer, ListPath<String>>> intToListStringFunctions() {
    return Arbitraries.of(
        i -> ListPath.pure("value:" + i),
        i -> ListPath.of(List.of("a:" + i, "b:" + i)),
        i -> i % 2 == 0 ? ListPath.of(List.of("even")) : ListPath.empty());
  }

  @Provide
  Arbitrary<Function<String, ListPath<String>>> stringToListStringFunctions() {
    return Arbitraries.of(
        s -> ListPath.pure(s.toUpperCase()),
        s -> ListPath.of(List.of(s + "!", s + "?")),
        s -> s.isEmpty() ? ListPath.empty() : ListPath.pure("non-empty:" + s));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("listPaths") ListPath<Integer> path) {
    ListPath<Integer> result = path.map(Function.identity());
    assertThat(result.run()).isEqualTo(path.run());
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("listPaths") ListPath<Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    ListPath<Integer> leftSide = path.map(f).map(g);
    ListPath<Integer> rightSide = path.map(f.andThen(g));

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: ListPath.pure(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToListStringFunctions") Function<Integer, ListPath<String>> f) {

    ListPath<String> leftSide = ListPath.pure(value).via(f);
    ListPath<String> rightSide = f.apply(value);

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  @Property
  @Label("Monad Right Identity Law: path.via(ListPath::pure) == path")
  void rightIdentityLaw(@ForAll("listPaths") ListPath<Integer> path) {
    ListPath<Integer> result = path.via(ListPath::pure);
    assertThat(result.run()).isEqualTo(path.run());
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("listPaths") ListPath<Integer> path,
      @ForAll("intToListStringFunctions") Function<Integer, ListPath<String>> f,
      @ForAll("stringToListStringFunctions") Function<String, ListPath<String>> g) {

    ListPath<String> leftSide = path.via(f).via(g);
    ListPath<String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  // ===== Derived Properties =====

  @Property
  @Label("empty returns empty list")
  void emptyReturnsEmptyList() {
    ListPath<Integer> path = ListPath.empty();
    assertThat(path.run()).isEmpty();
  }

  @Property
  @Label("pure creates single-element list")
  void pureCreatesSingleElementList(@ForAll @IntRange(min = -100, max = 100) int value) {
    ListPath<Integer> path = ListPath.pure(value);
    assertThat(path.run()).containsExactly(value);
  }

  @Property
  @Label("map over empty returns empty")
  void mapOverEmptyReturnsEmpty(@ForAll("intToStringFunctions") Function<Integer, String> f) {
    ListPath<Integer> empty = ListPath.empty();
    ListPath<String> result = empty.map(f);
    assertThat(result.run()).isEmpty();
  }

  @Property
  @Label("via over empty returns empty")
  void viaOverEmptyReturnsEmpty(
      @ForAll("intToListStringFunctions") Function<Integer, ListPath<String>> f) {
    ListPath<Integer> empty = ListPath.empty();
    ListPath<String> result = empty.via(f);
    assertThat(result.run()).isEmpty();
  }

  @Property
  @Label("zipWith uses positional semantics (stops at shortest)")
  void zipWithUsesPositionalSemantics() {
    ListPath<Integer> a = ListPath.of(List.of(1, 2, 3));
    ListPath<String> b = ListPath.of(List.of("a", "b"));

    ListPath<String> result = a.zipWith(b, (i, s) -> i + s);

    assertThat(result.run()).containsExactly("1a", "2b");
  }

  @Property
  @Label("filter removes non-matching elements")
  void filterRemovesNonMatching() {
    ListPath<Integer> path = ListPath.of(List.of(1, 2, 3, 4, 5));
    ListPath<Integer> result = path.filter(i -> i % 2 == 0);

    assertThat(result.run()).containsExactly(2, 4);
  }

  @Property
  @Label("take limits list size")
  void takeLimitsSize() {
    ListPath<Integer> path = ListPath.of(List.of(1, 2, 3, 4, 5));
    ListPath<Integer> result = path.take(3);

    assertThat(result.run()).containsExactly(1, 2, 3);
  }

  @Property
  @Label("drop skips elements")
  void dropSkipsElements() {
    ListPath<Integer> path = ListPath.of(List.of(1, 2, 3, 4, 5));
    ListPath<Integer> result = path.drop(2);

    assertThat(result.run()).containsExactly(3, 4, 5);
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("listPaths") ListPath<Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    ListPath<Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    ListPath<Integer> composed = path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(stepByStep.run()).isEqualTo(composed.run());
  }

  @Property
  @Label("headOption returns first element or empty")
  void headOptionReturnsFirstOrEmpty(@ForAll("listPaths") ListPath<Integer> path) {
    var head = path.headOption();
    var list = path.run();

    if (list.isEmpty()) {
      assertThat(head.isEmpty()).isTrue();
    } else {
      assertThat(head.isPresent()).isTrue();
      assertThat(head.orElse(-999)).isEqualTo(list.get(0));
    }
  }
}
