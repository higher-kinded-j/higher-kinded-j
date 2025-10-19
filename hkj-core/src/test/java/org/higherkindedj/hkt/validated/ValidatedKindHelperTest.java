// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedKindHelper Complete Test Suite")
class ValidatedKindHelperTest {

  private Validated<String, Integer> validInstance;
  private Validated<String, Integer> invalidInstance;

  @BeforeEach
  void setUp() {
    validInstance = Validated.valid(42);
    invalidInstance = Validated.invalid("test-error");
  }

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete ValidatedKindHelper test pattern for Valid")
    void runCompleteValidatedKindHelperTestPatternForValid() {
      CoreTypeTest.validatedKindHelper(validInstance).test();
    }

    @Test
    @DisplayName("Run complete ValidatedKindHelper test pattern for Invalid")
    void runCompleteValidatedKindHelperTestPatternForInvalid() {
      CoreTypeTest.validatedKindHelper(invalidInstance).test();
    }
  }

  @Nested
  @DisplayName("Widen Operations")
  class WidenOperations {

    @Test
    @DisplayName("Widen converts Valid to Kind")
    void widenConvertsValidToKind() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(validInstance);

      assertThat(kind).isNotNull();
      assertThat(kind).isInstanceOf(ValidatedKind.class);
      assertThat(kind).isSameAs(validInstance);
    }

    @Test
    @DisplayName("Widen converts Invalid to Kind")
    void widenConvertsInvalidToKind() {
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
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(validInstance);
      Validated<String, Integer> narrowed = VALIDATED.narrow(kind);

      assertThat(narrowed).isNotNull();
      assertThat(narrowed).isSameAs(validInstance);
      assertThat(narrowed.isValid()).isTrue();
      assertThat(narrowed.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("Narrow converts Kind to Invalid")
    void narrowConvertsKindToInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(invalidInstance);
      Validated<String, Integer> narrowed = VALIDATED.narrow(kind);

      assertThat(narrowed).isNotNull();
      assertThat(narrowed).isSameAs(invalidInstance);
      assertThat(narrowed.isInvalid()).isTrue();
      assertThat(narrowed.getError()).isEqualTo("test-error");
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
      Kind<ValidatedKind.Witness<String>, Integer> widened = VALIDATED.widen(validInstance);
      Validated<String, Integer> narrowed = VALIDATED.narrow(widened);

      assertThat(narrowed).isSameAs(validInstance);
    }

    @Test
    @DisplayName("Round trip preserves Invalid identity")
    void roundTripPreservesInvalidIdentity() {
      Kind<ValidatedKind.Witness<String>, Integer> widened = VALIDATED.widen(invalidInstance);
      Validated<String, Integer> narrowed = VALIDATED.narrow(widened);

      assertThat(narrowed).isSameAs(invalidInstance);
    }

    @Test
    @DisplayName("Multiple round trips preserve identity")
    void multipleRoundTripsPreserveIdentity() {
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
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.valid(42);

      assertThat(kind).isNotNull();
      assertThat(kind).isInstanceOf(ValidatedKind.class);

      Validated<String, Integer> validated = VALIDATED.narrow(kind);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo(42);
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
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.invalid("error");

      assertThat(kind).isNotNull();
      assertThat(kind).isInstanceOf(ValidatedKind.class);

      Validated<String, Integer> validated = VALIDATED.narrow(kind);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error");
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
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(validInstance);
      Validated<String, Integer> narrowed = VALIDATED.narrow(kind);

      assertThat(narrowed).isInstanceOf(Valid.class);
      assertThat(narrowed.get()).isInstanceOf(Integer.class);
    }

    @Test
    @DisplayName("Widen and narrow preserve Invalid type information")
    void widenAndNarrowPreserveInvalidTypeInformation() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(invalidInstance);
      Validated<String, Integer> narrowed = VALIDATED.narrow(kind);

      assertThat(narrowed).isInstanceOf(Invalid.class);
      assertThat(narrowed.getError()).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("Kind representation preserves Valid toString")
    void kindRepresentationPreservesValidToString() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(validInstance);

      assertThat(kind.toString()).isEqualTo("Valid(42)");
    }

    @Test
    @DisplayName("Kind representation preserves Invalid toString")
    void kindRepresentationPreservesInvalidToString() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(invalidInstance);

      assertThat(kind.toString()).isEqualTo("Invalid(test-error)");
    }

    @Test
    @DisplayName("ValidatedKindHelper is a singleton enum")
    void validatedKindHelperIsASingletonEnum() {
      assertThat(VALIDATED).isSameAs(ValidatedKindHelper.VALIDATED);
      assertThat(ValidatedKindHelper.values()).containsExactly(VALIDATED);
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Widen operation is fast")
    void widenOperationIsFast() {
      int iterations = 10000;

      // Warm up
      for (int i = 0; i < 1000; i++) {
        VALIDATED.widen(validInstance);
      }

      long start = System.nanoTime();
      for (int i = 0; i < iterations; i++) {
        VALIDATED.widen(validInstance);
      }
      long duration = System.nanoTime() - start;

      double averageNanos = (double) duration / iterations;
      assertThat(averageNanos).as("Widen should be fast (< 1000ns average)").isLessThan(1000.0);
    }

    @Test
    @DisplayName("Narrow operation is fast")
    void narrowOperationIsFast() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(validInstance);
      int iterations = 10000;

      // Warm up
      for (int i = 0; i < 1000; i++) {
        VALIDATED.narrow(kind);
      }

      long start = System.nanoTime();
      for (int i = 0; i < iterations; i++) {
        VALIDATED.narrow(kind);
      }
      long duration = System.nanoTime() - start;

      double averageNanos = (double) duration / iterations;
      assertThat(averageNanos).as("Narrow should be fast (< 1000ns average)").isLessThan(1000.0);
    }
  }
}
