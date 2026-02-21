// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for VStreamPath using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs. VStreamPath represents
 * lazy, pull-based streams that execute on virtual threads.
 */
@Label("VStreamPath Property-Based Tests")
class VStreamPathPropertyTest {

  private static List<Integer> materialise(VStreamPath<Integer> path) {
    return path.toList().unsafeRun();
  }

  private static List<String> materialiseStr(VStreamPath<String> path) {
    return path.toList().unsafeRun();
  }

  @Provide
  Arbitrary<VStreamPath<Integer>> vstreamPaths() {
    return Arbitraries.oneOf(
        // Empty stream
        Arbitraries.just(Path.vstreamEmpty()),
        // Single element
        Arbitraries.integers().between(-100, 100).map(Path::vstreamPure),
        // From list
        Arbitraries.integers()
            .between(-100, 100)
            .list()
            .ofMinSize(1)
            .ofMaxSize(10)
            .map(Path::vstreamFromList));
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
  Arbitrary<Function<Integer, VStreamPath<String>>> intToVStreamStringFunctions() {
    return Arbitraries.of(
        i -> Path.vstreamPure("value:" + i),
        i -> Path.vstreamOf("a:" + i, "b:" + i),
        i -> i % 2 == 0 ? Path.vstreamOf("even") : Path.vstreamEmpty());
  }

  @Provide
  Arbitrary<Function<String, VStreamPath<String>>> stringToVStreamStringFunctions() {
    return Arbitraries.of(
        s -> Path.vstreamPure(s.toUpperCase()),
        s -> Path.vstreamOf(s + "!", s + "?"),
        s -> s.isEmpty() ? Path.vstreamEmpty() : Path.vstreamPure("non-empty:" + s));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("vstreamPaths") VStreamPath<Integer> path) {
    VStreamPath<Integer> result = path.map(Function.identity());
    assertThat(materialise(result)).isEqualTo(materialise(path));
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("vstreamPaths") VStreamPath<Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    VStreamPath<Integer> leftSide = path.map(f).map(g);
    VStreamPath<Integer> rightSide = path.map(f.andThen(g));

    assertThat(materialise(leftSide)).isEqualTo(materialise(rightSide));
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: pure(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToVStreamStringFunctions") Function<Integer, VStreamPath<String>> f) {

    VStreamPath<String> leftSide = Path.vstreamPure(value).via(f);
    VStreamPath<String> rightSide = f.apply(value);

    assertThat(materialiseStr(leftSide)).isEqualTo(materialiseStr(rightSide));
  }

  @Property
  @Label("Monad Right Identity Law: path.via(pure) == path")
  void rightIdentityLaw(@ForAll("vstreamPaths") VStreamPath<Integer> path) {
    VStreamPath<Integer> result = path.via(x -> Path.vstreamPure(x));
    assertThat(materialise(result)).isEqualTo(materialise(path));
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("vstreamPaths") VStreamPath<Integer> path,
      @ForAll("intToVStreamStringFunctions") Function<Integer, VStreamPath<String>> f,
      @ForAll("stringToVStreamStringFunctions") Function<String, VStreamPath<String>> g) {

    VStreamPath<String> leftSide = path.via(f).via(g);
    VStreamPath<String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(materialiseStr(leftSide)).isEqualTo(materialiseStr(rightSide));
  }

  // ===== Derived Properties =====

  @Property
  @Label("empty returns empty stream")
  void emptyReturnsEmptyStream() {
    VStreamPath<Integer> path = Path.vstreamEmpty();
    assertThat(materialise(path)).isEmpty();
    assertThat(path.count().unsafeRun()).isEqualTo(0L);
  }

  @Property
  @Label("pure creates single-element stream")
  void pureCreatesSingleElementStream(@ForAll @IntRange(min = -100, max = 100) int value) {
    VStreamPath<Integer> path = Path.vstreamPure(value);
    assertThat(materialise(path)).containsExactly(value);
    assertThat(path.count().unsafeRun()).isEqualTo(1L);
  }

  @Property
  @Label("map over empty returns empty")
  void mapOverEmptyReturnsEmpty(@ForAll("intToStringFunctions") Function<Integer, String> f) {
    VStreamPath<Integer> empty = Path.vstreamEmpty();
    VStreamPath<String> result = empty.map(f);
    assertThat(materialiseStr(result)).isEmpty();
  }

  @Property
  @Label("via over empty returns empty")
  void viaOverEmptyReturnsEmpty(
      @ForAll("intToVStreamStringFunctions") Function<Integer, VStreamPath<String>> f) {
    VStreamPath<Integer> empty = Path.vstreamEmpty();
    VStreamPath<String> result = empty.via(f);
    assertThat(materialiseStr(result)).isEmpty();
  }

  @Property
  @Label("filter removes non-matching elements")
  void filterRemovesNonMatching() {
    VStreamPath<Integer> path = Path.vstreamFromList(List.of(1, 2, 3, 4, 5));
    VStreamPath<Integer> result = path.filter(i -> i % 2 == 0);
    assertThat(materialise(result)).containsExactly(2, 4);
  }

  @Property
  @Label("take limits stream size")
  void takeLimitsSize() {
    VStreamPath<Integer> path = Path.vstreamFromList(List.of(1, 2, 3, 4, 5));
    VStreamPath<Integer> result = path.take(3);
    assertThat(materialise(result)).containsExactly(1, 2, 3);
  }

  @Property
  @Label("zipWith pairs elements positionally")
  void zipWithPairsElements() {
    VStreamPath<Integer> a = Path.vstreamOf(1, 2, 3);
    VStreamPath<String> b = Path.vstreamOf("a", "b", "c");

    VStreamPath<String> result = a.zipWith(b, (i, s) -> i + s);

    // VStreamPath uses positional zipping (shortest length)
    assertThat(materialiseStr(result)).containsExactly("1a", "2b", "3c");
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("vstreamPaths") VStreamPath<Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    VStreamPath<Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    VStreamPath<Integer> composed = path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(materialise(stepByStep)).isEqualTo(materialise(composed));
  }

  @Property
  @Label("headOption returns first element or empty")
  void headOptionReturnsFirstOrEmpty(@ForAll("vstreamPaths") VStreamPath<Integer> path) {
    var head = path.headOption().unsafeRun();
    var list = materialise(path);

    if (list.isEmpty()) {
      assertThat(head.isEmpty()).isTrue();
    } else {
      assertThat(head.isPresent()).isTrue();
      assertThat(head.orElse(-999)).isEqualTo(list.get(0));
    }
  }

  @Property
  @Label("count matches list size")
  void countMatchesListSize(@ForAll("vstreamPaths") VStreamPath<Integer> path) {
    long count = path.count().unsafeRun();
    int listSize = materialise(path).size();

    assertThat(count).isEqualTo(listSize);
  }

  @Property
  @Label("concat preserves all elements")
  void concatPreservesElements(
      @ForAll("vstreamPaths") VStreamPath<Integer> a,
      @ForAll("vstreamPaths") VStreamPath<Integer> b) {
    List<Integer> listA = materialise(a);
    List<Integer> listB = materialise(b);

    VStreamPath<Integer> concatenated = a.concat(b);
    List<Integer> resultList = materialise(concatenated);

    assertThat(resultList.size()).isEqualTo(listA.size() + listB.size());
    assertThat(resultList.subList(0, listA.size())).isEqualTo(listA);
    assertThat(resultList.subList(listA.size(), resultList.size())).isEqualTo(listB);
  }
}
