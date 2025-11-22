// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.validated.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedKindHelper Complete Test Suite")
class ValidatedKindHelperTest extends ValidatedTestBase {

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete ValidatedKindHelper test pattern for Valid")
    void runCompleteValidatedKindHelperTestPatternForValid() {
      Validated<String, Integer> validInstance = Validated.valid(DEFAULT_VALID_VALUE);
      CoreTypeTest.validatedKindHelper(validInstance).test();
    }

    @Test
    @DisplayName("Run complete ValidatedKindHelper test pattern for Invalid")
    void runCompleteValidatedKindHelperTestPatternForInvalid() {
      Validated<String, Integer> invalidInstance = Validated.invalid(DEFAULT_ERROR);
      CoreTypeTest.validatedKindHelper(invalidInstance).test();
    }
  }

  @Nested
  @DisplayName("Widen Operations")
  class WidenOperations {

    @Test
    @DisplayName("Widen converts Valid to Kind")
    void widenConvertsValidToKind() {
      Validated<String, Integer> validInstance = Validated.valid(DEFAULT_VALID_VALUE);
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(validInstance);

      assertThat(kind).isNotNull();
      assertThat(kind).isInstanceOf(ValidatedKind.class);
      assertThat(kind).isSameAs(validInstance);
    }

    @Test
    @DisplayName("Widen converts Invalid to Kind")
    void widenConvertsInvalidToKind() {
      Validated<String, Integer> invalidInstance = Validated.invalid(DEFAULT_ERROR);
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(invalidInstance);

      assertThat(kind).isNotNull();
      assertThat(kind).isInstanceOf(ValidatedKind.class);
      assertThat(kind).isSameAs(invalidInstance);
    }

    @Test
    @DisplayName("Widen rejects null Validated")
    void widenRejectsNullValidated() {
      assertThatThrownBy(() -> VALIDATED.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Validated")
          .hasMessageContaining("widen")
          .hasMessageContaining("cannot be null");
    }

    @Test
    @DisplayName("Widen is idempotent")
    void widenIsIdempotent() {
      Validated<String, Integer> validInstance = Validated.valid(DEFAULT_VALID_VALUE);
      Kind<ValidatedKind.Witness<String>, Integer> kind1 = VALIDATED.widen(validInstance);
      Kind<ValidatedKind.Witness<String>, Integer> kind2 = VALIDATED.widen(validInstance);

      assertThat(kind1).isSameAs(kind2);
      assertThat(kind1).isSameAs(validInstance);
    }
  }

  @Nested
  @DisplayName("Narrow Operations")
  class NarrowOperations {

    @Test
    @DisplayName("Narrow converts Kind to Valid")
    void narrowConvertsKindToValid() {
      Validated<String, Integer> validInstance = Validated.valid(DEFAULT_VALID_VALUE);
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(validInstance);
      Validated<String, Integer> narrowed = VALIDATED.narrow(kind);

      assertThat(narrowed).isNotNull();
      assertThat(narrowed).isSameAs(validInstance);
      assertThatValidated(narrowed).isValid().hasValue(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("Narrow converts Kind to Invalid")
    void narrowConvertsKindToInvalid() {
      Validated<String, Integer> invalidInstance = Validated.invalid(DEFAULT_ERROR);
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(invalidInstance);
      Validated<String, Integer> narrowed = VALIDATED.narrow(kind);

      assertThat(narrowed).isNotNull();
      assertThat(narrowed).isSameAs(invalidInstance);
      assertThatValidated(narrowed).isInvalid().hasError(DEFAULT_ERROR);
    }

    @Test
    @DisplayName("Narrow rejects null Kind")
    void narrowRejectsNullKind() {
      assertThatThrownBy(() -> VALIDATED.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Cannot narrow null Kind for Validated");
    }

    @Test
    @DisplayName("Narrow rejects invalid Kind type")
    void narrowRejectsInvalidKindType() {
      Kind<ValidatedKind.Witness<String>, Integer> invalidKind =
          new Kind<ValidatedKind.Witness<String>, Integer>() {
            @Override
            public String toString() {
              return "InvalidKind";
            }
          };

      assertThatThrownBy(() -> VALIDATED.narrow(invalidKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Validated");
    }

    @Test
    @DisplayName("Narrow is idempotent")
    void narrowIsIdempotent() {
      Validated<String, Integer> validInstance = Validated.valid(DEFAULT_VALID_VALUE);
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(validInstance);
      Validated<String, Integer> narrowed1 = VALIDATED.narrow(kind);
      Validated<String, Integer> narrowed2 = VALIDATED.narrow(kind);

      assertThat(narrowed1).isSameAs(narrowed2);
      assertThat(narrowed1).isSameAs(validInstance);
    }
  }

  @Nested
  @DisplayName("Round Trip Operations")
  class RoundTripOperations {

    @Test
    @DisplayName("Round trip preserves Valid identity")
    void roundTripPreservesValidIdentity() {
      Validated<String, Integer> validInstance = Validated.valid(DEFAULT_VALID_VALUE);
      Kind<ValidatedKind.Witness<String>, Integer> widened = VALIDATED.widen(validInstance);
      Validated<String, Integer> narrowed = VALIDATED.narrow(widened);

      assertThat(narrowed).isSameAs(validInstance);
    }

    @Test
    @DisplayName("Round trip preserves Invalid identity")
    void roundTripPreservesInvalidIdentity() {
      Validated<String, Integer> invalidInstance = Validated.invalid(DEFAULT_ERROR);
      Kind<ValidatedKind.Witness<String>, Integer> widened = VALIDATED.widen(invalidInstance);
      Validated<String, Integer> narrowed = VALIDATED.narrow(widened);

      assertThat(narrowed).isSameAs(invalidInstance);
    }

    @Test
    @DisplayName("Multiple round trips preserve identity")
    void multipleRoundTripsPreserveIdentity() {
      Validated<String, Integer> validInstance = Validated.valid(DEFAULT_VALID_VALUE);
      Validated<String, Integer> current = validInstance;

      for (int i = 0; i < 3; i++) {
        Kind<ValidatedKind.Witness<String>, Integer> widened = VALIDATED.widen(current);
        current = VALIDATED.narrow(widened);
      }

      assertThat(current).isSameAs(validInstance);
    }
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    @DisplayName("Valid factory creates Valid Kind")
    void validFactoryCreatesValidKind() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.valid(DEFAULT_VALID_VALUE);

      assertThat(kind).isNotNull();
      assertThat(kind).isInstanceOf(ValidatedKind.class);

      Validated<String, Integer> validated = VALIDATED.narrow(kind);
      assertThatValidated(validated).isValid().hasValue(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("Valid factory rejects null value")
    void validFactoryRejectsNullValue() {
      assertThatThrownBy(() -> VALIDATED.<String, Integer>valid(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("value")
          .hasMessageContaining("Validated")
          .hasMessageContaining("construction");
    }

    @Test
    @DisplayName("Invalid factory creates Invalid Kind")
    void invalidFactoryCreatesInvalidKind() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.invalid(DEFAULT_ERROR);

      assertThat(kind).isNotNull();
      assertThat(kind).isInstanceOf(ValidatedKind.class);

      Validated<String, Integer> validated = VALIDATED.narrow(kind);
      assertThatValidated(validated).isInvalid().hasError(DEFAULT_ERROR);
    }

    @Test
    @DisplayName("Invalid factory rejects null error")
    void invalidFactoryRejectsNullError() {
      assertThatThrownBy(() -> VALIDATED.<String, Integer>invalid(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("error")
          .hasMessageContaining("Validated");
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Widen and narrow preserve Valid type information")
    void widenAndNarrowPreserveValidTypeInformation() {
      Validated<String, Integer> validInstance = Validated.valid(DEFAULT_VALID_VALUE);
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(validInstance);
      Validated<String, Integer> narrowed = VALIDATED.narrow(kind);

      assertThat(narrowed).isInstanceOf(Valid.class);
      assertThat(narrowed.get()).isInstanceOf(Integer.class);
    }

    @Test
    @DisplayName("Widen and narrow preserve Invalid type information")
    void widenAndNarrowPreserveInvalidTypeInformation() {
      Validated<String, Integer> invalidInstance = Validated.invalid(DEFAULT_ERROR);
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(invalidInstance);
      Validated<String, Integer> narrowed = VALIDATED.narrow(kind);

      assertThat(narrowed).isInstanceOf(Invalid.class);
      assertThat(narrowed.getError()).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("Kind representation preserves Valid toString")
    void kindRepresentationPreservesValidToString() {
      Validated<String, Integer> validInstance = Validated.valid(DEFAULT_VALID_VALUE);
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(validInstance);

      assertThat(kind.toString()).isEqualTo("Valid(42)");
    }

    @Test
    @DisplayName("Kind representation preserves Invalid toString")
    void kindRepresentationPreservesInvalidToString() {
      Validated<String, Integer> invalidInstance = Validated.invalid(DEFAULT_ERROR);
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(invalidInstance);

      assertThat(kind.toString()).isEqualTo("Invalid(error)");
    }

    @Test
    @DisplayName("ValidatedKindHelper is a singleton enum")
    void validatedKindHelperIsASingletonEnum() {
      assertThat(VALIDATED).isSameAs(ValidatedKindHelper.VALIDATED);
      assertThat(ValidatedKindHelper.values()).containsExactly(VALIDATED);
    }
  }

  @Nested
  @DisplayName("narrow2() Method Specific Tests")
  class Narrow2MethodTests {

    @Test
    @DisplayName("narrow2() successfully unwraps valid Kind2 for Valid")
    void narrow2UnwrapsValidKind2ForValid() {
      Validated<String, Integer> original = Validated.valid(42);
      Kind2<ValidatedKind2.Witness, String, Integer> kind2 = VALIDATED.widen2(original);

      Validated<String, Integer> result = VALIDATED.narrow2(kind2);

      assertThat(result).isEqualTo(original);
      assertThatValidated(result).isValid().hasValue(42);
    }

    @Test
    @DisplayName("narrow2() successfully unwraps valid Kind2 for Invalid")
    void narrow2UnwrapsValidKind2ForInvalid() {
      Validated<String, Integer> original = Validated.invalid("error message");
      Kind2<ValidatedKind2.Witness, String, Integer> kind2 = VALIDATED.widen2(original);

      Validated<String, Integer> result = VALIDATED.narrow2(kind2);

      assertThat(result).isEqualTo(original);
      assertThatValidated(result).isInvalid().hasError("error message");
    }

    @Test
    @DisplayName("narrow2() throws KindUnwrapException when Kind2 is null")
    void narrow2ThrowsWhenKind2Null() {
      assertThatThrownBy(() -> VALIDATED.narrow2(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Cannot narrow null Kind2 for Validated");
    }

    @Test
    @DisplayName("narrow2() throws KindUnwrapException for wrong Kind2 type")
    void narrow2ThrowsWhenWrongKind2Type() {
      // Create a Kind2 that is NOT a Validated
      Kind2<ValidatedKind2.Witness, String, Integer> wrongKind =
          new Kind2<ValidatedKind2.Witness, String, Integer>() {};

      assertThatThrownBy(() -> VALIDATED.narrow2(wrongKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind2 instance cannot be narrowed to Validated")
          .hasMessageContaining("received:");
    }

    @Test
    @DisplayName("narrow2() round-trip preserves Valid")
    void narrow2RoundTripPreservesValid() {
      Validated<String, Integer> original = Validated.valid(100);

      Validated<String, Integer> result = VALIDATED.narrow2(VALIDATED.widen2(original));

      assertThat(result).isEqualTo(original);
      assertThatValidated(result).isValid().hasValue(100);
    }

    @Test
    @DisplayName("narrow2() round-trip preserves Invalid")
    void narrow2RoundTripPreservesInvalid() {
      Validated<String, Integer> original = Validated.invalid("test error");

      Validated<String, Integer> result = VALIDATED.narrow2(VALIDATED.widen2(original));

      assertThat(result).isEqualTo(original);
      assertThatValidated(result).isInvalid().hasError("test error");
    }

    @Test
    @DisplayName("narrow2() works with different type parameters")
    void narrow2WorksWithDifferentTypes() {
      Validated<List<String>, Integer> original = Validated.invalid(List.of("error1", "error2"));
      Kind2<ValidatedKind2.Witness, List<String>, Integer> kind2 = VALIDATED.widen2(original);

      Validated<List<String>, Integer> result = VALIDATED.narrow2(kind2);

      assertThat(result).isEqualTo(original);
      assertThatValidated(result).isInvalid();
      assertThat(result.getError()).containsExactly("error1", "error2");
    }

    @Test
    @DisplayName("narrow2() works with complex nested types")
    void narrow2WorksWithComplexNestedTypes() {
      Validated<String, List<Integer>> original = Validated.valid(List.of(1, 2, 3));
      Kind2<ValidatedKind2.Witness, String, List<Integer>> kind2 = VALIDATED.widen2(original);

      Validated<String, List<Integer>> result = VALIDATED.narrow2(kind2);

      assertThat(result).isEqualTo(original);
      assertThatValidated(result).isValid();
      assertThat(result.get()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("narrow2() multiple operations create independent results")
    void narrow2MultipleOperationsCreateIndependentResults() {
      Validated<String, Integer> valid1 = Validated.valid(10);
      Validated<String, Integer> valid2 = Validated.valid(20);

      Kind2<ValidatedKind2.Witness, String, Integer> kind1 = VALIDATED.widen2(valid1);
      Kind2<ValidatedKind2.Witness, String, Integer> kind2 = VALIDATED.widen2(valid2);

      Validated<String, Integer> result1 = VALIDATED.narrow2(kind1);
      Validated<String, Integer> result2 = VALIDATED.narrow2(kind2);

      assertThat(result1).isNotSameAs(result2);
      assertThatValidated(result1).isValid().hasValue(10);
      assertThatValidated(result2).isValid().hasValue(20);
    }

    @Test
    @DisplayName("narrow2() is idempotent - multiple narrows of same Kind2")
    void narrow2IsIdempotent() {
      Validated<String, Integer> original = Validated.valid(42);
      Kind2<ValidatedKind2.Witness, String, Integer> kind2 = VALIDATED.widen2(original);

      Validated<String, Integer> result1 = VALIDATED.narrow2(kind2);
      Validated<String, Integer> result2 = VALIDATED.narrow2(kind2);
      Validated<String, Integer> result3 = VALIDATED.narrow2(kind2);

      assertThat(result1).isEqualTo(original);
      assertThat(result2).isEqualTo(original);
      assertThat(result3).isEqualTo(original);
      assertThat(result1).isEqualTo(result2).isEqualTo(result3);
    }

    @Test
    @DisplayName("narrow2() error message includes actual type received")
    void narrow2ErrorMessageIncludesActualType() {
      Kind2<ValidatedKind2.Witness, String, Integer> wrongKind =
          new Kind2<ValidatedKind2.Witness, String, Integer>() {};

      assertThatThrownBy(() -> VALIDATED.narrow2(wrongKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind2 instance cannot be narrowed to Validated")
          .hasMessageContaining("received:");
    }

    @Test
    @DisplayName("narrow2() preserves Valid instance identity")
    void narrow2PreservesValidInstanceIdentity() {
      Validated<String, Integer> original = Validated.valid(99);
      Kind2<ValidatedKind2.Witness, String, Integer> kind2 = VALIDATED.widen2(original);

      Validated<String, Integer> result = VALIDATED.narrow2(kind2);

      assertThat(result).isSameAs(original);
      assertThat(result).isInstanceOf(Valid.class);
    }

    @Test
    @DisplayName("narrow2() preserves Invalid instance identity")
    void narrow2PreservesInvalidInstanceIdentity() {
      Validated<String, Integer> original = Validated.invalid("failure");
      Kind2<ValidatedKind2.Witness, String, Integer> kind2 = VALIDATED.widen2(original);

      Validated<String, Integer> result = VALIDATED.narrow2(kind2);

      assertThat(result).isSameAs(original);
      assertThat(result).isInstanceOf(Invalid.class);
    }

    @Test
    @DisplayName("narrow2() works with complex error types")
    void narrow2WorksWithComplexErrorTypes() {
      record ErrorDetails(String code, int severity, String message) {}

      List<ErrorDetails> errors =
          List.of(
              new ErrorDetails("E001", 5, "Critical error"),
              new ErrorDetails("E002", 3, "Warning"));
      Validated<List<ErrorDetails>, Integer> original = Validated.invalid(errors);
      Kind2<ValidatedKind2.Witness, List<ErrorDetails>, Integer> kind2 =
          ValidatedKindHelper.VALIDATED.widen2(original);

      Validated<List<ErrorDetails>, Integer> result = ValidatedKindHelper.VALIDATED.narrow2(kind2);

      assertThat(result).isEqualTo(original);
      assertThatValidated(result).isInvalid();
      assertThat(result.getError()).hasSize(2);
      assertThat(result.getError().get(0).code()).isEqualTo("E001");
      assertThat(result.getError().get(1).code()).isEqualTo("E002");
    }

    @Test
    @DisplayName("narrow2() works with both Valid and Invalid in sequence")
    void narrow2WorksWithBothValidAndInvalidInSequence() {
      Validated<String, Integer> valid = Validated.valid(42);
      Validated<String, Integer> invalid = Validated.invalid("error");

      Kind2<ValidatedKind2.Witness, String, Integer> validKind2 = VALIDATED.widen2(valid);
      Kind2<ValidatedKind2.Witness, String, Integer> invalidKind2 = VALIDATED.widen2(invalid);

      Validated<String, Integer> validResult = VALIDATED.narrow2(validKind2);
      Validated<String, Integer> invalidResult = VALIDATED.narrow2(invalidKind2);

      assertThatValidated(validResult).isValid().hasValue(42);
      assertThatValidated(invalidResult).isInvalid().hasError("error");
    }
  }
}
