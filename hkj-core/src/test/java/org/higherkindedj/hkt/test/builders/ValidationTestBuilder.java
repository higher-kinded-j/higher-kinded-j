// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.builders;

import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.ThrowableAssert;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.assertions.FunctionAssertions;
import org.higherkindedj.hkt.test.assertions.KindAssertions;

/**
 * Fluent builder for testing multiple validation conditions.
 *
 * <p>Allows chaining multiple validation assertions and executing them all together, collecting
 * failures for comprehensive error reporting.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * ValidationTestBuilder.create()
 *     .assertMapperNull(() -> monad.map(null, validKind), "map")
 *     .assertKindNull(() -> monad.map(validMapper, null), "map")
 *     .assertFlatMapperNull(() -> monad.flatMap(null, validKind), "flatMap")
 *     .execute();
 * }</pre>
 */
public final class ValidationTestBuilder {

  private final List<ValidationAssertion> assertions = new ArrayList<>();

  private ValidationTestBuilder() {}

  public static ValidationTestBuilder create() {
    return new ValidationTestBuilder();
  }

  // Function validation methods

  public ValidationTestBuilder assertMapperNull(
      ThrowableAssert.ThrowingCallable executable, String operation) {
    assertions.add(() -> FunctionAssertions.assertMapperNull(executable, operation));
    return this;
  }

  public ValidationTestBuilder assertFlatMapperNull(
      ThrowableAssert.ThrowingCallable executable, String operation) {
    assertions.add(() -> FunctionAssertions.assertFlatMapperNull(executable, operation));
    return this;
  }

  public ValidationTestBuilder assertApplicativeNull(
      ThrowableAssert.ThrowingCallable executable, String operation) {
    assertions.add(() -> FunctionAssertions.assertApplicativeNull(executable, operation));
    return this;
  }

  public ValidationTestBuilder assertMonoidNull(
      ThrowableAssert.ThrowingCallable executable, String operation) {
    assertions.add(() -> FunctionAssertions.assertMonoidNull(executable, operation));
    return this;
  }

  public ValidationTestBuilder assertFunctionNull(
      ThrowableAssert.ThrowingCallable executable, String functionName, String operation) {
    assertions.add(
        () -> FunctionAssertions.assertFunctionNull(executable, functionName, operation));
    return this;
  }

  // Kind validation methods

  public ValidationTestBuilder assertKindNull(
      ThrowableAssert.ThrowingCallable executable, String operation) {
    assertions.add(() -> KindAssertions.assertKindNull(executable, operation));
    return this;
  }

  public ValidationTestBuilder assertNarrowNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> targetType) {
    assertions.add(() -> KindAssertions.assertNarrowNull(executable, targetType));
    return this;
  }

  public ValidationTestBuilder assertWidenNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> targetType) {
    assertions.add(() -> KindAssertions.assertWidenNull(executable, targetType));
    return this;
  }

  public ValidationTestBuilder assertInvalidKindType(
      ThrowableAssert.ThrowingCallable executable, Class<?> targetType, Kind<?, ?> invalidKind) {
    assertions.add(() -> KindAssertions.assertInvalidKindType(executable, targetType, invalidKind));
    return this;
  }

  /**
   * Execute all assertions.
   *
   * <p>Each assertion runs independently. All failures are collected and reported together to
   * provide complete validation feedback.
   */
  public void execute() {
    var failures = new ArrayList<AssertionError>();

    for (int i = 0; i < assertions.size(); i++) {
      try {
        assertions.get(i).run();
      } catch (AssertionError e) {
        failures.add(new AssertionError("Validation assertion " + (i + 1) + " failed", e));
      }
    }

    if (!failures.isEmpty()) {
      AssertionError combined =
          new AssertionError(failures.size() + " validation assertion(s) failed");
      for (AssertionError failure : failures) {
        combined.addSuppressed(failure);
      }
      throw combined;
    }
  }

  @FunctionalInterface
  private interface ValidationAssertion {
    void run();
  }
}
