// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.test.api.KindHelperTests.optionalKindHelper;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.optional.OptionalKindHelper.OptionalHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OptionalKindHelper Complete Test Suite")
class OptionalKindHelperTest extends OptionalTestBase {

  @Nested
  @DisplayName("Complete KindHelper Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete KindHelper test suite for Optional")
    void completeKindHelperTestSuite() {
      optionalKindHelper(Optional.of("Success")).test();
    }

    @Test
    @DisplayName("Complete test suite with present and empty Optionals")
    void completeTestSuiteWithMultipleStates() {
      List<Optional<String>> instances =
          List.of(Optional.of("Success"), Optional.empty(), Optional.of(""), Optional.of("Test"));
      for (Optional<String> instance : instances) {
        optionalKindHelper(instance).test();
      }
    }
  }

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponentTests {

    @Test
    @DisplayName("Test round-trip widen/narrow operations")
    void testRoundTripOperations() {
      optionalKindHelper(Optional.of("test"))
          .skipValidations()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test null parameter validations")
    void testNullParameterValidations() {
      optionalKindHelper(Optional.of("test"))
          .skipRoundTrip()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test invalid Kind type handling")
    void testInvalidKindType() {
      optionalKindHelper(Optional.of("test"))
          .skipRoundTrip()
          .skipValidations()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test idempotency of operations")
    void testIdempotency() {
      optionalKindHelper(Optional.of("idempotent"))
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test edge cases and boundary conditions")
    void testEdgeCases() {
      optionalKindHelper(Optional.of("edge"))
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipIdempotency()
          .test();
    }
  }

  @Nested
  @DisplayName("Specific Optional Behaviour Tests")
  class SpecificBehaviourTests {

    @Test
    @DisplayName("Both present and empty Optionals round-trip correctly")
    void bothPresentAndEmptyRoundTrip() {
      optionalKindHelper(Optional.of("Success")).test();
      optionalKindHelper(Optional.<String>empty()).test();
    }

    @Test
    @DisplayName("OptionalHolder constructor rejects a null Optional")
    @SuppressWarnings("DataFlowIssue") // deliberately constructing a holder with null to verify
    void holderConstructorRejectsNullOptional() {
      assertThatNullPointerException()
          .isThrownBy(() -> new OptionalHolder<>(null))
          .withMessageContaining("Input Optional cannot be null for widen");
    }
  }
}
