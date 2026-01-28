// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.typeclass;

import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dynamic test factory for Monoid laws using JUnit 6's @TestFactory.
 *
 * <p>This class demonstrates how to use {@code @TestFactory} to generate tests dynamically at
 * runtime, providing comprehensive law testing across all monoid implementations with minimal
 * boilerplate.
 *
 * <p>Benefits of @TestFactory approach:
 *
 * <ul>
 *   <li>Tests are generated at runtime based on actual monoid implementations
 *   <li>Adding new monoids automatically adds test coverage
 *   <li>Clear, structured test output showing which monoid/law combination passed/failed
 *   <li>Each test runs independently with proper isolation
 * </ul>
 */
@DisplayName("Monoid Laws - Dynamic Test Factory")
class MonoidLawsTestFactory {

  /**
   * Test data record containing all information needed to test a monoid.
   *
   * @param <T> the type parameter of the monoid
   */
  record MonoidTestData<T>(
      String name,
      Monoid<T> monoid,
      T testValue1,
      T testValue2,
      T testValue3,
      T expectedAssociativeResult) {

    static <T> MonoidTestData<T> of(
        String name, Monoid<T> monoid, T v1, T v2, T v3, T expectedAssociativeResult) {
      return new MonoidTestData<>(name, monoid, v1, v2, v3, expectedAssociativeResult);
    }
  }

  /**
   * Provides test data for all monoid implementations.
   *
   * <p>This is a centralized source of test data. Adding a new monoid implementation requires only
   * adding one line here, and all law tests will automatically cover it.
   */
  private static Stream<MonoidTestData<?>> allMonoids() {
    return Stream.of(
        MonoidTestData.of("longAddition", Monoids.longAddition(), 100L, 200L, 300L, 600L),
        MonoidTestData.of("longMultiplication", Monoids.longMultiplication(), 2L, 3L, 4L, 24L),
        MonoidTestData.of("doubleAddition", Monoids.doubleAddition(), 1.5, 2.5, 3.5, 7.5),
        MonoidTestData.of(
            "doubleMultiplication", Monoids.doubleMultiplication(), 2.0, 3.0, 4.0, 24.0),
        MonoidTestData.of(
            "firstOptional",
            Monoids.firstOptional(),
            Optional.of("first"),
            Optional.of("second"),
            Optional.of("third"),
            Optional.of("first")),
        MonoidTestData.of(
            "lastOptional",
            Monoids.lastOptional(),
            Optional.of("first"),
            Optional.of("second"),
            Optional.of("third"),
            Optional.of("third")),
        MonoidTestData.of(
            "maximum",
            Monoids.maximum(),
            Optional.of(5),
            Optional.of(10),
            Optional.of(3),
            Optional.of(10)),
        MonoidTestData.of(
            "minimum",
            Monoids.minimum(),
            Optional.of(5),
            Optional.of(10),
            Optional.of(3),
            Optional.of(3)));
  }

  /**
   * Dynamically generates tests for the left identity law: {@code combine(empty, x) == x}
   *
   * <p>This test factory creates one test per monoid implementation, each verifying that combining
   * the empty element with any value returns that value unchanged.
   */
  @TestFactory
  @DisplayName("Left Identity Law: combine(empty, x) = x")
  Stream<DynamicTest> leftIdentityLaw() {
    return allMonoids()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies left identity",
                    () -> {
                      Monoid<Object> monoid = (Monoid<Object>) data.monoid();
                      Object testValue = data.testValue1();

                      Object result = monoid.combine(monoid.empty(), testValue);

                      assertThat(result)
                          .as(
                              "Left identity law failed for %s: combine(empty, %s)",
                              data.name(), testValue)
                          .isEqualTo(testValue);
                    }));
  }

  /**
   * Dynamically generates tests for the right identity law: {@code combine(x, empty) == x}
   *
   * <p>This test factory creates one test per monoid implementation, each verifying that combining
   * any value with the empty element returns that value unchanged.
   */
  @TestFactory
  @DisplayName("Right Identity Law: combine(x, empty) = x")
  Stream<DynamicTest> rightIdentityLaw() {
    return allMonoids()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies right identity",
                    () -> {
                      Monoid<Object> monoid = (Monoid<Object>) data.monoid();
                      Object testValue = data.testValue1();

                      Object result = monoid.combine(testValue, monoid.empty());

                      assertThat(result)
                          .as(
                              "Right identity law failed for %s: combine(%s, empty)",
                              data.name(), testValue)
                          .isEqualTo(testValue);
                    }));
  }

  /**
   * Dynamically generates tests for the associativity law: {@code combine(combine(a, b), c) ==
   * combine(a, combine(b, c))}
   *
   * <p>This test factory creates one test per monoid implementation, each verifying that the order
   * of combining operations doesn't affect the result.
   */
  @TestFactory
  @DisplayName("Associativity Law: combine(combine(a, b), c) = combine(a, combine(b, c))")
  Stream<DynamicTest> associativityLaw() {
    return allMonoids()
        .flatMap(
            data -> {
              // Only test associativity for monoids where we have expected results
              if (data.expectedAssociativeResult() == null) {
                return Stream.empty();
              }

              return Stream.of(
                  DynamicTest.dynamicTest(
                      data.name() + " satisfies associativity",
                      () -> {
                        Monoid<Object> monoid = (Monoid<Object>) data.monoid();
                        Object a = data.testValue1();
                        Object b = data.testValue2();
                        Object c = data.testValue3();

                        Object leftAssoc = monoid.combine(monoid.combine(a, b), c);
                        Object rightAssoc = monoid.combine(a, monoid.combine(b, c));

                        assertThat(leftAssoc)
                            .as(
                                "Associativity law failed for %s: left=%s, right=%s",
                                data.name(), leftAssoc, rightAssoc)
                            .isEqualTo(rightAssoc)
                            .isEqualTo(data.expectedAssociativeResult());
                      }));
            });
  }

  /**
   * Dynamically generates tests verifying that each monoid's empty element is truly an identity.
   *
   * <p>This is a meta-test that verifies the empty element behaves correctly with multiple test
   * values.
   */
  @TestFactory
  @DisplayName("Empty element is a true identity for multiple values")
  Stream<DynamicTest> emptyIsIdentityWithMultipleValues() {
    return allMonoids()
        .flatMap(
            data -> {
              Monoid<Object> monoid = (Monoid<Object>) data.monoid();
              List<Object> testValues =
                  List.of(data.testValue1(), data.testValue2(), data.testValue3());

              return testValues.stream()
                  .map(
                      value ->
                          DynamicTest.dynamicTest(
                              String.format("%s: empty is identity for %s", data.name(), value),
                              () -> {
                                Object leftResult = monoid.combine(monoid.empty(), value);
                                Object rightResult = monoid.combine(value, monoid.empty());

                                assertThat(leftResult)
                                    .as("Left identity failed for value: %s", value)
                                    .isEqualTo(value);
                                assertThat(rightResult)
                                    .as("Right identity failed for value: %s", value)
                                    .isEqualTo(value);
                              }));
            });
  }

  /**
   * Dynamically generates tests for the idempotence property of specific monoids (max, min, first,
   * last).
   *
   * <p>This demonstrates conditional test generation - only certain monoids are tested for this
   * property.
   */
  @TestFactory
  @DisplayName("Idempotent monoids: combine(x, x) = x for max/min/first/last")
  Stream<DynamicTest> idempotenceProperty() {
    return allMonoids()
        .filter(
            data ->
                data.name().equals("maximum")
                    || data.name().equals("minimum")
                    || data.name().equals("firstOptional")
                    || data.name().equals("lastOptional"))
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " is idempotent: combine(x, x) = x",
                    () -> {
                      Monoid<Object> monoid = (Monoid<Object>) data.monoid();
                      Object testValue = data.testValue1();

                      Object result = monoid.combine(testValue, testValue);

                      assertThat(result)
                          .as("Idempotence failed for %s", data.name())
                          .isEqualTo(testValue);
                    }));
  }
}
