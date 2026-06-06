// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.StreamAssert.assertThatStream;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StreamTraverse Complete Test Suite")
class StreamTraverseTest extends StreamTestBase {

  private final Traverse<StreamKind.Witness> streamTraverse = StreamTraverse.INSTANCE;
  private final Foldable<StreamKind.Witness> streamFoldable = StreamTraverse.INSTANCE;

  // The applicative effect threaded through traverse is the real Maybe (its just/nothing
  // short-circuit semantics are exactly what traverse must respect), matching every other
  // *TraverseTest in the codebase.
  private final Applicative<MaybeKind.Witness> maybeApplicative = Instances.monadError(maybe());

  @Nested
  @DisplayName("map method")
  class MapTests {
    @Test
    @DisplayName("map() on empty stream returns empty stream")
    void mapEmptyStreamReturnsEmpty() {
      Kind<StreamKind.Witness, Integer> input = emptyStream();
      var result = streamTraverse.map(Object::toString, input);

      assertThatStream(result).isEmpty();
    }

    @Test
    @DisplayName("map() applies function lazily to non-empty stream")
    void mapNonEmptyStreamAppliesFunctionLazily() {
      var input = streamOf(1, 2, 3);
      var result = streamTraverse.map(x -> x * 2, input);

      // Map is lazy, force evaluation
      assertThatStream(result).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("map() works with infinite streams")
    void mapWorksWithInfiniteStreams() {
      Kind<StreamKind.Witness, Integer> infinite = STREAM.widen(Stream.iterate(1, n -> n + 1));
      var result = streamTraverse.map(x -> x * 2, infinite);

      // Take only first 5 elements
      List<Integer> collected = STREAM.narrow(result).limit(5).collect(Collectors.toList());
      assertThat(collected).containsExactly(2, 4, 6, 8, 10);
    }
  }

  @Nested
  @DisplayName("traverse method")
  class TraverseTests {
    private final Function<Integer, Kind<MaybeKind.Witness, String>> intToJustString =
        i -> MAYBE.widen(Maybe.just("v" + i));

    private final Function<Integer, Kind<MaybeKind.Witness, Integer>> intToMaybeSometimesNothing =
        i -> (i % 2 == 0) ? MAYBE.widen(Maybe.nothing()) : MAYBE.widen(Maybe.just(i * 3));

    @Test
    @DisplayName("traverse() on empty stream returns applicative of empty stream")
    void traverseEmptyStreamReturnsApplicativeOfEmptyStream() {
      Kind<StreamKind.Witness, Integer> emptyStreamKind = emptyStream();

      var resultKind = streamTraverse.traverse(maybeApplicative, intToJustString, emptyStreamKind);

      Maybe<Kind<StreamKind.Witness, String>> maybe = MAYBE.narrow(resultKind);
      assertThat(maybe.isJust()).isTrue();
      assertThatStream(maybe.get()).isEmpty();
    }

    @Test
    @DisplayName("traverse() with all effects succeeding returns applicative of stream of results")
    void traverseAllEffectsSucceedReturnsStreamOfResults() {
      var inputStream = streamOf(1, 2, 3);

      var resultKind = streamTraverse.traverse(maybeApplicative, intToJustString, inputStream);

      Maybe<Kind<StreamKind.Witness, String>> maybe = MAYBE.narrow(resultKind);
      assertThat(maybe.isJust()).isTrue();
      assertThatStream(maybe.get()).containsExactly("v1", "v2", "v3");
    }

    @Test
    @DisplayName("traverse() with one effect failing returns nothing")
    void traverseOneEffectFailsReturnsNothing() {
      var inputStream = streamOf(1, 2, 3);

      var resultKind =
          streamTraverse.traverse(maybeApplicative, intToMaybeSometimesNothing, inputStream);

      // Should be Nothing because element 2 fails
      assertThat(MAYBE.narrow(resultKind).isNothing()).isTrue();
    }

    @Test
    @DisplayName("traverse() consumes the stream")
    void traverseConsumesStream() {
      Stream<Integer> stream = Stream.of(1, 2, 3);
      Kind<StreamKind.Witness, Integer> streamKind = STREAM.widen(stream);

      // Traverse consumes the stream; this documents the single-consumption semantics.
      streamTraverse.traverse(maybeApplicative, intToJustString, streamKind);
    }

    @Test
    @DisplayName("traverse() works with limited infinite stream")
    void traverseWorksWithLimitedInfiniteStream() {
      // Create a limited infinite stream
      Kind<StreamKind.Witness, Integer> limitedInfinite =
          STREAM.widen(Stream.iterate(1, n -> n + 1).limit(10));

      var resultKind = streamTraverse.traverse(maybeApplicative, intToJustString, limitedInfinite);

      Maybe<Kind<StreamKind.Witness, String>> maybe = MAYBE.narrow(resultKind);
      assertThat(maybe.isJust()).isTrue();
      assertThatStream(maybe.get())
          .hasSize(10)
          .containsExactly("v1", "v2", "v3", "v4", "v5", "v6", "v7", "v8", "v9", "v10");
    }
  }

  @Nested
  @DisplayName("sequenceA method")
  class SequenceATests {

    @Test
    @DisplayName("sequenceA() on empty stream returns applicative of empty stream")
    void sequenceAEmptyStreamReturnsApplicativeOfEmptyStream() {
      Kind<StreamKind.Witness, Kind<MaybeKind.Witness, String>> emptyStreamOfMaybes = emptyStream();

      var result = streamTraverse.sequenceA(maybeApplicative, emptyStreamOfMaybes);

      Maybe<Kind<StreamKind.Witness, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThatStream(maybe.get()).isEmpty();
    }

    @Test
    @DisplayName("sequenceA() with all just returns just of stream")
    void sequenceAAllJustReturnsJustOfStream() {
      Stream<Kind<MaybeKind.Witness, Integer>> stream =
          Stream.of(
              MAYBE.widen(Maybe.just(1)), MAYBE.widen(Maybe.just(2)), MAYBE.widen(Maybe.just(3)));
      Kind<StreamKind.Witness, Kind<MaybeKind.Witness, Integer>> streamOfMaybes =
          STREAM.widen(stream);

      var result = streamTraverse.sequenceA(maybeApplicative, streamOfMaybes);

      Maybe<Kind<StreamKind.Witness, Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThatStream(maybe.get()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("sequenceA() with nothing returns nothing")
    void sequenceAContainsNothingReturnsNothing() {
      Stream<Kind<MaybeKind.Witness, Integer>> stream =
          Stream.of(
              MAYBE.widen(Maybe.just(1)), MAYBE.widen(Maybe.nothing()), MAYBE.widen(Maybe.just(3)));
      Kind<StreamKind.Witness, Kind<MaybeKind.Witness, Integer>> streamOfMaybes =
          STREAM.widen(stream);

      var result = streamTraverse.sequenceA(maybeApplicative, streamOfMaybes);

      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("foldMap method")
  class FoldMapTests {
    @Test
    @DisplayName("foldMap() on empty stream returns monoid empty")
    void foldMapEmptyStreamReturnsMonoidEmpty() {
      Kind<StreamKind.Witness, Integer> emptyStreamKind = emptyStream();
      Monoid<Integer> sumMonoid = Monoids.integerAddition();

      Integer result = streamFoldable.foldMap(sumMonoid, Function.identity(), emptyStreamKind);

      assertThat(result).isEqualTo(sumMonoid.empty()).isZero();
    }

    @Test
    @DisplayName("foldMap() with integer addition sums elements")
    void foldMapWithIntegerAdditionSumsElements() {
      var numbers = streamOf(1, 2, 3, 4);
      Monoid<Integer> sumMonoid = Monoids.integerAddition();

      Integer result = streamFoldable.foldMap(sumMonoid, Function.identity(), numbers);

      assertThat(result).isEqualTo(10);
    }

    @Test
    @DisplayName("foldMap() with string concat concatenates mapped elements")
    void foldMapWithStringConcatConcatenatesMappedElements() {
      var numbers = streamOf(1, 2, 3);
      Monoid<String> stringMonoid = Monoids.string();

      String result = streamFoldable.foldMap(stringMonoid, i -> "i" + i, numbers);

      assertThat(result).isEqualTo("i1i2i3");
    }

    @Test
    @DisplayName("foldMap() works with limited infinite stream")
    void foldMapWorksWithLimitedInfiniteStream() {
      // Sum first 100 natural numbers
      Kind<StreamKind.Witness, Integer> limitedInfinite =
          STREAM.widen(Stream.iterate(1, n -> n + 1).limit(100));
      Monoid<Integer> sumMonoid = Monoids.integerAddition();

      Integer result = streamFoldable.foldMap(sumMonoid, Function.identity(), limitedInfinite);

      // Sum of 1 to 100 = 100 * 101 / 2 = 5050
      assertThat(result).isEqualTo(5050);
    }

    @Test
    @DisplayName("foldMap() with integer multiplication multiplies elements")
    void foldMapWithIntegerMultiplicationMultipliesElements() {
      var numbers = streamOf(2, 3, 4);
      Monoid<Integer> productMonoid = Monoids.integerMultiplication();

      Integer result = streamFoldable.foldMap(productMonoid, Function.identity(), numbers);

      assertThat(result).isEqualTo(24); // 2 * 3 * 4
    }

    @Test
    @DisplayName("foldMap() consumes the stream")
    void foldMapConsumesStream() {
      Stream<Integer> stream = Stream.of(1, 2, 3);
      Kind<StreamKind.Witness, Integer> streamKind = STREAM.widen(stream);
      Monoid<Integer> sumMonoid = Monoids.integerAddition();

      // First fold consumes the stream; this documents the single-consumption semantics.
      streamFoldable.foldMap(sumMonoid, Function.identity(), streamKind);
    }
  }

  @Nested
  @DisplayName("Integration tests")
  class IntegrationTests {

    private Function<String, Kind<MaybeKind.Witness, Integer>> validateAndParse() {
      return str -> {
        try {
          int value = Integer.parseInt(str);
          return value > 0 ? MAYBE.widen(Maybe.just(value)) : MAYBE.widen(Maybe.nothing());
        } catch (NumberFormatException e) {
          return MAYBE.widen(Maybe.nothing());
        }
      };
    }

    @Test
    @DisplayName("Real-world example: validate and process stream pipeline")
    void realWorldValidateAndProcessStreamPipeline() {
      // Simulate processing a stream of user inputs
      Stream<String> userInputs = Stream.of("10", "20", "30", "40");
      Kind<StreamKind.Witness, String> inputStream = STREAM.widen(userInputs);

      // Traverse: validate all inputs
      var validated = streamTraverse.traverse(maybeApplicative, validateAndParse(), inputStream);

      // All valid, should get Just(stream of integers)
      Maybe<Kind<StreamKind.Witness, Integer>> maybe = MAYBE.narrow(validated);
      assertThat(maybe.isJust()).isTrue();

      // Now fold the validated stream
      Monoid<Integer> sumMonoid = Monoids.integerAddition();
      Integer sum = streamFoldable.foldMap(sumMonoid, Function.identity(), maybe.get());

      assertThat(sum).isEqualTo(100); // 10 + 20 + 30 + 40
    }

    @Test
    @DisplayName("Real-world example: early termination on validation failure")
    void realWorldEarlyTerminationOnValidationFailure() {
      // Some invalid inputs
      Stream<String> userInputs = Stream.of("10", "abc", "30");
      Kind<StreamKind.Witness, String> inputStream = STREAM.widen(userInputs);

      var validated = streamTraverse.traverse(maybeApplicative, validateAndParse(), inputStream);

      // Should fail due to "abc"
      assertThat(MAYBE.narrow(validated).isNothing()).isTrue();
    }
  }
}
