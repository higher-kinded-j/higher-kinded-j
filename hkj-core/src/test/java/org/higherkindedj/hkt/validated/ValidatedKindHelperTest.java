// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.test.api.KindHelperTests.validatedKindHelper;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedKindHelper Complete Test Suite")
class ValidatedKindHelperTest extends ValidatedTestBase {

  @Nested
  @DisplayName("Complete KindHelper Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete KindHelper test suite for Valid and Invalid")
    void completeKindHelperTestSuite() {
      validatedKindHelper(Validated.<String, Integer>valid(DEFAULT_VALID_VALUE)).test();
      validatedKindHelper(Validated.<String, Integer>invalid(DEFAULT_ERROR)).test();
    }
  }

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponentTests {

    @Test
    @DisplayName("Test round-trip widen/narrow operations")
    void testRoundTripOperations() {
      validatedKindHelper(Validated.<String, Integer>valid(DEFAULT_VALID_VALUE))
          .skipValidations()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test null parameter validations")
    void testNullParameterValidations() {
      validatedKindHelper(Validated.<String, Integer>valid(DEFAULT_VALID_VALUE))
          .skipRoundTrip()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test invalid Kind type handling")
    void testInvalidKindType() {
      validatedKindHelper(Validated.<String, Integer>valid(DEFAULT_VALID_VALUE))
          .skipRoundTrip()
          .skipValidations()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test idempotency of operations")
    void testIdempotency() {
      validatedKindHelper(Validated.<String, Integer>valid(DEFAULT_VALID_VALUE))
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test edge cases and boundary conditions")
    void testEdgeCases() {
      validatedKindHelper(Validated.<String, Integer>valid(DEFAULT_VALID_VALUE))
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipIdempotency()
          .test();
    }
  }

  @Nested
  @DisplayName("Specific Validated Behaviour Tests")
  class SpecificBehaviourTests {

    @Test
    @DisplayName("Both Valid and Invalid instances work correctly")
    void testValidAndInvalidInstances() {
      validatedKindHelper(Validated.<String, Integer>valid(DEFAULT_VALID_VALUE)).test();
      validatedKindHelper(Validated.<String, Integer>invalid(DEFAULT_ERROR)).test();
    }

    @Test
    @DisplayName("Factory methods create the expected Kind")
    void factoryMethodsCreateExpectedKind() {
      assertThatValidated(VALIDATED.<String, Integer>valid(DEFAULT_VALID_VALUE))
          .isValid()
          .hasValue(DEFAULT_VALID_VALUE);
      assertThatValidated(VALIDATED.<String, Integer>invalid(DEFAULT_ERROR))
          .isInvalid()
          .hasError(DEFAULT_ERROR);
    }

    @Test
    @DisplayName("Valid factory rejects null value")
    @SuppressWarnings("DataFlowIssue") // deliberately passing null to verify rejection
    void validFactoryRejectsNullValue() {
      assertThatThrownBy(() -> VALIDATED.<String, Integer>valid(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("value")
          .hasMessageContaining("Validated")
          .hasMessageContaining("construction");
    }

    @Test
    @DisplayName("Invalid factory rejects null error")
    @SuppressWarnings("DataFlowIssue") // deliberately passing null to verify rejection
    void invalidFactoryRejectsNullError() {
      assertThatThrownBy(() -> VALIDATED.<String, Integer>invalid(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("error")
          .hasMessageContaining("Validated");
    }

    @Test
    @DisplayName("Kind representation preserves Validated toString")
    void kindRepresentationPreservesToString() {
      assertThat(VALIDATED.<String, Integer>valid(42).toString()).isEqualTo("Valid(42)");
      assertThat(VALIDATED.<String, Integer>invalid(DEFAULT_ERROR).toString())
          .isEqualTo("Invalid(error)");
    }

    @Test
    @DisplayName("ValidatedKindHelper is a singleton enum")
    void validatedKindHelperIsASingletonEnum() {
      assertThat(ValidatedKindHelper.values()).containsExactly(VALIDATED);
    }
  }

  @Nested
  @DisplayName("narrow2() Method Specific Tests")
  class Narrow2MethodTests {

    /** A {@link Kind2} that is not a {@link Validated}; exercises {@code narrow2}'s type guard. */
    private record NotAValidated<L, R>() implements Kind2<ValidatedKind2.Witness, L, R> {}

    @Test
    @DisplayName("narrow2() successfully unwraps valid Kind2 for Valid")
    void narrow2UnwrapsValidKind2ForValid() {
      Validated<String, Integer> original = Validated.valid(42);
      Kind2<ValidatedKind2.Witness, String, Integer> kind2 = VALIDATED.widen2(original);

      Validated<String, Integer> result = VALIDATED.narrow2(kind2);
      assertThat(result).isSameAs(original);
      assertThatValidated(result).isValid().hasValue(42);
    }

    @Test
    @DisplayName("narrow2() successfully unwraps valid Kind2 for Invalid")
    void narrow2UnwrapsValidKind2ForInvalid() {
      Validated<String, Integer> original = Validated.invalid("error message");
      Kind2<ValidatedKind2.Witness, String, Integer> kind2 = VALIDATED.widen2(original);

      Validated<String, Integer> result = VALIDATED.narrow2(kind2);
      assertThat(result).isSameAs(original);
      assertThatValidated(result).isInvalid().hasError("error message");
    }

    @Test
    @DisplayName("narrow2() throws KindUnwrapException when Kind2 is null")
    @SuppressWarnings("DataFlowIssue") // deliberately passing null to verify rejection
    void narrow2ThrowsWhenKind2Null() {
      assertThatThrownBy(() -> VALIDATED.narrow2(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Cannot narrow null Kind2 for Validated");
    }

    @Test
    @DisplayName("narrow2() throws KindUnwrapException for wrong Kind2 type")
    void narrow2ThrowsWhenWrongKind2Type() {
      Kind2<ValidatedKind2.Witness, String, Integer> wrongKind = new NotAValidated<>();

      assertThatThrownBy(() -> VALIDATED.narrow2(wrongKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind2 instance cannot be narrowed to Validated")
          .hasMessageContaining("received:");
    }

    @Test
    @DisplayName("narrow2() works with different error types")
    void narrow2WorksWithDifferentTypes() {
      Validated<List<String>, Integer> original = Validated.invalid(List.of("error1", "error2"));
      Kind2<ValidatedKind2.Witness, List<String>, Integer> kind2 = VALIDATED.widen2(original);

      Validated<List<String>, Integer> result = VALIDATED.narrow2(kind2);
      assertThatValidated(result).isInvalid();
      assertThat(result.getError()).containsExactly("error1", "error2");
    }

    @Test
    @DisplayName("narrow2() works with complex nested types")
    void narrow2WorksWithComplexNestedTypes() {
      Validated<String, List<Integer>> original = Validated.valid(List.of(1, 2, 3));
      Kind2<ValidatedKind2.Witness, String, List<Integer>> kind2 = VALIDATED.widen2(original);

      Validated<String, List<Integer>> result = VALIDATED.narrow2(kind2);
      assertThatValidated(result).isValid();
      assertThat(result.get()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("narrow2() works with complex error record types")
    void narrow2WorksWithComplexErrorTypes() {
      record ErrorDetails(String code, int severity, String message) {}

      List<ErrorDetails> errors =
          List.of(
              new ErrorDetails("E001", 5, "Critical error"),
              new ErrorDetails("E002", 3, "Warning"));
      Validated<List<ErrorDetails>, Integer> original = Validated.invalid(errors);
      Kind2<ValidatedKind2.Witness, List<ErrorDetails>, Integer> kind2 = VALIDATED.widen2(original);

      Validated<List<ErrorDetails>, Integer> result = VALIDATED.narrow2(kind2);
      assertThatValidated(result).isInvalid();
      assertThat(result.getError()).hasSize(2);
      assertThat(result.getError().get(0).code()).isEqualTo("E001");
      assertThat(result.getError().get(1).code()).isEqualTo("E002");
    }
  }
}
