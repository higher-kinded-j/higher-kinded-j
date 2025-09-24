// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.util.validation.*;
import org.jspecify.annotations.Nullable;

/**
 * Improved error handling facade that provides both the new context-based API and maintains
 * backward compatibility with existing string-based methods.
 *
 * <p>The new API uses rich contexts while the legacy methods delegate to them for consistent error
 * messaging.
 */
public final class ImprovedErrorHandling {

  private ImprovedErrorHandling() {
    throw new AssertionError("ImprovedErrorHandling is a utility class");
  }

  /** Narrow Kind with rich type context - prevents misuse of type name */
  public static <F, A, T> T narrow(
      @Nullable Kind<F, A> kind, Class<T> targetType, Function<Kind<F, A>, T> narrower) {
    return KindValidator.narrow(kind, targetType, narrower);
  }

  /** Narrow Kind with type checking - type-safe version */
  public static <F, A, T> T narrowWithTypeCheck(@Nullable Kind<F, A> kind, Class<T> targetType) {
    return KindValidator.narrowWithTypeCheck(kind, targetType);
  }

  /** Validate input for widen operations - type-safe version */
  public static <T> T requireForWiden(T input, Class<T> inputType) {
    return KindValidator.requireForWiden(input, inputType);
  }

  /** Type-safe function validation for mappers */
  public static <T> T requireMapper(T function, String operation) {
    return FunctionValidator.requireMapper(function, operation);
  }

  /** Type-safe function validation for flatMappers */
  public static <T> T requireFlatMapper(T function, String operation) {
    return FunctionValidator.requireFlatMapper(function, operation);
  }

  /** Validate collections with rich context */
  public static <T extends Collection<?>> T requireNonEmptyCollection(
      T collection, String parameterName) {
    return CollectionValidator.requireNonEmpty(collection, parameterName);
  }

  /** Validate arrays with rich context */
  public static <T> T[] requireNonEmptyArray(T[] array, String parameterName) {
    return CollectionValidator.requireNonEmpty(array, parameterName);
  }

  /** Validate ranges with rich context */
  public static <T extends Comparable<T>> T requireInRange(
      T value, T min, T max, String parameterName) {
    return ConditionValidator.requireInRange(value, min, max, parameterName);
  }

  /** Validate outer monad for transformers */
  public static <F> Monad<F> requireOuterMonad(Monad<F> monad, String transformerName) {
    return DomainValidator.requireOuterMonad(monad, transformerName);
  }

  /** Validate matching witness types */
  public static <F, G> void requireMatchingWitness(
      Class<F> expected, Class<G> actual, String operation) {
    DomainValidator.requireMatchingWitness(expected, actual, operation);
  }

  /** Fluent validation builder for complex scenarios */
  public static class ValidationBuilder {

    public static ValidationBuilder create() {
      return new ValidationBuilder();
    }

    public <T> ValidationBuilder requireNonNull(T object, String parameterName) {
      Objects.requireNonNull(object, parameterName + " cannot be null");
      return this;
    }

    public ValidationBuilder requireCondition(boolean condition, String message, Object... args) {
      ConditionValidator.require(condition, message, args);
      return this;
    }

    public <T extends Comparable<T>> ValidationBuilder requireInRange(
        T value, T min, T max, String parameterName) {
      ConditionValidator.requireInRange(value, min, max, parameterName);
      return this;
    }

    public <T extends Collection<?>> ValidationBuilder requireNonEmpty(
        T collection, String parameterName) {
      CollectionValidator.requireNonEmpty(collection, parameterName);
      return this;
    }

    // Terminal operation
    public void execute() {
      // All validations are executed during the method calls
      // This is just a no-op terminal operation for fluent interface
    }
  }
}
