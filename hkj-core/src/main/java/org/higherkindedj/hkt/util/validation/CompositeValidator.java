// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Handles complex multi-step validations that don't fit into other categories.
 *
 * <p>This validator provides utilities for batch validation and complex validation scenarios that
 * involve multiple different types of checks.
 */
public final class CompositeValidator {

  private CompositeValidator() {
    throw new AssertionError(
        "CompositeValidator is a utility class and should not be instantiated");
  }

  /**
   * Executes multiple validation steps and collects all failure messages.
   *
   * @param validations The validation steps to execute
   * @throws IllegalArgumentException with combined error messages if any validation fails
   */
  public static void validateAll(ValidationStep... validations) {
    List<String> errors = new ArrayList<>();

    for (int i = 0; i < validations.length; i++) {
      try {
        if (validations[i] != null) {
          validations[i].validate();
        }
      } catch (Exception e) {
        errors.add("Validation " + (i + 1) + ": " + e.getMessage());
      }
    }

    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(
          "Multiple validation failures: " + String.join("; ", errors));
    }
  }

  /**
   * Lazy evaluation wrapper for expensive error message construction.
   *
   * @param condition The condition to check
   * @param messageSupplier Supplier that produces the error message only when needed
   * @throws IllegalArgumentException with lazily-evaluated message if condition is false
   */
  public static void requireLazy(boolean condition, Supplier<String> messageSupplier) {
    if (!condition) {
      throw new IllegalArgumentException(messageSupplier.get());
    }
  }

  /**
   * Validates that all elements in a collection pass a predicate.
   *
   * @param collection The collection to validate
   * @param predicate The predicate each element must satisfy
   * @param parameterName The parameter name for error messaging
   * @param <T> The element type
   * @return The validated collection
   * @throws IllegalArgumentException if any element fails the predicate
   */
  public static <T> Collection<T> requireAllMatch(
      Collection<T> collection, java.util.function.Predicate<T> predicate, String parameterName) {

    Objects.requireNonNull(collection, parameterName + " cannot be null");
    Objects.requireNonNull(predicate, "predicate cannot be null");

    for (T element : collection) {
      if (!predicate.test(element)) {
        throw new IllegalArgumentException(
            String.format(
                "All elements in %s must satisfy the predicate, but found: %s",
                parameterName, element));
      }
    }

    return collection;
  }

  /**
   * Validates that at least one element in a collection passes a predicate.
   *
   * @param collection The collection to validate
   * @param predicate The predicate at least one element must satisfy
   * @param parameterName The parameter name for error messaging
   * @param <T> The element type
   * @return The validated collection
   * @throws IllegalArgumentException if no element passes the predicate
   */
  public static <T> Collection<T> requireAnyMatch(
      Collection<T> collection, java.util.function.Predicate<T> predicate, String parameterName) {

    Objects.requireNonNull(collection, parameterName + " cannot be null");
    Objects.requireNonNull(predicate, "predicate cannot be null");

    for (T element : collection) {
      if (predicate.test(element)) {
        return collection;
      }
    }

    throw new IllegalArgumentException(
        String.format("At least one element in %s must satisfy the predicate", parameterName));
  }

  /** Represents a single validation step that can throw an exception. */
  @FunctionalInterface
  public interface ValidationStep {
    static ValidationStep requireNonNull(Object obj, String message) {
      return () -> Objects.requireNonNull(obj, message);
    }

    static ValidationStep requireCondition(boolean condition, String message) {
      return () -> ConditionValidator.require(condition, message);
    }

    static ValidationStep custom(Runnable validation) {
      return validation::run;
    }

    void validate() throws Exception;
  }

  /** Fluent validation builder */
  public static class ValidationChain<T> {
    private final T value;
    private final List<String> errors = new ArrayList<>();

    private ValidationChain(T value) {
      this.value = value;
    }

    public static <T> ValidationChain<T> validate(T value) {
      return new ValidationChain<>(value);
    }

    public ValidationChain<T> nonNull(String parameterName) {
      if (value == null) {
        errors.add(parameterName + " cannot be null");
      }
      return this;
    }

    public ValidationChain<T> matches(Predicate<T> predicate, String message) {
      if (value != null && !predicate.test(value)) {
        errors.add(message);
      }
      return this;
    }

    public T orThrow() {
      if (!errors.isEmpty()) {
        throw new IllegalArgumentException("Validation failed: " + String.join("; ", errors));
      }
      return value;
    }
  }
}
