// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import java.util.function.Function;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for Id core type implementation.
 *
 * <p>Tests all Id-specific operations including factory methods, map, flatMap, value access,
 * validation, and edge cases.
 */
@DisplayName("Id Complete Test Suite")
class IdTest {

  private static final Id<Integer> TEST_INSTANCE = Id.of(42);
  private static final Id<String> TEST_INSTANCE_STRING = Id.of("test");
  private static final Id<Integer> TEST_INSTANCE_NULL = Id.of(null);
  private static final Function<Integer, String> INT_TO_STRING = Object::toString;
  private static final Function<String, Integer> STRING_LENGTH = String::length;

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Id test pattern with Integer value")
    void runCompleteTestPatternWithInteger() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Id test pattern with String value")
    void runCompleteTestPatternWithString() {
      CoreTypeTest.<String>id(Id.class)
          .withInstance(TEST_INSTANCE_STRING)
          .withMappers(STRING_LENGTH)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Id test pattern with null value")
    void runCompleteTestPatternWithNull() {
      Function<Integer, String> nullSafeMapper = i -> i == null ? "null" : i.toString();

      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE_NULL)
          .withMappers(nullSafeMapper)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Id test pattern without mappers")
    void runCompleteTestPatternWithoutMappers() {
      CoreTypeTest.<Integer>id(Id.class).withInstance(TEST_INSTANCE).withoutMappers().testAll();
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("Test factory methods (of)")
    void testFactoryMethods() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .onlyFactoryMethods()
          .testAll();
    }

    @Test
    @DisplayName("Test value accessor")
    void testValueAccessor() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .onlyGetters()
          .testAll();
    }

    @Test
    @DisplayName("Test map operation")
    void testMapOperation() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .onlyMap()
          .testAll();
    }

    @Test
    @DisplayName("Test flatMap operation")
    void testFlatMapOperation() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .onlyFlatMap()
          .testAll();
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only (no validations or edge cases)")
    void testOperationsOnly() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .testValidations();
    }

    @Test
    @DisplayName("Test edge cases only")
    void testEdgeCasesOnly() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .testEdgeCases();
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("Test Id with null value")
    void testWithNullValue() {
      Function<Integer, String> nullSafeMapper = i -> i == null ? "null" : i.toString();

      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE_NULL)
          .withMappers(nullSafeMapper)
          .testAll();
    }

    @Test
    @DisplayName("Test Id.of with null creates proper instance")
    void testOfWithNull() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(Id.of(null))
          .withMappers(INT_TO_STRING)
          .onlyFactoryMethods()
          .testAll();
    }

    @Test
    @DisplayName("Test toString representation")
    void testToStringRepresentation() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .onlyEdgeCases()
          .testAll();
    }

    @Test
    @DisplayName("Test equals and hashCode")
    void testEqualsAndHashCode() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .onlyEdgeCases()
          .testAll();
    }

    @Test
    @DisplayName("Test with different value types")
    void testWithDifferentValueTypes() {
      // Integer
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(Id.of(42))
          .withMappers(INT_TO_STRING)
          .testAll();

      // String
      CoreTypeTest.<String>id(Id.class)
          .withInstance(Id.of("test"))
          .withMappers(STRING_LENGTH)
          .testAll();

      // Boolean
      CoreTypeTest.<Boolean>id(Id.class)
          .withInstance(Id.of(true))
          .withMappers(Object::toString)
          .testAll();

      // Double
      CoreTypeTest.<Double>id(Id.class)
          .withInstance(Id.of(3.14))
          .withMappers(Object::toString)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("Test map null function validation")
    void testMapNullFunctionValidation() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .onlyValidations()
          .testAll();
    }

    @Test
    @DisplayName("Test flatMap null function validation")
    void testFlatMapNullFunctionValidation() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .onlyValidations()
          .testAll();
    }

    @Test
    @DisplayName("Test validation with inheritance contexts")
    void testValidationWithInheritanceContexts() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(IdMonad.class)
          .withFlatMapFrom(IdMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test validation with default contexts")
    void testValidationWithDefaultContexts() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .configureValidation()
          .useDefaultValidation()
          .testValidations();
    }
  }

  @Nested
  @DisplayName("Selective Testing")
  class SelectiveTesting {

    @Test
    @DisplayName("Skip factory methods")
    void skipFactoryMethods() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .skipFactoryMethods()
          .testAll();
    }

    @Test
    @DisplayName("Skip getters")
    void skipGetters() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .skipGetters()
          .testAll();
    }

    @Test
    @DisplayName("Skip map operations")
    void skipMapOperations() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .skipMap()
          .testAll();
    }

    @Test
    @DisplayName("Skip flatMap operations")
    void skipFlatMapOperations() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .skipFlatMap()
          .testAll();
    }

    @Test
    @DisplayName("Skip validations")
    void skipValidations() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .skipValidations()
          .testAll();
    }

    @Test
    @DisplayName("Skip edge cases")
    void skipEdgeCases() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .skipEdgeCases()
          .testAll();
    }
  }

  @Nested
  @DisplayName("Exception Propagation")
  class ExceptionPropagation {

    @Test
    @DisplayName("Test map propagates exceptions from mapper function")
    void testMapPropagatesExceptions() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .onlyMap()
          .testAll();
    }

    @Test
    @DisplayName("Test flatMap propagates exceptions from flatMapper function")
    void testFlatMapPropagatesExceptions() {
      CoreTypeTest.<Integer>id(Id.class)
          .withInstance(TEST_INSTANCE)
          .withMappers(INT_TO_STRING)
          .onlyFlatMap()
          .testAll();
    }
  }
}
