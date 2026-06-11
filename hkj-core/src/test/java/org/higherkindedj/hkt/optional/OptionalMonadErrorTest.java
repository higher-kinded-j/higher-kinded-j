// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.OptionalKindAssert.assertThatOptionalKind;
import static org.higherkindedj.hkt.instances.Witnesses.optional;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("OptionalMonad — error handling")
class OptionalMonadErrorTest extends OptionalTestBase {

  private MonadError<OptionalKind.Witness, Unit> monadError;
  private Function<Unit, Kind<OptionalKind.Witness, Integer>> validHandler;
  private Kind<OptionalKind.Witness, Integer> validFallback;

  @BeforeEach
  void setUpMonadError() {
    monadError = Instances.monadError(optional());
    validHandler = _ -> monadError.of(-1);
    validFallback = monadError.of(-999);
    validateMonadFixtures();
  }

  /**
   * Operations, null-argument validation and exception propagation on the MonadError instance. The
   * Monad/MonadError laws are verified parameterised in {@link OptionalMonadTest}, so this contract
   * omits {@link Category#LAWS}.
   *
   * <p>{@link Category#VALIDATIONS} <em>is</em> run: {@code OptionalMonad} now overrides {@code
   * recoverWith} to reject a null fallback eagerly (regardless of whether {@code ma} is present or
   * empty), matching {@code Either}/{@code Maybe}. Per-method message assertions live in the
   * operation tests below.
   */
  @Test
  @DisplayName(
      "MonadError contract — operations, validations & exceptions (laws in OptionalMonadTest)")
  void monadErrorContract() {
    TypeClassContract.<OptionalKind.Witness, Unit>monadError(OptionalMonad.class)
        .<Integer>instance(monadError)
        .<String>withKind(validKind)
        .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
        .withErrorHandling(validHandler, validFallback)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("handleErrorWith() recovers from empty")
    void handleErrorWithRecoversFromEmpty() {
      var result = monadError.handleErrorWith(emptyOptional(), validHandler);
      assertThatOptionalKind(result).isPresent().contains(-1);
    }

    @Test
    @DisplayName("handleErrorWith() passes through a present value")
    void handleErrorWithPassesThroughPresent() {
      var result = monadError.handleErrorWith(validKind, validHandler);
      assertThat(result).isSameAs(validKind);
      assertThatOptionalKind(result).isPresent().contains(DEFAULT_PRESENT_VALUE);
    }

    @Test
    @DisplayName("raiseError() creates empty")
    void raiseErrorCreatesEmpty() {
      assertThatOptionalKind(monadError.raiseError(Unit.INSTANCE)).isEmpty();
    }

    @Test
    @DisplayName("raiseError() with null Unit still creates empty")
    void raiseErrorWithNullCreatesEmpty() {
      assertThatOptionalKind(monadError.raiseError(null)).isEmpty();
    }

    @Test
    @DisplayName("recoverWith() uses fallback on empty")
    void recoverWithUsesFallbackOnEmpty() {
      var result = monadError.recoverWith(emptyOptional(), validFallback);
      assertThatOptionalKind(result).isPresent().contains(-999);
    }

    @Test
    @DisplayName("recoverWith() passes through a present value")
    void recoverWithPassesThroughPresent() {
      var result = monadError.recoverWith(validKind, validFallback);
      assertThat(result).isSameAs(validKind);
    }

    @Test
    @DisplayName("recover() uses value on empty")
    void recoverUsesValueOnEmpty() {
      var result = monadError.recover(emptyOptional(), 100);
      assertThatOptionalKind(result).isPresent().contains(100);
    }

    @Test
    @DisplayName("recover() with null value creates empty (ofNullable semantics)")
    void recoverWithNullValueCreatesEmpty() {
      var result = monadError.recover(emptyOptional(), null);
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("handleError() transforms the error to a value")
    void handleErrorTransformsErrorToValue() {
      Function<Unit, Integer> errorHandler = _ -> 999;
      var result = monadError.handleError(emptyOptional(), errorHandler);
      assertThatOptionalKind(result).isPresent().contains(999);
    }

    @Test
    @DisplayName("zero() returns empty")
    void zeroReturnsEmpty() {
      var zero = Instances.monadZero(optional()).zero();
      assertThatOptionalKind(zero).isEmpty();
    }

    @ParameterizedTest(name = "handleErrorWith rejects null {0} argument")
    @MethodSource("handleErrorWithNullArguments")
    @DisplayName("handleErrorWith() rejects null arguments")
    void handleErrorWithRejectsNullArguments(
        String label,
        Kind<OptionalKind.Witness, Integer> ma,
        Function<Unit, Kind<OptionalKind.Witness, Integer>> handler) {
      assertThatThrownBy(() -> monadError.handleErrorWith(ma, handler))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining(label);
    }

    static Stream<Arguments> handleErrorWithNullArguments() {
      Kind<OptionalKind.Witness, Integer> okKind = Instances.monadError(optional()).of(42);
      Function<Unit, Kind<OptionalKind.Witness, Integer>> okHandler =
          _ -> Instances.monadError(optional()).of(0);
      return Stream.of(
          Arguments.of("Kind", null, okHandler), Arguments.of("handler", okKind, null));
    }

    @ParameterizedTest(name = "recoverWith rejects null {0} argument")
    @MethodSource("recoverWithNullArguments")
    @DisplayName("recoverWith() rejects null arguments eagerly, regardless of source state")
    void recoverWithRejectsNullArguments(
        String expectedMessagePart,
        Kind<OptionalKind.Witness, Integer> ma,
        Kind<OptionalKind.Witness, Integer> fallback) {
      assertThatThrownBy(() -> monadError.recoverWith(ma, fallback))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining(expectedMessagePart);
    }

    static Stream<Arguments> recoverWithNullArguments() {
      Kind<OptionalKind.Witness, Integer> present = Instances.monadError(optional()).of(42);
      Kind<OptionalKind.Witness, Integer> empty = Instances.monadZero(optional()).zero();
      Kind<OptionalKind.Witness, Integer> okFallback = Instances.monadError(optional()).of(-999);
      return Stream.of(
          Arguments.of("recoverWith (source)", null, okFallback),
          // A null fallback must be rejected for a present value too — not just on the empty path.
          Arguments.of("recoverWith (fallback)", present, null),
          Arguments.of("recoverWith (fallback)", empty, null));
    }

    @Test
    @DisplayName("recover() rejects a null source, naming recover")
    @SuppressWarnings("DataFlowIssue") // null source exercises recover's guard
    void recoverRejectsNullSource() {
      assertThatThrownBy(() -> monadError.recover(null, 1))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("recover (source)");
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Chained error recovery: the second handler succeeds")
    void chainedErrorRecovery() {
      Kind<OptionalKind.Witness, Integer> result =
          monadError.handleErrorWith(
              emptyOptional(),
              _ -> {
                Kind<OptionalKind.Witness, Integer> firstAttempt =
                    monadError.raiseError(Unit.INSTANCE);
                return monadError.handleErrorWith(firstAttempt, _ -> monadError.of(999));
              });
      assertThatOptionalKind(result).isPresent().contains(999);
    }

    @Test
    @DisplayName("Mixing map, flatMap, and error recovery")
    void mixingMapFlatMapAndErrorRecovery() {
      Kind<OptionalKind.Witness, String> result =
          monadError.flatMap(
              i -> {
                if (i < 5) return monadError.raiseError(Unit.INSTANCE);
                return monadError.map(x -> "Value:" + x, monadError.of(i * 2));
              },
              presentOf(10));
      result = monadError.handleErrorWith(result, _ -> monadError.of("Recovered"));
      assertThatOptionalKind(result).isPresent().contains("Value:20");
    }

    @Test
    @DisplayName("recoverWith() with an empty fallback stays empty")
    void recoverWithEmptyFallback() {
      Kind<OptionalKind.Witness, Integer> emptyFallback = emptyOptional();
      var result = monadError.recoverWith(emptyOptional(), emptyFallback);
      assertThatOptionalKind(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("MonadZero Tests")
  class MonadZeroTests {

    @Test
    @DisplayName("zero() is consistent with raiseError()")
    void zeroIsConsistentWithRaiseError() {
      var zero = Instances.monadZero(optional()).zero();
      var raised = monadError.raiseError(Unit.INSTANCE);
      assertThatOptionalKind(zero).isEmpty();
      assertThat(narrowToOptional(zero)).isEqualTo(narrowToOptional(raised));
    }

    @Test
    @DisplayName("zero() can be recovered")
    void zeroCanBeRecovered() {
      Kind<OptionalKind.Witness, Integer> zero = Instances.monadZero(optional()).zero();
      var recovered = monadError.handleErrorWith(zero, _ -> monadError.of(0));
      assertThatOptionalKind(recovered).isPresent().contains(0);
    }

    @Test
    @DisplayName("filter keeps a present value matching the predicate")
    void filterKeepsMatchingPresent() {
      MonadZero<OptionalKind.Witness> mz = Instances.monadZero(optional());
      Kind<OptionalKind.Witness, Integer> input = presentOf(4);
      assertThatOptionalKind(mz.filter(x -> x % 2 == 0, input)).isPresent().contains(4);
    }

    @Test
    @DisplayName("filter drops a present value not matching the predicate")
    void filterDropsNonMatchingPresent() {
      MonadZero<OptionalKind.Witness> mz = Instances.monadZero(optional());
      Kind<OptionalKind.Witness, Integer> input = presentOf(3);
      assertThatOptionalKind(mz.filter(x -> x % 2 == 0, input)).isEmpty();
    }

    @Test
    @DisplayName("filter on empty stays empty")
    void filterOnEmptyStaysEmpty() {
      MonadZero<OptionalKind.Witness> mz = Instances.monadZero(optional());
      Kind<OptionalKind.Witness, Integer> empty = emptyOptional();
      assertThatOptionalKind(mz.filter(_ -> true, empty)).isEmpty();
    }

    @Test
    @DisplayName("filter rejects null arguments")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void filterRejectsNullArguments() {
      MonadZero<OptionalKind.Witness> mz = Instances.monadZero(optional());
      Kind<OptionalKind.Witness, Integer> input = presentOf(1);
      assertThatThrownBy(() -> mz.filter(null, input)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> mz.filter(_ -> true, null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Real-world scenario: validation pipeline")
    void validationPipeline() {
      Function<Integer, Optional<Integer>> validatePositive =
          i -> i > 0 ? Optional.of(i) : Optional.empty();
      Function<Integer, Optional<Integer>> validateRange =
          i -> i <= 100 ? Optional.of(i) : Optional.empty();
      Function<Integer, Optional<String>> format = i -> Optional.of("Valid: " + i);

      Optional<String> success = validatePositive.apply(50).flatMap(validateRange).flatMap(format);
      assertThat(success).contains("Valid: 50");

      Optional<String> failure = validatePositive.apply(-5).flatMap(validateRange).flatMap(format);
      assertThat(failure).isEmpty();
    }

    @Test
    @DisplayName("Real-world scenario: error recovery with fallback")
    void errorRecoveryWithFallback() {
      Function<String, Optional<String>> primarySource = _ -> Optional.empty();
      Function<String, Optional<String>> fallbackSource = _ -> Optional.of("fallback-data");

      String id = "user-123";
      Optional<String> result = primarySource.apply(id).or(() -> fallbackSource.apply(id));
      assertThat(result).contains("fallback-data");
    }
  }
}
