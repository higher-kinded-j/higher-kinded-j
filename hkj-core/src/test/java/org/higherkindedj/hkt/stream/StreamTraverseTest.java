// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.stream.StreamAssert.assertThatStream;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Traverse;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StreamTraverse Complete Test Suite")
class StreamTraverseTest extends StreamTestBase {

  private Traverse<StreamKind.Witness> streamTraverse;
  private Foldable<StreamKind.Witness> streamFoldable;

  @BeforeEach
  void setUpTraverse() {
    streamTraverse = StreamTraverse.INSTANCE;
    streamFoldable = StreamTraverse.INSTANCE;
  }

  // --- Mock/Simple Optional HKT for Testing Traverse ---

  /** HKT marker for TestOptional. Made non-generic. */
  static final class TestOptionalKindWitness {
    private TestOptionalKindWitness() {}
  }

  /**
   * HKT interface for our test Optional. Now uses the non-generic TestOptionalKindWitness marker.
   */
  interface TestOptional<A> extends Kind<TestOptionalKindWitness, A> {
    Optional<A> getOptional();

    static <A> TestOptional<A> narrow(Kind<TestOptionalKindWitness, A> kind) {
      return (TestOptional<A>) kind;
    }

    static <A> TestOptional<A> some(A value) {
      return new Some<>(value);
    }

    static <A> TestOptional<A> none() {
      return None.instance();
    }
  }

  record Some<A>(A value) implements TestOptional<A> {
    @Override
    public Optional<A> getOptional() {
      return Optional.ofNullable(value);
    }
  }

  static final class None<A> implements TestOptional<A> {
    private static final None<?> INSTANCE = new None<>();

    private None() {}

    @SuppressWarnings("unchecked")
    public static <A> None<A> instance() {
      return (None<A>) INSTANCE;
    }

    @Override
    public Optional<A> getOptional() {
      return Optional.empty();
    }
  }

  /**
   * Applicative instance for TestOptional. Now uses the non-generic TestOptionalKindWitness marker.
   */
  static class TestOptionalApplicative implements Applicative<TestOptionalKindWitness> {
    public static final TestOptionalApplicative INSTANCE = new TestOptionalApplicative();

    private TestOptionalApplicative() {}

    @Override
    public <A> @NonNull Kind<TestOptionalKindWitness, A> of(A value) {
      return TestOptional.some(value);
    }

    @Override
    public <A, B> @NonNull Kind<TestOptionalKindWitness, B> map(
        @NonNull Function<? super A, ? extends B> f, @NonNull Kind<TestOptionalKindWitness, A> fa) {
      return TestOptional.narrow(fa)
          .getOptional()
          .map(f)
          .map(TestOptional::some)
          .orElseGet(TestOptional::none);
    }

    @Override
    public <A, B> @NonNull Kind<TestOptionalKindWitness, B> ap(
        @NonNull Kind<TestOptionalKindWitness, ? extends Function<A, B>> ff,
        @NonNull Kind<TestOptionalKindWitness, A> fa) {
      Optional<? extends Function<A, B>> optFunc = TestOptional.narrow(ff).getOptional();
      Optional<A> optVal = TestOptional.narrow(fa).getOptional();

      return optFunc.flatMap(optVal::map).map(TestOptional::some).orElseGet(TestOptional::none);
    }
  }

  // --- End of Mock Optional HKT ---

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
    private final Applicative<TestOptionalKindWitness> optionalApplicative =
        TestOptionalApplicative.INSTANCE;

    Function<Integer, Kind<TestOptionalKindWitness, String>> intToOptionalStringKind =
        i -> TestOptional.some("v" + i);

    Function<Integer, Kind<TestOptionalKindWitness, Integer>> intToOptionalIntSometimesNoneKind =
        i -> (i % 2 == 0) ? TestOptional.none() : TestOptional.some(i * 3);

    @Test
    @DisplayName("traverse() on empty stream returns applicative of empty stream")
    void traverseEmptyStreamReturnsApplicativeOfEmptyStream() {
      Kind<StreamKind.Witness, Integer> emptyStreamKind = emptyStream();

      var resultKind =
          streamTraverse.traverse(optionalApplicative, intToOptionalStringKind, emptyStreamKind);

      Optional<Kind<StreamKind.Witness, String>> resultOptional =
          TestOptional.narrow(resultKind).getOptional();
      assertThat(resultOptional).isPresent();
      assertThatStream(resultOptional.get()).isEmpty();
    }

    @Test
    @DisplayName("traverse() with all effects succeeding returns applicative of stream of results")
    void traverseAllEffectsSucceedReturnsStreamOfResults() {
      var inputStream = streamOf(1, 2, 3);

      var resultKind =
          streamTraverse.traverse(optionalApplicative, intToOptionalStringKind, inputStream);

      Optional<Kind<StreamKind.Witness, String>> resultOptional =
          TestOptional.narrow(resultKind).getOptional();
      assertThat(resultOptional).isPresent();
      assertThatStream(resultOptional.get()).containsExactly("v1", "v2", "v3");
    }

    @Test
    @DisplayName("traverse() with one effect failing returns none")
    void traverseOneEffectFailsReturnsNone() {
      var inputStream = streamOf(1, 2, 3);

      var resultKind =
          streamTraverse.traverse(
              optionalApplicative, intToOptionalIntSometimesNoneKind, inputStream);

      Optional<Kind<StreamKind.Witness, Integer>> resultOptional =
          TestOptional.narrow(resultKind).getOptional();
      // Should be None because element 2 fails
      assertThat(resultOptional).isEmpty();
    }

    @Test
    @DisplayName("traverse() consumes the stream")
    void traverseConsumesStream() {
      Stream<Integer> stream = Stream.of(1, 2, 3);
      Kind<StreamKind.Witness, Integer> streamKind = STREAM.widen(stream);

      // Traverse consumes the stream
      streamTraverse.traverse(optionalApplicative, intToOptionalStringKind, streamKind);

      // Attempting to use the original stream should fail
      // Note: We can't directly test this with the wrapped stream, but we can verify
      // that a second traverse would fail if we tried to reuse it
      // This is more of a documentation point than a test
    }

    @Test
    @DisplayName("traverse() works with limited infinite stream")
    void traverseWorksWithLimitedInfiniteStream() {
      // Create a limited infinite stream
      Kind<StreamKind.Witness, Integer> limitedInfinite =
          STREAM.widen(Stream.iterate(1, n -> n + 1).limit(10));

      var resultKind =
          streamTraverse.traverse(optionalApplicative, intToOptionalStringKind, limitedInfinite);

      Optional<Kind<StreamKind.Witness, String>> resultOptional =
          TestOptional.narrow(resultKind).getOptional();
      assertThat(resultOptional).isPresent();
      assertThatStream(resultOptional.get())
          .hasSize(10)
          .containsExactly("v1", "v2", "v3", "v4", "v5", "v6", "v7", "v8", "v9", "v10");
    }
  }

  @Nested
  @DisplayName("sequenceA method")
  class SequenceATests {
    private final Applicative<TestOptionalKindWitness> optionalApplicative =
        TestOptionalApplicative.INSTANCE;

    @Test
    @DisplayName("sequenceA() on empty stream returns applicative of empty stream")
    void sequenceAEmptyStreamReturnsApplicativeOfEmptyStream() {
      Kind<StreamKind.Witness, Kind<TestOptionalKindWitness, String>> emptyStreamOfOptionals =
          emptyStream();

      var result = streamTraverse.sequenceA(optionalApplicative, emptyStreamOfOptionals);

      Optional<Kind<StreamKind.Witness, String>> resultOptional =
          TestOptional.narrow(result).getOptional();
      assertThat(resultOptional).isPresent();
      assertThatStream(resultOptional.get()).isEmpty();
    }

    @Test
    @DisplayName("sequenceA() with all some returns some of stream")
    void sequenceAAllSomeReturnsSomeOfStream() {
      Stream<Kind<TestOptionalKindWitness, Integer>> stream =
          Stream.of(TestOptional.some(1), TestOptional.some(2), TestOptional.some(3));
      Kind<StreamKind.Witness, Kind<TestOptionalKindWitness, Integer>> streamOfOptionals =
          STREAM.widen(stream);

      var result = streamTraverse.sequenceA(optionalApplicative, streamOfOptionals);

      Optional<Kind<StreamKind.Witness, Integer>> resultOptional =
          TestOptional.narrow(result).getOptional();
      assertThat(resultOptional).isPresent();
      assertThatStream(resultOptional.get()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("sequenceA() with none returns none")
    void sequenceAContainsNoneReturnsNone() {
      Stream<Kind<TestOptionalKindWitness, Integer>> stream =
          Stream.of(TestOptional.some(1), TestOptional.none(), TestOptional.some(3));
      Kind<StreamKind.Witness, Kind<TestOptionalKindWitness, Integer>> streamOfOptionals =
          STREAM.widen(stream);

      var result = streamTraverse.sequenceA(optionalApplicative, streamOfOptionals);

      Optional<Kind<StreamKind.Witness, Integer>> resultOptional =
          TestOptional.narrow(result).getOptional();
      assertThat(resultOptional).isEmpty();
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

      // First fold
      streamFoldable.foldMap(sumMonoid, Function.identity(), streamKind);

      // Stream is consumed, can't be reused
      // (This is a documentation point - the stream is already consumed)
    }
  }

  @Nested
  @DisplayName("Integration tests")
  class IntegrationTests {
    private final Applicative<TestOptionalKindWitness> optionalApplicative =
        TestOptionalApplicative.INSTANCE;

    @Test
    @DisplayName("Real-world example: validate and process stream pipeline")
    void realWorldValidateAndProcessStreamPipeline() {
      // Simulate processing a stream of user inputs
      Stream<String> userInputs = Stream.of("10", "20", "30", "40");
      Kind<StreamKind.Witness, String> inputStream = STREAM.widen(userInputs);

      // Validation function that parses and validates
      Function<String, Kind<TestOptionalKindWitness, Integer>> validateAndParse =
          str -> {
            try {
              int value = Integer.parseInt(str);
              return value > 0 ? TestOptional.some(value) : TestOptional.none();
            } catch (NumberFormatException e) {
              return TestOptional.none();
            }
          };

      // Traverse: validate all inputs
      var validated = streamTraverse.traverse(optionalApplicative, validateAndParse, inputStream);

      // All valid, should get Some(stream of integers)
      Optional<Kind<StreamKind.Witness, Integer>> resultOptional =
          TestOptional.narrow(validated).getOptional();
      assertThat(resultOptional).isPresent();

      // Now fold the validated stream
      Kind<StreamKind.Witness, Integer> validatedStream = resultOptional.get();
      Monoid<Integer> sumMonoid = Monoids.integerAddition();
      Integer sum = streamFoldable.foldMap(sumMonoid, Function.identity(), validatedStream);

      assertThat(sum).isEqualTo(100); // 10 + 20 + 30 + 40
    }

    @Test
    @DisplayName("Real-world example: early termination on validation failure")
    void realWorldEarlyTerminationOnValidationFailure() {
      // Some invalid inputs
      Stream<String> userInputs = Stream.of("10", "abc", "30");
      Kind<StreamKind.Witness, String> inputStream = STREAM.widen(userInputs);

      Function<String, Kind<TestOptionalKindWitness, Integer>> validateAndParse =
          str -> {
            try {
              int value = Integer.parseInt(str);
              return value > 0 ? TestOptional.some(value) : TestOptional.none();
            } catch (NumberFormatException e) {
              return TestOptional.none();
            }
          };

      var validated = streamTraverse.traverse(optionalApplicative, validateAndParse, inputStream);

      // Should fail due to "abc"
      Optional<Kind<StreamKind.Witness, Integer>> resultOptional =
          TestOptional.narrow(validated).getOptional();
      assertThat(resultOptional).isEmpty();
    }
  }
}
