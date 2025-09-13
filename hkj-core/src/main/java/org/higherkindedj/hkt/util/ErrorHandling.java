// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Centralized error handling utility for Higher-Kinded-J operations.
 *
 * <p>This utility provides standardized error handling patterns across the library, particularly
 * for Kind unwrapping operations, null validation, and error message consistency. It helps reduce
 * code duplication and ensures consistent error reporting throughout the library.
 *
 * <p>The utility follows these design principles:
 *
 * <ul>
 *   <li>Fail-fast with clear, descriptive error messages
 *   <li>Consistent exception types for similar error conditions
 *   <li>Null-safe operations with appropriate annotations
 *   <li>Lazy error message evaluation for performance
 * </ul>
 */
public final class ErrorHandling {

  // Prevent instantiation
  private ErrorHandling() {
    throw new AssertionError("ErrorHandling is a utility class and should not be instantiated");
  }

  // =============================================================================
  // Standard Error Messages
  // =============================================================================

  /** Standard error message template for null Kind instances. */
  public static final String NULL_KIND_TEMPLATE = "Cannot narrow null Kind for %s";

  /** Standard error message template for unexpected Kind types. */
  public static final String INVALID_KIND_TYPE_TEMPLATE = "Kind instance is not a %s: %s";

  /** Standard error message template for null inputs to widen operations. */
  public static final String NULL_WIDEN_INPUT_TEMPLATE = "Input %s cannot be null for widen";

  /** Standard error message template for null holder states. */
  public static final String NULL_HOLDER_STATE_TEMPLATE = "%s contained null %s instance";

  /** Standard error message for null functions. */
  public static final String NULL_FUNCTION_MSG = "%s cannot be null";

  /** Standard error message for null Kind arguments. */
  public static final String NULL_KIND_ARG_MSG = "Kind argument cannot be null";

  // =============================================================================
  // Kind Unwrapping Utilities
  // =============================================================================

  /**
   * Safely narrows a Kind to a specific type with standardized error handling.
   *
   * <p>This method provides a standard pattern for Kind unwrapping operations throughout the
   * library. It performs null checks and type validation with consistent error messages.
   *
   * @param <F> The witness type of the Kind
   * @param <A> The value type of the Kind
   * @param <T> The target type to narrow to
   * @param kind The Kind instance to narrow, may be null
   * @param targetTypeName The name of the target type for error messages
   * @param narrower A function that performs the actual narrowing operation
   * @return The narrowed instance
   * @throws KindUnwrapException if kind is null or narrowing fails
   */
  public static <F, A, T> T narrowKind(
      @Nullable Kind<F, A> kind, String targetTypeName, Function<Kind<F, A>, T> narrower) {

    if (kind == null) {
      throw new KindUnwrapException(String.format(NULL_KIND_TEMPLATE, targetTypeName));
    }

    try {
      return narrower.apply(kind);
    } catch (ClassCastException e) {
      throw new KindUnwrapException(
          String.format(INVALID_KIND_TYPE_TEMPLATE, targetTypeName, kind.getClass().getName()), e);
    }
  }

  /**
   * Safely narrows a Kind using instanceof checks with standardized error handling.
   *
   * <p>This method is particularly useful for narrowing operations that need to verify the concrete
   * type of the Kind instance before casting.
   *
   * @param <F> The witness type of the Kind
   * @param <A> The value type of the Kind
   * @param <T> The target type to narrow to
   * @param kind The Kind instance to narrow, may be null
   * @param targetType The Class of the target type
   * @param targetTypeName The name of the target type for error messages
   * @return The narrowed instance
   * @throws KindUnwrapException if kind is null or not of the expected type
   */
  @SuppressWarnings("unchecked")
  public static <F, A, T> T narrowKindWithTypeCheck(
      @Nullable Kind<F, A> kind, Class<T> targetType, String targetTypeName) {

    if (kind == null) {
      throw new KindUnwrapException(String.format(NULL_KIND_TEMPLATE, targetTypeName));
    }

    if (!targetType.isInstance(kind)) {
      throw new KindUnwrapException(
          String.format(INVALID_KIND_TYPE_TEMPLATE, targetTypeName, kind.getClass().getName()));
    }

    return (T) kind;
  }

  /**
   * Pattern matching utility for Kind narrowing with multiple possible types.
   *
   * <p>This method provides a more flexible approach to Kind narrowing when multiple concrete types
   * might be valid.
   *
   * @param <F> The witness type of the Kind
   * @param <A> The value type of the Kind
   * @param <T> The target type to narrow to
   * @param kind The Kind instance to narrow, may be null
   * @param targetTypeName The name of the target type for error messages
   * @param matchers Array of type matchers to try in order
   * @return The narrowed instance
   * @throws KindUnwrapException if kind is null or no matcher succeeds
   */
  @SafeVarargs
  public static <F, A, T> T narrowKindWithMatchers(
      @Nullable Kind<F, A> kind, String targetTypeName, TypeMatcher<Kind<F, A>, T>... matchers) {

    if (kind == null) {
      throw new KindUnwrapException(String.format(NULL_KIND_TEMPLATE, targetTypeName));
    }

    for (TypeMatcher<Kind<F, A>, T> matcher : matchers) {
      if (matcher.matches(kind)) {
        return matcher.extract(kind);
      }
    }

    throw new KindUnwrapException(
        String.format(INVALID_KIND_TYPE_TEMPLATE, targetTypeName, kind.getClass().getName()));
  }

  /**
   * Interface for type matching in Kind narrowing operations.
   *
   * <p>This interface defines a pattern matcher that can check if a source instance matches certain
   * criteria and extract a target type from it.
   *
   * @param <S> The source type to match against
   * @param <T> The target type to extract
   */
  public interface TypeMatcher<S, T> {
    /**
     * Checks if the given source instance matches this matcher's criteria.
     *
     * @param source The source instance to check
     * @return true if this matcher can handle the source
     */
    boolean matches(S source);

    /**
     * Extracts the target type from the source instance.
     *
     * <p>This method should only be called if {@link #matches(Object)} returns true.
     *
     * @param source The source instance to extract from
     * @return The extracted target instance
     */
    T extract(S source);
  }

  /** Utility class for creating common TypeMatcher instances. */
  public static final class TypeMatchers {
    private TypeMatchers() {
      throw new AssertionError("TypeMatchers is a utility class and should not be instantiated");
    }

    /**
     * Creates a type matcher for a specific class.
     *
     * @param <S> The source type
     * @param <T> The target type
     * @param targetClass The class to match against
     * @param extractor Function to extract the target from a matched source
     * @return A new type matcher
     */
    public static <S, T> TypeMatcher<S, T> forClass(
        Class<? extends T> targetClass, java.util.function.Function<S, T> extractor) {
      return new TypeMatcher<S, T>() {
        @Override
        public boolean matches(S source) {
          return targetClass.isInstance(source);
        }

        @Override
        public T extract(S source) {
          return extractor.apply(source);
        }
      };
    }

    /**
     * Creates a type matcher that matches any instance of a specific class and casts it to the
     * target type.
     *
     * @param <S> The source type
     * @param <T> The target type
     * @param targetClass The class to match against
     * @return A new type matcher that performs a safe cast
     */
    @SuppressWarnings("unchecked")
    public static <S, T> TypeMatcher<S, T> forClassWithCast(Class<? extends T> targetClass) {
      return new TypeMatcher<S, T>() {
        @Override
        public boolean matches(S source) {
          return targetClass.isInstance(source);
        }

        @Override
        public T extract(S source) {
          return (T) source;
        }
      };
    }
  }

  // =============================================================================
  // Null Validation Utilities
  // =============================================================================

  /**
   * Validates that an input to a widen operation is not null.
   *
   * @param <T> The type of the input
   * @param input The input to validate
   * @param typeName The name of the type for error messages
   * @return The validated input (guaranteed non-null)
   * @throws NullPointerException if input is null
   */
  public static <T> T requireNonNullForWiden(T input, String typeName) {
    return Objects.requireNonNull(input, NULL_WIDEN_INPUT_TEMPLATE.formatted(typeName));
  }

  /**
   * Validates that an input to a holder is not null.
   *
   * @param <T> The type of the input
   * @param input The input to validate
   * @param typeName The name of the type for error messages
   * @return The validated input (guaranteed non-null)
   * @throws NullPointerException if input is null
   */
  public static <T> T requireNonNullForHolder(T input, String typeName) {
    return Objects.requireNonNull(
        input, NULL_HOLDER_STATE_TEMPLATE.formatted(typeName + "Holder", typeName));
  }

  /**
   * Validates that a function parameter is not null.
   *
   * @param <T> The type of the function
   * @param function The function to validate
   * @return The validated function (guaranteed non-null)
   * @throws NullPointerException if function is null
   */
  public static <T> T requireNonNullFunction(T function) {
    return Objects.requireNonNull(function, NULL_FUNCTION_MSG.formatted("Function"));
  }

  /**
   * Validates that a function parameter is not null with a custom message.
   *
   * @param <T> The type of the function
   * @param function The function to validate
   * @param parameterName The name of the parameter for error messages
   * @return The validated function (guaranteed non-null)
   * @throws NullPointerException if function is null
   */
  public static <T> T requireNonNullFunction(T function, String parameterName) {
    return Objects.requireNonNull(function, NULL_FUNCTION_MSG.formatted(parameterName));
  }

  /**
   * Validates that a Kind argument is not null.
   *
   * @param <F> The witness type of the Kind
   * @param <A> The value type of the Kind
   * @param kind The Kind to validate
   * @return The validated Kind (guaranteed non-null)
   * @throws NullPointerException if kind is null
   */
  public static <F, A> Kind<F, A> requireNonNullKind(Kind<F, A> kind) {
    return Objects.requireNonNull(kind, NULL_KIND_ARG_MSG);
  }

  /**
   * Validates that a Kind argument is not null with a custom message.
   *
   * @param <F> The witness type of the Kind
   * @param <A> The value type of the Kind
   * @param kind The Kind to validate
   * @param parameterName The name of the parameter for error messages
   * @return The validated Kind (guaranteed non-null)
   * @throws NullPointerException if kind is null
   */
  public static <F, A> Kind<F, A> requireNonNullKind(Kind<F, A> kind, String parameterName) {
    return Objects.requireNonNull(kind, parameterName + " cannot be null");
  }

  /** Validates that a collection parameter is not null or empty. */
  public static <T extends Collection<?>> T requireNonEmptyCollection(
      T collection, String parameterName) {
    Objects.requireNonNull(collection, parameterName + " cannot be null");
    if (collection.isEmpty()) {
      throw new IllegalArgumentException(parameterName + " cannot be empty");
    }
    return collection;
  }

  /** Validates that an array parameter is not null or empty. */
  public static <T> T[] requireNonEmptyArray(T[] array, String parameterName) {
    Objects.requireNonNull(array, parameterName + " cannot be null");
    if (array.length == 0) {
      throw new IllegalArgumentException(parameterName + " cannot be empty");
    }
    return array;
  }

  /** Validates a condition with a standardized error message. */
  public static void requireCondition(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }

  /** Validates that a numeric parameter is within bounds. */
  public static <T extends Comparable<T>> T requireInRange(
      T value, T min, T max, String parameterName) {
    Objects.requireNonNull(value, parameterName + " cannot be null");
    if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
      throw new IllegalArgumentException(
          String.format("%s must be between %s and %s, got %s", parameterName, min, max, value));
    }
    return value;
  }

  // =============================================================================
  // Lazy Error Message Utilities
  // =============================================================================

  /**
   * Creates a lazy error message supplier for expensive-to-compute error descriptions.
   *
   * <p>This is useful when error message construction is expensive and should only be performed if
   * an error actually occurs.
   *
   * @param template The message template with placeholders
   * @param args The arguments to substitute into the template
   * @return A supplier that produces the formatted message when called
   */
  public static Supplier<String> lazyMessage(String template, Object... args) {
    return () -> String.format(template, args);
  }

  /**
   * Throws a KindUnwrapException with a lazily-evaluated message.
   *
   * @param messageSupplier Supplier for the error message
   * @throws KindUnwrapException always
   */
  public static void throwKindUnwrapException(Supplier<String> messageSupplier) {
    throw new KindUnwrapException(messageSupplier.get());
  }

  /**
   * Throws a KindUnwrapException with a lazily-evaluated message and cause.
   *
   * @param messageSupplier Supplier for the error message
   * @param cause The underlying cause of the exception
   * @throws KindUnwrapException always
   */
  public static void throwKindUnwrapException(Supplier<String> messageSupplier, Throwable cause) {
    throw new KindUnwrapException(messageSupplier.get(), cause);
  }

  // =============================================================================
  // Validation Combinators
  // =============================================================================

  /**
   * Validates multiple conditions and collects error messages.
   *
   * <p>This utility allows for validation of multiple conditions simultaneously, collecting all
   * error messages before throwing a single exception.
   *
   * @param validations Array of validation functions to execute
   * @throws IllegalArgumentException if any validation fails
   */
  public static void validateAll(Validation... validations) {
    var errors = new java.util.ArrayList<String>();

    for (Validation validation : validations) {
      try {
        validation.validate();
      } catch (Exception e) {
        errors.add(e.getMessage());
      }
    }

    if (!errors.isEmpty()) {
      throw new IllegalArgumentException("Validation failed: " + String.join("; ", errors));
    }
  }

  /** Functional interface for validation operations. */
  @FunctionalInterface
  public interface Validation {
    /**
     * Performs the validation.
     *
     * @throws Exception if validation fails
     */
    void validate() throws Exception;

    /**
     * Creates a validation that checks a condition.
     *
     * @param condition The condition to check
     * @param errorMessage The error message if the condition is false
     * @return A new validation
     */
    static Validation require(boolean condition, String errorMessage) {
      return () -> {
        if (!condition) {
          throw new IllegalArgumentException(errorMessage);
        }
      };
    }

    /**
     * Creates a validation that checks for null.
     *
     * @param object The object to check
     * @param errorMessage The error message if the object is null
     * @return A new validation
     */
    static Validation requireNonNull(Object object, String errorMessage) {
      return () -> Objects.requireNonNull(object, errorMessage);
    }
  }

  // =============================================================================
  // Exception Chain Utilities
  // =============================================================================

  /**
   * Wraps an exception with additional context while preserving the original stack trace.
   *
   * @param <T> The type of exception to wrap
   * @param originalException The original exception
   * @param contextMessage Additional context message
   * @param wrapperConstructor Constructor for the wrapper exception
   * @return The wrapped exception
   */
  public static <T extends Exception> T wrapWithContext(
      Exception originalException,
      String contextMessage,
      java.util.function.BiFunction<String, Throwable, T> wrapperConstructor) {

    String combinedMessage = contextMessage + ": " + originalException.getMessage();
    return wrapperConstructor.apply(combinedMessage, originalException);
  }

  /**
   * Creates a KindUnwrapException with additional context.
   *
   * @param originalException The original exception
   * @param contextMessage Additional context message
   * @return The wrapped KindUnwrapException
   */
  public static KindUnwrapException wrapAsKindUnwrapException(
      Exception originalException, String contextMessage) {

    return wrapWithContext(originalException, contextMessage, KindUnwrapException::new);
  }

  // =======================================
  // Domain specific helpers
  // ======================================

  /** Validates that an outer monad is not null for transformer construction. */
  public static <F> Monad<F> requireValidOuterMonad(Monad<F> outerMonad, String transformerName) {
    return Objects.requireNonNull(outerMonad, "Outer Monad cannot be null for " + transformerName);
  }

  /** Validates that a witness type matches expected type for Kind operations. */
  public static <F, G> void requireMatchingWitness(
      Class<F> expected, Class<G> actual, String operation) {
    if (!expected.equals(actual)) {
      throw new IllegalArgumentException(
          String.format(
              "Witness type mismatch in %s: expected %s, got %s",
              operation, expected.getSimpleName(), actual.getSimpleName()));
    }
  }
}
