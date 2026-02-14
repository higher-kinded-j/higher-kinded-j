// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for StreamPath using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs. StreamPath represents lazy
 * stream sequences with reusable stream suppliers.
 */
@Label("StreamPath Property-Based Tests")
class StreamPathPropertyTest {

  @Provide
  Arbitrary<StreamPath<Integer>> streamPaths() {
    return Arbitraries.oneOf(
        // Empty stream
        Arbitraries.just(StreamPath.empty()),
        // Single element
        Arbitraries.integers().between(-100, 100).map(StreamPath::pure),
        // From list
        Arbitraries.integers()
            .between(-100, 100)
            .list()
            .ofMinSize(1)
            .ofMaxSize(10)
            .map(StreamPath::fromList));
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
  Arbitrary<Function<Integer, StreamPath<String>>> intToStreamStringFunctions() {
    return Arbitraries.of(
        i -> StreamPath.pure("value:" + i),
        i -> StreamPath.fromList(List.of("a:" + i, "b:" + i)),
        i -> i % 2 == 0 ? StreamPath.fromList(List.of("even")) : StreamPath.empty());
  }

  @Provide
  Arbitrary<Function<String, StreamPath<String>>> stringToStreamStringFunctions() {
    return Arbitraries.of(
        s -> StreamPath.pure(s.toUpperCase()),
        s -> StreamPath.fromList(List.of(s + "!", s + "?")),
        s -> s.isEmpty() ? StreamPath.empty() : StreamPath.pure("non-empty:" + s));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("streamPaths") StreamPath<Integer> path) {
    StreamPath<Integer> result = path.map(Function.identity());
    assertThat(result.toList()).isEqualTo(path.toList());
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("streamPaths") StreamPath<Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    StreamPath<Integer> leftSide = path.map(f).map(g);
    StreamPath<Integer> rightSide = path.map(f.andThen(g));

    assertThat(leftSide.toList()).isEqualTo(rightSide.toList());
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: StreamPath.pure(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStreamStringFunctions") Function<Integer, StreamPath<String>> f) {

    StreamPath<String> leftSide = StreamPath.pure(value).via(f);
    StreamPath<String> rightSide = f.apply(value);

    assertThat(leftSide.toList()).isEqualTo(rightSide.toList());
  }

  @Property
  @Label("Monad Right Identity Law: path.via(StreamPath::pure) == path")
  void rightIdentityLaw(@ForAll("streamPaths") StreamPath<Integer> path) {
    StreamPath<Integer> result = path.via(StreamPath::pure);
    assertThat(result.toList()).isEqualTo(path.toList());
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("streamPaths") StreamPath<Integer> path,
      @ForAll("intToStreamStringFunctions") Function<Integer, StreamPath<String>> f,
      @ForAll("stringToStreamStringFunctions") Function<String, StreamPath<String>> g) {

    StreamPath<String> leftSide = path.via(f).via(g);
    StreamPath<String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(leftSide.toList()).isEqualTo(rightSide.toList());
  }

  // ===== Derived Properties =====

  @Property
  @Label("empty returns empty stream")
  void emptyReturnsEmptyStream() {
    StreamPath<Integer> path = StreamPath.empty();
    assertThat(path.toList()).isEmpty();
    assertThat(path.count()).isEqualTo(0);
  }

  @Property
  @Label("pure creates single-element stream")
  void pureCreatesSingleElementStream(@ForAll @IntRange(min = -100, max = 100) int value) {
    StreamPath<Integer> path = StreamPath.pure(value);
    assertThat(path.toList()).containsExactly(value);
    assertThat(path.count()).isEqualTo(1);
  }

  @Property
  @Label("map over empty returns empty")
  void mapOverEmptyReturnsEmpty(@ForAll("intToStringFunctions") Function<Integer, String> f) {

    StreamPath<Integer> empty = StreamPath.empty();
    StreamPath<String> result = empty.map(f);
    assertThat(result.toList()).isEmpty();
  }

  @Property
  @Label("via over empty returns empty")
  void viaOverEmptyReturnsEmpty(
      @ForAll("intToStreamStringFunctions") Function<Integer, StreamPath<String>> f) {

    StreamPath<Integer> empty = StreamPath.empty();
    StreamPath<String> result = empty.via(f);
    assertThat(result.toList()).isEmpty();
  }

  @Property
  @Label("stream can be consumed multiple times")
  void streamCanBeConsumedMultipleTimes(@ForAll @IntRange(min = -100, max = 100) int value) {
    StreamPath<Integer> path = StreamPath.fromList(List.of(value, value + 1, value + 2));

    // First consumption
    List<Integer> first = path.toList();

    // Second consumption should return same results
    List<Integer> second = path.toList();

    assertThat(first).isEqualTo(second);
  }

  @Property
  @Label("filter removes non-matching elements")
  void filterRemovesNonMatching() {
    StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3, 4, 5));
    StreamPath<Integer> result = path.filter(i -> i % 2 == 0);

    assertThat(result.toList()).containsExactly(2, 4);
  }

  @Property
  @Label("take limits stream size")
  void takeLimitsSize() {
    StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3, 4, 5));
    StreamPath<Integer> result = path.take(3);

    assertThat(result.toList()).containsExactly(1, 2, 3);
  }

  @Property
  @Label("zipWith combines streams with Cartesian product")
  void zipWithCombinesStreams() {
    StreamPath<Integer> a = StreamPath.fromList(List.of(1, 2));
    StreamPath<String> b = StreamPath.fromList(List.of("a", "b"));

    StreamPath<String> result = a.zipWith(b, (i, s) -> i + s);

    // StreamPath uses Cartesian product semantics (all combinations)
    assertThat(result.toList()).containsExactly("1a", "1b", "2a", "2b");
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("streamPaths") StreamPath<Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    StreamPath<Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    StreamPath<Integer> composed = path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(stepByStep.toList()).isEqualTo(composed.toList());
  }

  @Property
  @Label("headOption returns first element or empty")
  void headOptionReturnsFirstOrEmpty(@ForAll("streamPaths") StreamPath<Integer> path) {
    var head = path.headOption();
    var list = path.toList();

    if (list.isEmpty()) {
      assertThat(head.isEmpty()).isTrue();
    } else {
      assertThat(head.isPresent()).isTrue();
      assertThat(head.orElse(-999)).isEqualTo(list.get(0));
    }
  }
}
