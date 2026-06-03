// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("ValidatedMonad")
class ValidatedMonadTest extends ValidatedTestBase {

  private MonadError<ValidatedKind.Witness<String>, String> monad;

  @BeforeEach
  void setUpMonad() {
    // Accumulating semigroup so ap still combines errors even though flatMap short-circuits.
    monad = Instances.validated(createDefaultSemigroup());
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.validated.ValidatedLawFixtures#values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(monad, value, testFunction, equalityChecker);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.validated.ValidatedLawFixtures#kinds")
    void rightIdentity(String label, Kind<ValidatedKind.Witness<String>, Integer> ma) {
      MonadLaws.assertRightIdentity(monad, ma, equalityChecker);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.validated.ValidatedLawFixtures#kinds")
    void associativity(String label, Kind<ValidatedKind.Witness<String>, Integer> ma) {
      MonadLaws.assertAssociativity(monad, ma, testFunction, chainFunction, equalityChecker);
    }
  }

  @Test
  @DisplayName("Monad contract — operations, validations & exceptions (laws verified above)")
  void monadContract() {
    TypeClassContract.<ValidatedKind.Witness<String>>monad(ValidatedMonad.class)
        .<Integer>instance(monad)
        .<String>withKind(validKind)
        .withMonadOperations(
            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("FlatMap chains Valid computations")
    void flatMapChainsValidComputations() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = validKind(DEFAULT_VALID_VALUE);
      Function<Integer, Kind<ValidatedKind.Witness<String>, String>> fn =
          n -> VALIDATED.widen(Validated.valid("Value: " + n));

      Kind<ValidatedKind.Witness<String>, String> result = monad.flatMap(fn, kind);
      assertThatValidated(result).isValid().hasValue("Value: 42");
    }

    @Test
    @DisplayName("FlatMap propagates Invalid from source")
    void flatMapPropagatesInvalidFromSource() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = invalidKind("source-error");
      Function<Integer, Kind<ValidatedKind.Witness<String>, String>> fn =
          n -> VALIDATED.widen(Validated.valid("Value: " + n));

      Kind<ValidatedKind.Witness<String>, String> result = monad.flatMap(fn, kind);
      assertThatValidated(result).isInvalid().hasError("source-error");
    }

    @Test
    @DisplayName("FlatMap uses result from function on Valid")
    void flatMapUsesResultFromFunctionOnValid() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = validKind(DEFAULT_VALID_VALUE);
      Function<Integer, Kind<ValidatedKind.Witness<String>, String>> fn =
          _ -> VALIDATED.widen(Validated.invalid("function-error"));

      Kind<ValidatedKind.Witness<String>, String> result = monad.flatMap(fn, kind);
      assertThatValidated(result).isInvalid().hasError("function-error");
    }

    @Test
    @DisplayName("FlatMap chains multiple operations")
    void flatMapChainsMultipleOperations() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = validKind(10);

      Kind<ValidatedKind.Witness<String>, Integer> step1 =
          monad.flatMap(n -> validKind(n * 2), kind);
      Kind<ValidatedKind.Witness<String>, Integer> step2 =
          monad.flatMap(n -> validKind(n + 5), step1);
      Kind<ValidatedKind.Witness<String>, String> step3 =
          monad.flatMap(n -> VALIDATED.widen(Validated.valid("Result: " + n)), step2);
      assertThatValidated(step3).isValid().hasValue("Result: 25");
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("FlatMap with function returning null Kind fails appropriately")
    @SuppressWarnings("DataFlowIssue") // function deliberately returns null to verify rejection
    void flatMapWithFunctionReturningNullKindFailsAppropriately() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = validKind(DEFAULT_VALID_VALUE);
      Function<Integer, Kind<ValidatedKind.Witness<String>, String>> nullReturningFn = _ -> null;

      assertThatThrownBy(() -> monad.flatMap(nullReturningFn, kind))
          .isInstanceOf(KindUnwrapException.class);
    }

    @Test
    @DisplayName("Ap still accumulates errors despite flatMap fail-fast behaviour")
    void apStillAccumulatesErrorsDespiteFlatMapFailFastBehaviour() {
      Kind<ValidatedKind.Witness<String>, Function<Integer, String>> fnKind =
          VALIDATED.widen(Validated.invalid("error1"));
      Kind<ValidatedKind.Witness<String>, Integer> valueKind = invalidKind("error2");

      Kind<ValidatedKind.Witness<String>, String> result = monad.ap(fnKind, valueKind);
      assertThatValidated(result).isInvalid().hasError("error1, error2");
    }
  }
}
