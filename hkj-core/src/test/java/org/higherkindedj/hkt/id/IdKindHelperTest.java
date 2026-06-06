// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import org.higherkindedj.hkt.test.api.KindHelperTests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for IdKindHelper implementation.
 *
 * <p>Tests the KindHelper operations (widen/narrow) for the Id type, ensuring correct conversion
 * between Id instances and their Kind representations.
 */
@DisplayName("IdKindHelper Complete Test Suite")
class IdKindHelperTest {

  private static final Id<Integer> TEST_INSTANCE = Id.of(42);
  private static final Id<String> TEST_INSTANCE_STRING = Id.of("test");
  private static final Id<Integer> TEST_INSTANCE_NULL = Id.of(null);

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete IdKindHelper test pattern with Integer value")
    void runCompleteTestWithInteger() {
      KindHelperTests.idKindHelper(TEST_INSTANCE).test();
    }

    @Test
    @DisplayName("Run complete IdKindHelper test pattern with String value")
    void runCompleteTestWithString() {
      KindHelperTests.idKindHelper(TEST_INSTANCE_STRING).test();
    }

    @Test
    @DisplayName("Run complete IdKindHelper test pattern with null value")
    void runCompleteTestWithNull() {
      KindHelperTests.idKindHelper(TEST_INSTANCE_NULL).test();
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test round-trip widen/narrow only")
    void testRoundTripOnly() {
      KindHelperTests.idKindHelper(TEST_INSTANCE)
          .skipValidations()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      KindHelperTests.idKindHelper(TEST_INSTANCE)
          .skipRoundTrip()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test invalid type handling only")
    void testInvalidTypeOnly() {
      KindHelperTests.idKindHelper(TEST_INSTANCE)
          .skipRoundTrip()
          .skipValidations()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test idempotency only")
    void testIdempotencyOnly() {
      KindHelperTests.idKindHelper(TEST_INSTANCE)
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test edge cases only")
    void testEdgeCasesOnly() {
      KindHelperTests.idKindHelper(TEST_INSTANCE)
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipIdempotency()
          .test();
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("Test KindHelper with null value")
    void testWithNullValue() {
      KindHelperTests.idKindHelper(TEST_INSTANCE_NULL).test();
    }

    @Test
    @DisplayName("Test KindHelper with different value types")
    void testWithDifferentValueTypes() {
      // Test with Integer
      KindHelperTests.idKindHelper(Id.of(42)).test();

      // Test with String
      KindHelperTests.idKindHelper(Id.of("test")).test();

      // Test with Boolean
      KindHelperTests.idKindHelper(Id.of(true)).test();

      // Test with Double
      KindHelperTests.idKindHelper(Id.of(3.14)).test();
    }

    @Test
    @DisplayName("Test multiple sequential widen/narrow operations")
    void testMultipleSequentialOperations() {
      KindHelperTests.idKindHelper(TEST_INSTANCE).skipValidations().test();
    }
  }
}
