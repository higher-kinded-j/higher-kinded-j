// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import org.higherkindedj.hkt.test.api.CoreTypeTest;
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
    void runCompleteTestPatternWithInteger() {
      CoreTypeTest.idKindHelper(TEST_INSTANCE).test();
    }

    @Test
    @DisplayName("Run complete IdKindHelper test pattern with String value")
    void runCompleteTestPatternWithString() {
      CoreTypeTest.idKindHelper(TEST_INSTANCE_STRING).test();
    }

    @Test
    @DisplayName("Run complete IdKindHelper test pattern with null value")
    void runCompleteTestPatternWithNull() {
      CoreTypeTest.idKindHelper(TEST_INSTANCE_NULL).test();
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test round-trip widen/narrow only")
    void testRoundTripOnly() {
      CoreTypeTest.idKindHelper(TEST_INSTANCE)
          .skipValidations()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      CoreTypeTest.idKindHelper(TEST_INSTANCE)
          .skipRoundTrip()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test invalid type handling only")
    void testInvalidTypeOnly() {
      CoreTypeTest.idKindHelper(TEST_INSTANCE)
          .skipRoundTrip()
          .skipValidations()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test idempotency only")
    void testIdempotencyOnly() {
      CoreTypeTest.idKindHelper(TEST_INSTANCE)
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test edge cases only")
    void testEdgeCasesOnly() {
      CoreTypeTest.idKindHelper(TEST_INSTANCE)
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipIdempotency()
          .test();
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Test widen/narrow performance characteristics")
    void testPerformanceCharacteristics() {
      CoreTypeTest.idKindHelper(TEST_INSTANCE).skipValidations().withPerformanceTests().test();
    }

    @Test
    @DisplayName("Test concurrent access to widen/narrow operations")
    void testConcurrentAccess() {
      CoreTypeTest.idKindHelper(TEST_INSTANCE).skipValidations().withConcurrencyTests().test();
    }

    @Test
    @DisplayName("Test performance and concurrency together")
    void testPerformanceAndConcurrency() {
      CoreTypeTest.idKindHelper(TEST_INSTANCE)
          .skipValidations()
          .withPerformanceTests()
          .withConcurrencyTests()
          .test();
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("Test KindHelper with null value")
    void testWithNullValue() {
      CoreTypeTest.idKindHelper(TEST_INSTANCE_NULL).test();
    }

    @Test
    @DisplayName("Test KindHelper with different value types")
    void testWithDifferentValueTypes() {
      // Test with Integer
      CoreTypeTest.idKindHelper(Id.of(42)).test();

      // Test with String
      CoreTypeTest.idKindHelper(Id.of("test")).test();

      // Test with Boolean
      CoreTypeTest.idKindHelper(Id.of(true)).test();

      // Test with Double
      CoreTypeTest.idKindHelper(Id.of(3.14)).test();
    }

    @Test
    @DisplayName("Test multiple sequential widen/narrow operations")
    void testMultipleSequentialOperations() {
      CoreTypeTest.idKindHelper(TEST_INSTANCE).skipValidations().test();
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("Test null parameter validation for widen")
    void testWidenNullValidation() {
      CoreTypeTest.idKindHelper(TEST_INSTANCE)
          .skipRoundTrip()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test null parameter validation for narrow")
    void testNarrowNullValidation() {
      CoreTypeTest.idKindHelper(TEST_INSTANCE)
          .skipRoundTrip()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test invalid Kind type validation")
    void testInvalidKindTypeValidation() {
      CoreTypeTest.idKindHelper(TEST_INSTANCE)
          .skipRoundTrip()
          .skipValidations()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }
  }
}
