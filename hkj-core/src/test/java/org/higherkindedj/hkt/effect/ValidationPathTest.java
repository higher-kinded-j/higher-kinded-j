// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.effect.capability.Accumulating;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for ValidationPath.
 *
 * <p>Tests cover factory methods, Composable/Chainable/Accumulating/Recoverable operations, utility
 * methods, and object methods.
 */
@DisplayName("ValidationPath<E, A> Complete Test Suite")
class ValidationPathTest {

  private static final String TEST_VALUE = "test";
  private static final String TEST_ERROR = "error";
  private static final Semigroup<String> STRING_SEMIGROUP = (a, b) -> a + ", " + b;
  private static final Semigroup<List<String>> LIST_SEMIGROUP =
      (a, b) -> {
        var result = new ArrayList<>(a);
        result.addAll(b);
        return result;
      };

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.valid() creates ValidationPath with value")
    void pathValidCreatesValidationPathWithValue() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      assertThat(path.run().isValid()).isTrue();
      assertThat(path.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("Path.invalid() creates ValidationPath with error")
    void pathInvalidCreatesValidationPathWithError() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      assertThat(path.run().isInvalid()).isTrue();
      assertThat(path.run().getError()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("Path.validated() creates ValidationPath from Validated")
    void pathValidatedCreatesValidationPathFromValidated() {
      Validated<String, String> valid = Validated.valid(TEST_VALUE);
      Validated<String, String> invalid = Validated.invalid(TEST_ERROR);

      ValidationPath<String, String> validPath = Path.validated(valid, STRING_SEMIGROUP);
      ValidationPath<String, String> invalidPath = Path.validated(invalid, STRING_SEMIGROUP);

      assertThat(validPath.run().isValid()).isTrue();
      assertThat(validPath.run().get()).isEqualTo(TEST_VALUE);
      assertThat(invalidPath.run().isInvalid()).isTrue();
      assertThat(invalidPath.run().getError()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("Path.valid() validates non-null semigroup")
    void pathValidValidatesNonNullSemigroup() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.valid(TEST_VALUE, null))
          .withMessageContaining("semigroup must not be null");
    }

    @Test
    @DisplayName("Path.invalid() validates non-null error")
    void pathInvalidValidatesNonNullError() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.invalid(null, STRING_SEMIGROUP))
          .withMessageContaining("error must not be null");
    }
  }

  @Nested
  @DisplayName("Run and Getter Methods")
  class RunAndGetterMethodsTests {

    @Test
    @DisplayName("run() returns underlying Validated")
    void runReturnsUnderlyingValidated() {
      ValidationPath<String, String> validPath = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      ValidationPath<String, String> invalidPath = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      assertThat(validPath.run()).isInstanceOf(Validated.class);
      assertThat(validPath.run().get()).isEqualTo(TEST_VALUE);
      assertThat(invalidPath.run().isInvalid()).isTrue();
    }

    @Test
    @DisplayName("isValid() returns true for Valid")
    void isValidReturnsTrueForValid() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      assertThat(path.isValid()).isTrue();
    }

    @Test
    @DisplayName("isValid() returns false for Invalid")
    void isValidReturnsFalseForInvalid() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);
      assertThat(path.isValid()).isFalse();
    }

    @Test
    @DisplayName("isInvalid() returns true for Invalid")
    void isInvalidReturnsTrueForInvalid() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);
      assertThat(path.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("isInvalid() returns false for Valid")
    void isInvalidReturnsFalseForValid() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      assertThat(path.isInvalid()).isFalse();
    }

    @Test
    @DisplayName("fold() applies invalidMapper for Invalid")
    void foldAppliesInvalidMapperForInvalid() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      Integer result = path.fold(String::length, String::length);

      assertThat(result).isEqualTo(TEST_ERROR.length());
    }

    @Test
    @DisplayName("fold() applies validMapper for Valid")
    void foldAppliesValidMapperForValid() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      Integer result = path.fold(e -> -1, String::length);

      assertThat(result).isEqualTo(TEST_VALUE.length());
    }

    @Test
    @DisplayName("fold() validates null mappers")
    void foldValidatesNullMappers() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      assertThatNullPointerException()
          .isThrownBy(() -> path.fold(null, String::length))
          .withMessageContaining("invalidMapper must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.fold(String::length, null))
          .withMessageContaining("validMapper must not be null");
    }

    @Test
    @DisplayName("semigroup() returns the configured Semigroup")
    void semigroupReturnsConfiguredSemigroup() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      assertThat(path.semigroup()).isSameAs(STRING_SEMIGROUP);
    }

    @Test
    @DisplayName("getOrElse() returns value for Valid")
    void getOrElseReturnsValueForValid() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      assertThat(path.getOrElse("default")).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("getOrElse() returns default for Invalid")
    void getOrElseReturnsDefaultForInvalid() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      assertThat(path.getOrElse("default")).isEqualTo("default");
    }

    @Test
    @DisplayName("getOrElseGet() returns value for Valid without calling supplier")
    void getOrElseGetReturnsValueForValidWithoutCallingSupplier() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      AtomicBoolean called = new AtomicBoolean(false);

      String result =
          path.getOrElseGet(
              () -> {
                called.set(true);
                return "default";
              });

      assertThat(result).isEqualTo(TEST_VALUE);
      assertThat(called).isFalse();
    }

    @Test
    @DisplayName("getOrElseGet() calls supplier for Invalid")
    void getOrElseGetCallsSupplierForInvalid() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);
      AtomicBoolean called = new AtomicBoolean(false);

      String result =
          path.getOrElseGet(
              () -> {
                called.set(true);
                return "default";
              });

      assertThat(result).isEqualTo("default");
      assertThat(called).isTrue();
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms value for Valid")
    void mapTransformsValueForValid() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      ValidationPath<String, Integer> result = path.map(String::length);

      assertThat(result.run().isValid()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE.length());
    }

    @Test
    @DisplayName("map() preserves Invalid")
    void mapPreservesInvalid() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      ValidationPath<String, Integer> result = path.map(String::length);

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("peek() observes value without modifying")
    void peekObservesValueWithoutModifying() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      AtomicBoolean called = new AtomicBoolean(false);

      ValidationPath<String, String> result = path.peek(v -> called.set(true));

      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
      assertThat(called).isTrue();
    }

    @Test
    @DisplayName("peek() does not call consumer for Invalid")
    void peekDoesNotCallConsumerForInvalid() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);
      AtomicBoolean called = new AtomicBoolean(false);

      path.peek(v -> called.set(true));

      assertThat(called).isFalse();
    }

    @Test
    @DisplayName("peekInvalid() observes error without modifying")
    void peekInvalidObservesErrorWithoutModifying() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);
      AtomicBoolean called = new AtomicBoolean(false);
      List<String> observed = new ArrayList<>();

      ValidationPath<String, String> result =
          path.peekInvalid(
              e -> {
                called.set(true);
                observed.add(e);
              });

      assertThat(result.run().getError()).isEqualTo(TEST_ERROR);
      assertThat(called).isTrue();
      assertThat(observed).containsExactly(TEST_ERROR);
    }

    @Test
    @DisplayName("peekInvalid() does not call consumer for Valid")
    void peekInvalidDoesNotCallConsumerForValid() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      AtomicBoolean called = new AtomicBoolean(false);

      path.peekInvalid(e -> called.set(true));

      assertThat(called).isFalse();
    }

    @Test
    @DisplayName("peekInvalid() validates null consumer")
    void peekInvalidValidatesNullConsumer() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      assertThatNullPointerException()
          .isThrownBy(() -> path.peekInvalid(null))
          .withMessageContaining("consumer must not be null");
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, then) - Short-Circuit Behavior")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() chains dependent computations for Valid")
    void viaChainsComputationsForValid() {
      ValidationPath<String, String> path = Path.valid("hello", STRING_SEMIGROUP);

      ValidationPath<String, Integer> result =
          path.via(s -> Path.valid(s.length(), STRING_SEMIGROUP));

      assertThat(result.run().get()).isEqualTo(5);
    }

    @Test
    @DisplayName("via() short-circuits on Invalid (does not accumulate)")
    void viaShortCircuitsOnInvalid() {
      ValidationPath<String, String> path = Path.invalid("error1", STRING_SEMIGROUP);

      ValidationPath<String, Integer> result =
          path.via(s -> Path.invalid("error2", STRING_SEMIGROUP));

      // Short-circuits: only first error
      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo("error1");
    }

    @Test
    @DisplayName("via() short-circuits on Invalid result")
    void viaShortCircuitsOnInvalidResult() {
      ValidationPath<String, String> path = Path.valid("hello", STRING_SEMIGROUP);

      ValidationPath<String, Integer> result =
          path.via(s -> Path.<String, Integer>invalid("error", STRING_SEMIGROUP))
              .via(i -> Path.valid(i * 2, STRING_SEMIGROUP));

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo("error");
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() validates result is ValidationPath")
    void viaValidatesResultType() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.via(s -> Path.right(s.length())))
          .withMessageContaining("via mapper must return ValidationPath");
    }

    @Test
    @DisplayName("then() sequences computations discarding value")
    void thenSequencesComputationsDiscardingValue() {
      ValidationPath<String, String> path = Path.valid("hello", STRING_SEMIGROUP);
      AtomicBoolean firstExecuted = new AtomicBoolean(false);

      ValidationPath<String, Integer> result =
          path.peek(v -> firstExecuted.set(true)).then(() -> Path.valid(42, STRING_SEMIGROUP));

      assertThat(result.run().get()).isEqualTo(42);
      assertThat(firstExecuted).isTrue();
    }

    @Test
    @DisplayName("then() short-circuits on Invalid")
    void thenShortCircuitsOnInvalid() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      ValidationPath<String, Integer> result = path.then(() -> Path.valid(42, STRING_SEMIGROUP));

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("then() validates result is ValidationPath")
    void thenValidatesResultType() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.then(() -> Path.right(42)))
          .withMessageContaining("then supplier must return ValidationPath");
    }
  }

  @Nested
  @DisplayName("Accumulating Operations (zipWithAccum, andAlso, andThen) - Error Accumulation")
  class AccumulatingOperationsTests {

    @Test
    @DisplayName("zipWithAccum() combines two Valid values")
    void zipWithAccumCombinesTwoValidValues() {
      ValidationPath<String, String> first = Path.valid("hello", STRING_SEMIGROUP);
      ValidationPath<String, Integer> second = Path.valid(3, STRING_SEMIGROUP);

      ValidationPath<String, String> result = first.zipWithAccum(second, (s, n) -> s.repeat(n));

      assertThat(result.run().get()).isEqualTo("hellohellohello");
    }

    @Test
    @DisplayName("zipWithAccum() accumulates errors from both Invalid paths")
    void zipWithAccumAccumulatesErrors() {
      ValidationPath<String, String> first = Path.invalid("error1", STRING_SEMIGROUP);
      ValidationPath<String, Integer> second = Path.invalid("error2", STRING_SEMIGROUP);

      ValidationPath<String, String> result = first.zipWithAccum(second, (s, n) -> s.repeat(n));

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo("error1, error2");
    }

    @Test
    @DisplayName("zipWithAccum() returns error if first is Invalid")
    void zipWithAccumReturnsErrorIfFirstIsInvalid() {
      ValidationPath<String, String> first = Path.invalid("error1", STRING_SEMIGROUP);
      ValidationPath<String, Integer> second = Path.valid(3, STRING_SEMIGROUP);

      ValidationPath<String, String> result = first.zipWithAccum(second, (s, n) -> s.repeat(n));

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo("error1");
    }

    @Test
    @DisplayName("zipWithAccum() returns error if second is Invalid")
    void zipWithAccumReturnsErrorIfSecondIsInvalid() {
      ValidationPath<String, String> first = Path.valid("hello", STRING_SEMIGROUP);
      ValidationPath<String, Integer> second = Path.invalid("error2", STRING_SEMIGROUP);

      ValidationPath<String, String> result = first.zipWithAccum(second, (s, n) -> s.repeat(n));

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo("error2");
    }

    @Test
    @DisplayName("zipWith3Accum() combines three Valid values")
    void zipWith3AccumCombinesThreeValidValues() {
      ValidationPath<String, String> first = Path.valid("hello", STRING_SEMIGROUP);
      ValidationPath<String, String> second = Path.valid(" ", STRING_SEMIGROUP);
      ValidationPath<String, String> third = Path.valid("world", STRING_SEMIGROUP);

      ValidationPath<String, String> result =
          first.zipWith3Accum(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().get()).isEqualTo("hello world");
    }

    @Test
    @DisplayName("zipWith3Accum() accumulates all errors")
    void zipWith3AccumAccumulatesAllErrors() {
      ValidationPath<String, String> first = Path.invalid("e1", STRING_SEMIGROUP);
      ValidationPath<String, String> second = Path.invalid("e2", STRING_SEMIGROUP);
      ValidationPath<String, String> third = Path.invalid("e3", STRING_SEMIGROUP);

      ValidationPath<String, String> result =
          first.zipWith3Accum(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo("e1, e2, e3");
    }

    @Test
    @DisplayName("andAlso() keeps this value when both are Valid")
    void andAlsoKeepsThisValueWhenBothValid() {
      ValidationPath<String, String> first = Path.valid("keep", STRING_SEMIGROUP);
      ValidationPath<String, Integer> second = Path.valid(42, STRING_SEMIGROUP);

      Accumulating<String, String> result = first.andAlso(second);

      assertThat(((ValidationPath<String, String>) result).run().get()).isEqualTo("keep");
    }

    @Test
    @DisplayName("andAlso() accumulates errors")
    void andAlsoAccumulatesErrors() {
      ValidationPath<String, String> first = Path.invalid("e1", STRING_SEMIGROUP);
      ValidationPath<String, Integer> second = Path.invalid("e2", STRING_SEMIGROUP);

      Accumulating<String, String> result = first.andAlso(second);

      assertThat(((ValidationPath<String, String>) result).run().getError()).isEqualTo("e1, e2");
    }

    @Test
    @DisplayName("andThen() keeps other value when both are Valid")
    void andThenKeepsOtherValueWhenBothValid() {
      ValidationPath<String, String> first = Path.valid("discard", STRING_SEMIGROUP);
      ValidationPath<String, Integer> second = Path.valid(42, STRING_SEMIGROUP);

      Accumulating<String, Integer> result = first.andThen(second);

      assertThat(((ValidationPath<String, Integer>) result).run().get()).isEqualTo(42);
    }

    @Test
    @DisplayName("andThen() accumulates errors")
    void andThenAccumulatesErrors() {
      ValidationPath<String, String> first = Path.invalid("e1", STRING_SEMIGROUP);
      ValidationPath<String, Integer> second = Path.invalid("e2", STRING_SEMIGROUP);

      Accumulating<String, Integer> result = first.andThen(second);

      assertThat(((ValidationPath<String, Integer>) result).run().getError()).isEqualTo("e1, e2");
    }

    @Test
    @DisplayName("zipWithAccum() with List semigroup accumulates list errors")
    void zipWithAccumWithListSemigroup() {
      ValidationPath<List<String>, String> first = Path.invalid(List.of("error1"), LIST_SEMIGROUP);
      ValidationPath<List<String>, Integer> second =
          Path.invalid(List.of("error2", "error3"), LIST_SEMIGROUP);

      ValidationPath<List<String>, String> result =
          first.zipWithAccum(second, (s, n) -> s.repeat(n));

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).containsExactly("error1", "error2", "error3");
    }

    // Note: Tests for "non-ValidationPath" error cases are not possible because
    // Accumulating is a sealed interface that only permits ValidationPath.
    // The defensive error handling in ValidationPath for non-ValidationPath inputs
    // exists as a safety measure but cannot be triggered in normal usage.
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith) - Short-Circuit Behavior")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines two Valid values")
    void zipWithCombinesTwoValidValues() {
      ValidationPath<String, String> first = Path.valid("hello", STRING_SEMIGROUP);
      ValidationPath<String, Integer> second = Path.valid(3, STRING_SEMIGROUP);

      ValidationPath<String, String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.run().get()).isEqualTo("hellohellohello");
    }

    @Test
    @DisplayName("zipWith() short-circuits on first Invalid (does not accumulate)")
    void zipWithShortCircuitsOnFirstInvalid() {
      ValidationPath<String, String> first = Path.invalid("error1", STRING_SEMIGROUP);
      ValidationPath<String, Integer> second = Path.invalid("error2", STRING_SEMIGROUP);

      ValidationPath<String, String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      // Short-circuits: only first error
      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo("error1");
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(Path.valid("x", STRING_SEMIGROUP), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith() throws when given non-ValidationPath")
    void zipWithThrowsWhenGivenNonValidationPath() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      EitherPath<String, Integer> eitherPath = Path.right(42);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(eitherPath, (s, i) -> s + i))
          .withMessageContaining("Cannot zipWith non-ValidationPath");
    }

    @Test
    @DisplayName("zipWith() returns second error when first is Valid and second is Invalid")
    void zipWithReturnsSecondErrorWhenFirstValidSecondInvalid() {
      ValidationPath<String, String> first = Path.valid("hello", STRING_SEMIGROUP);
      ValidationPath<String, Integer> second = Path.invalid("error2", STRING_SEMIGROUP);

      ValidationPath<String, String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo("error2");
    }

    @Test
    @DisplayName("zipWith3() combines three Valid values (short-circuit)")
    void zipWith3CombinesThreeValidValues() {
      ValidationPath<String, String> first = Path.valid("hello", STRING_SEMIGROUP);
      ValidationPath<String, String> second = Path.valid(" ", STRING_SEMIGROUP);
      ValidationPath<String, String> third = Path.valid("world", STRING_SEMIGROUP);

      ValidationPath<String, String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().get()).isEqualTo("hello world");
    }

    @Test
    @DisplayName("zipWith3() short-circuits on first Invalid")
    void zipWith3ShortCircuitsOnFirstInvalid() {
      ValidationPath<String, String> first = Path.invalid("e1", STRING_SEMIGROUP);
      ValidationPath<String, String> second = Path.invalid("e2", STRING_SEMIGROUP);
      ValidationPath<String, String> third = Path.invalid("e3", STRING_SEMIGROUP);

      ValidationPath<String, String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      // Short-circuits: only first error, no accumulation
      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo("e1");
    }

    @Test
    @DisplayName("zipWith3() short-circuits on second Invalid")
    void zipWith3ShortCircuitsOnSecondInvalid() {
      ValidationPath<String, String> first = Path.valid("hello", STRING_SEMIGROUP);
      ValidationPath<String, String> second = Path.invalid("e2", STRING_SEMIGROUP);
      ValidationPath<String, String> third = Path.invalid("e3", STRING_SEMIGROUP);

      ValidationPath<String, String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      // Short-circuits: only second error
      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo("e2");
    }

    @Test
    @DisplayName("zipWith3() short-circuits on third Invalid")
    void zipWith3ShortCircuitsOnThirdInvalid() {
      ValidationPath<String, String> first = Path.valid("hello", STRING_SEMIGROUP);
      ValidationPath<String, String> second = Path.valid(" ", STRING_SEMIGROUP);
      ValidationPath<String, String> third = Path.invalid("e3", STRING_SEMIGROUP);

      ValidationPath<String, String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo("e3");
    }

    @Test
    @DisplayName("zipWith3() validates null parameters")
    void zipWith3ValidatesNullParameters() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      ValidationPath<String, String> other = Path.valid("other", STRING_SEMIGROUP);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith3(null, other, (a, b, c) -> a + b + c))
          .withMessageContaining("second must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith3(other, null, (a, b, c) -> a + b + c))
          .withMessageContaining("third must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith3(other, other, null))
          .withMessageContaining("combiner must not be null");
    }
  }

  @Nested
  @DisplayName("Recoverable Operations")
  class RecoverableOperationsTests {

    @Test
    @DisplayName("recover() provides fallback for Invalid")
    void recoverProvidesFallbackForInvalid() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      ValidationPath<String, String> result = path.recover(error -> "recovered: " + error);

      assertThat(result.run().get()).isEqualTo("recovered: " + TEST_ERROR);
    }

    @Test
    @DisplayName("recover() preserves Valid value")
    void recoverPreservesValidValue() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      ValidationPath<String, String> result = path.recover(error -> "recovered");

      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recoverWith() provides fallback path for Invalid")
    void recoverWithProvidesFallbackPathForInvalid() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      ValidationPath<String, String> result =
          path.recoverWith(error -> Path.valid("fallback", STRING_SEMIGROUP));

      assertThat(result.run().get()).isEqualTo("fallback");
    }

    @Test
    @DisplayName("recoverWith() preserves Valid value")
    void recoverWithPreservesValidValue() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      ValidationPath<String, String> result =
          path.recoverWith(error -> Path.valid("fallback", STRING_SEMIGROUP));

      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("orElse() provides alternative path for Invalid")
    void orElseAlternativeForInvalid() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      ValidationPath<String, String> result =
          path.orElse(() -> Path.valid("alternative", STRING_SEMIGROUP));

      assertThat(result.run().get()).isEqualTo("alternative");
    }

    @Test
    @DisplayName("orElse() preserves Valid value")
    void orElsePreservesValidValue() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      AtomicBoolean called = new AtomicBoolean(false);

      ValidationPath<String, String> result =
          path.orElse(
              () -> {
                called.set(true);
                return Path.valid("alternative", STRING_SEMIGROUP);
              });

      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
      assertThat(called).isFalse();
    }

    @Test
    @DisplayName("mapError() throws UnsupportedOperationException for Invalid")
    void mapErrorThrowsUnsupportedOperationException() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      // mapError is not supported on ValidationPath because changing error type
      // requires providing a new Semigroup for the new error type
      assertThatThrownBy(() -> path.mapError(String::length))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining(
              "mapError on ValidationPath requires creating a new ValidationPath");
    }

    @Test
    @DisplayName("mapError() on Valid returns valid with placeholder semigroup")
    void mapErrorOnValidReturnsValidWithPlaceholderSemigroup() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      // mapError on Valid path returns a new ValidationPath with a placeholder semigroup
      ValidationPath<Integer, String> result = path.mapError(String::length);

      // The result should be valid with the same value
      assertThat(result.run().isValid()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);

      // The placeholder semigroup throws when used for accumulation
      ValidationPath<Integer, String> invalid = Path.invalid(1, result.semigroup());
      ValidationPath<Integer, String> anotherInvalid = Path.invalid(2, result.semigroup());

      assertThatThrownBy(() -> invalid.zipWithAccum(anotherInvalid, (a, b) -> a + b))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("Cannot accumulate errors after mapError");
    }

    @Test
    @DisplayName("mapError() workaround via toEitherPath()")
    void mapErrorWorkaroundViaToEitherPath() {
      // Demonstrates the recommended approach for mapError on ValidationPath
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);
      Semigroup<Integer> intSemigroup = Integer::sum;

      // Convert to EitherPath, map error, convert back with new Semigroup
      EitherPath<Integer, String> mapped = path.toEitherPath().mapError(String::length);
      ValidationPath<Integer, String> result = mapped.toValidationPath(intSemigroup);

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo(TEST_ERROR.length());
    }

    @Test
    @DisplayName("recoverWith() throws when recovery returns non-ValidationPath")
    void recoverWithThrowsWhenRecoveryReturnsNonValidationPath() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.recoverWith(e -> Path.right("fallback")))
          .withMessageContaining("recovery must return ValidationPath");
    }

    @Test
    @DisplayName("orElse() throws when alternative returns non-ValidationPath")
    void orElseThrowsWhenAlternativeReturnsNonValidationPath() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.orElse(() -> Path.right("alternative")))
          .withMessageContaining("alternative must return ValidationPath");
    }

    @Test
    @DisplayName("mapErrorWith() transforms error with new Semigroup for Invalid")
    void mapErrorWithTransformsErrorForInvalid() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);
      Semigroup<Integer> intSemigroup = Integer::sum;

      ValidationPath<Integer, String> result = path.mapErrorWith(String::length, intSemigroup);

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo(TEST_ERROR.length());
      assertThat(result.semigroup()).isSameAs(intSemigroup);
    }

    @Test
    @DisplayName("mapErrorWith() preserves Valid with new Semigroup")
    void mapErrorWithPreservesValidWithNewSemigroup() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      Semigroup<Integer> intSemigroup = Integer::sum;

      ValidationPath<Integer, String> result = path.mapErrorWith(String::length, intSemigroup);

      assertThat(result.run().isValid()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
      assertThat(result.semigroup()).isSameAs(intSemigroup);
    }

    @Test
    @DisplayName("mapErrorWith() validates null mapper")
    void mapErrorWithValidatesNullMapper() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      Semigroup<Integer> intSemigroup = Integer::sum;

      assertThatNullPointerException()
          .isThrownBy(() -> path.mapErrorWith(null, intSemigroup))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("mapErrorWith() validates null semigroup")
    void mapErrorWithValidatesNullSemigroup() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      assertThatNullPointerException()
          .isThrownBy(() -> path.mapErrorWith(String::length, null))
          .withMessageContaining("newSemigroup must not be null");
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toEitherPath() converts Valid to Right")
    void toEitherPathConvertsValidToRight() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      EitherPath<String, String> result = path.toEitherPath();

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toEitherPath() converts Invalid to Left")
    void toEitherPathConvertsInvalidToLeft() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      EitherPath<String, String> result = path.toEitherPath();

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("toMaybePath() converts Valid to Just")
    void toMaybePathConvertsValidToJust() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toMaybePath() converts Invalid to Nothing")
    void toMaybePathConvertsInvalidToNothing() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("toTryPath() converts Valid to Success")
    void toTryPathConvertsValidToSuccess() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      TryPath<String> result = path.toTryPath(RuntimeException::new);

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.getOrElse(null)).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toTryPath() converts Invalid to Failure")
    void toTryPathConvertsInvalidToFailure() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      TryPath<String> result = path.toTryPath(RuntimeException::new);

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("toOptionalPath() converts Valid to present")
    void toOptionalPathConvertsValidToPresent() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      OptionalPath<String> result = path.toOptionalPath();

      assertThat(result.run()).isPresent();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toOptionalPath() converts Invalid to empty")
    void toOptionalPathConvertsInvalidToEmpty() {
      ValidationPath<String, String> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      OptionalPath<String> result = path.toOptionalPath();

      assertThat(result.run()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("equals() works correctly")
    void equalsWorksCorrectly() {
      ValidationPath<String, String> path1 = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      ValidationPath<String, String> path2 = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      ValidationPath<String, String> path3 = Path.valid("different", STRING_SEMIGROUP);
      ValidationPath<String, String> invalid1 = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);
      ValidationPath<String, String> invalid2 = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      assertThat(path1).isEqualTo(path2);
      assertThat(path1).isNotEqualTo(path3);
      assertThat(path1).isNotEqualTo(invalid1);
      assertThat(invalid1).isEqualTo(invalid2);
    }

    @Test
    @DisplayName("equals() returns true for same instance")
    void equalsReturnsTrueForSameInstance() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      assertThat(path.equals(path)).isTrue();
    }

    @Test
    @DisplayName("equals() returns false for non-ValidationPath")
    void equalsReturnsFalseForNonValidationPath() {
      ValidationPath<String, String> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      assertThat(path.equals("not a ValidationPath")).isFalse();
      assertThat(path.equals(null)).isFalse();
    }

    @Test
    @DisplayName("hashCode() is consistent with equals")
    void hashCodeIsConsistentWithEquals() {
      ValidationPath<String, String> path1 = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      ValidationPath<String, String> path2 = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      ValidationPath<String, String> validPath = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
      ValidationPath<String, String> invalidPath = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      assertThat(validPath.toString()).contains("ValidationPath");
      assertThat(validPath.toString()).contains("Valid");
      assertThat(invalidPath.toString()).contains("ValidationPath");
      assertThat(invalidPath.toString()).contains("Invalid");
    }
  }

  @Nested
  @DisplayName("Complex Validation Patterns")
  class ComplexValidationPatternsTests {

    @Test
    @DisplayName("Form validation accumulates all field errors")
    void formValidationAccumulatesAllFieldErrors() {
      // Simulate validating a form with multiple fields
      ValidationPath<List<String>, String> nameValidation =
          Path.invalid(List.of("Name is required"), LIST_SEMIGROUP);
      ValidationPath<List<String>, String> emailValidation =
          Path.invalid(List.of("Invalid email format"), LIST_SEMIGROUP);
      ValidationPath<List<String>, Integer> ageValidation =
          Path.invalid(List.of("Age must be positive", "Age must be under 150"), LIST_SEMIGROUP);

      ValidationPath<List<String>, String> result =
          nameValidation.zipWith3Accum(
              emailValidation,
              ageValidation,
              (name, email, age) -> String.format("%s <%s>, age %d", name, email, age));

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError())
          .containsExactly(
              "Name is required",
              "Invalid email format",
              "Age must be positive",
              "Age must be under 150");
    }

    @Test
    @DisplayName("Successful form validation combines all values")
    void successfulFormValidationCombinesValues() {
      ValidationPath<List<String>, String> nameValidation = Path.valid("John Doe", LIST_SEMIGROUP);
      ValidationPath<List<String>, String> emailValidation =
          Path.valid("john@example.com", LIST_SEMIGROUP);
      ValidationPath<List<String>, Integer> ageValidation = Path.valid(30, LIST_SEMIGROUP);

      ValidationPath<List<String>, String> result =
          nameValidation.zipWith3Accum(
              emailValidation,
              ageValidation,
              (name, email, age) -> String.format("%s <%s>, age %d", name, email, age));

      assertThat(result.run().get()).isEqualTo("John Doe <john@example.com>, age 30");
    }

    @Test
    @DisplayName("Sequential validation with via short-circuits on first error")
    void sequentialValidationShortCircuits() {
      ValidationPath<String, String> result =
          Path.valid("input", STRING_SEMIGROUP)
              .via(s -> Path.<String, String>invalid("step1 failed", STRING_SEMIGROUP))
              .via(s -> Path.<String, String>invalid("step2 failed", STRING_SEMIGROUP));

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo("step1 failed");
    }

    @Test
    @DisplayName("Mixing short-circuit and accumulating operations")
    void mixingShortCircuitAndAccumulating() {
      // First validate independently with accumulation
      ValidationPath<String, String> name = Path.invalid("name error", STRING_SEMIGROUP);
      ValidationPath<String, String> email = Path.invalid("email error", STRING_SEMIGROUP);

      ValidationPath<String, String> combinedValidation =
          name.zipWithAccum(email, (n, e) -> n + " " + e);

      // Then sequence with short-circuit
      ValidationPath<String, String> result =
          combinedValidation.via(s -> Path.valid(s.toUpperCase(), STRING_SEMIGROUP));

      // Accumulated errors from first step
      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo("name error, email error");
    }
  }
}
