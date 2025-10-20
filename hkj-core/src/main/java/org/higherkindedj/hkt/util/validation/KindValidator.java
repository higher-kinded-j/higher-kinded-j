// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Handles Kind-specific validations with rich context.
 *
 * <p>This validator provides type-safe Kind operations that prevent common errors like passing
 * descriptions instead of type names, and ensures consistent error messaging across all
 * Kind-related operations.
 */
public enum KindValidator {
  KIND_VALIDATOR;

  /**
   * Validates and narrows a Kind with rich type context using a custom narrower function.
   *
   * @param kind The Kind to narrow, may be null
   * @param targetType The target type class for type-safe error messaging
   * @param narrower Function to perform the actual narrowing
   * @param <F> The witness type of the Kind
   * @param <A> The value type of the Kind
   * @param <T> The target type to narrow to
   * @return The narrowed result
   * @throws KindUnwrapException if kind is null or narrowing fails
   */
  public <F, A, T> T narrow(
      @Nullable Kind<F, A> kind, Class<T> targetType, Function<Kind<F, A>, T> narrower) {

    var context = KindContext.narrow(targetType);

    if (kind == null) {
      throw new KindUnwrapException(context.nullParameterMessage());
    }

    try {
      return narrower.apply(kind);
    } catch (ClassCastException e) {
      throw new KindUnwrapException(context.invalidTypeMessage(kind.getClass().getName()), e);
    } catch (Exception e) {
      throw new KindUnwrapException(
          "Failed to narrow Kind to %s: %s".formatted(targetType.getSimpleName(), e.getMessage()),
          e);
    }
  }

  /**
   * Validates and narrows using instanceof type checking. This is the preferred method when you
   * don't need custom narrowing logic.
   *
   * @param kind The Kind to narrow, may be null
   * @param targetType The target type class
   * @param <F> The witness type of the Kind
   * @param <A> The value type of the Kind
   * @param <T> The target type to narrow to
   * @return The narrowed result
   * @throws KindUnwrapException if kind is null or not of the expected type
   */
  public <F, A, T> T narrowWithTypeCheck(@Nullable Kind<F, A> kind, Class<T> targetType) {

    var context = KindContext.narrow(targetType);

    if (kind == null) {
      throw new KindUnwrapException(context.nullParameterMessage());
    }

    if (!targetType.isInstance(kind)) {
      throw new KindUnwrapException(context.invalidTypeMessage(kind.getClass().getName()));
    }

    return targetType.cast(kind);
  }

  /**
   * Validates input for widen operations with type-safe context.
   *
   * @param input The input to validate for widening
   * @param inputType The class of the input type for context
   * @param <T> The input type
   * @return The validated input
   * @throws NullPointerException if input is null
   */
  public <T> T requireForWiden(T input, Class<T> inputType) {
    var context = KindContext.widen(inputType);
    return Objects.requireNonNull(input, context.nullInputMessage());
  }

  /**
   * Validates Kind parameter for operations with class-based context.
   *
   * @param kind The Kind to validate
   * @param contextClass The class providing context (e.g., StateTMonad.class, OptionalT.class)
   * @param operation The operation name for context
   * @param <F> The witness type
   * @param <A> The value type
   * @return The validated Kind
   * @throws NullPointerException if kind is null
   * @example
   *     <pre>
   * Validation.kindValidator().requireNonNull(fa, StateTMonad.class, "map");
   * // Error: "Kind for StateTMonad.map cannot be null"
   * </pre>
   */
  public <F, A> Kind<F, A> requireNonNull(
      Kind<F, A> kind, Class<?> contextClass, Operation operation) {

    Objects.requireNonNull(contextClass, "contextClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    String fullOperation = contextClass.getSimpleName() + "." + operation;
    return Objects.requireNonNull(kind, "Kind for " + fullOperation + " cannot be null");
  }

  /**
   * Validates Kind parameter for operations with operation context.
   *
   * @param kind The Kind to validate
   * @param operation The operation name for context
   * @param <F> The witness type
   * @param <A> The value type
   * @return The validated Kind
   * @throws NullPointerException if kind is null
   */
  public <F, A> Kind<F, A> requireNonNull(Kind<F, A> kind, Operation operation) {
    return Objects.requireNonNull(kind, "Kind for " + operation + " cannot be null");
  }

  /**
   * Validates Kind parameter with class-based context and optional descriptor.
   *
   * <p>Use descriptors to distinguish between multiple Kind parameters in the same operation. For
   * example, in an {@code ap} operation with both a function Kind and an argument Kind, use
   * descriptors like "function" and "argument" to make error messages clearer.
   *
   * @param kind The Kind to validate
   * @param contextClass The class providing context
   * @param operation The operation name for context
   * @param descriptor Optional descriptor for the parameter (e.g., "function", "argument",
   *     "source")
   * @param <F> The witness type
   * @param <A> The value type
   * @return The validated Kind
   * @throws NullPointerException if kind is null
   * @example
   *     <pre>
   * Validation.kindValidator().requireNonNull(ff, StateTMonad.class, "ap", "function");
   * // Error: "Kind for StateTMonad.ap (function) cannot be null"
   *
   * Validation.kindValidator().requireNonNull(fa, StateTMonad.class, "ap", "argument");
   * // Error: "Kind for StateTMonad.ap (argument) cannot be null"
   * </pre>
   */
  public <F, A> Kind<F, A> requireNonNull(
      Kind<F, A> kind, Class<?> contextClass, Operation operation, @Nullable String descriptor) {

    Objects.requireNonNull(contextClass, "contextClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    String fullOperation = contextClass.getSimpleName() + "." + operation;
    String contextMessage =
        descriptor != null ? fullOperation + " (" + descriptor + ")" : fullOperation;

    return Objects.requireNonNull(kind, "Kind for " + contextMessage + " cannot be null");
  }

  /**
   * Validates Kind parameter with optional descriptor for enhanced error messages.
   *
   * @param kind The Kind to validate
   * @param operation The operation name for context
   * @param descriptor Optional descriptor for the parameter
   * @param <F> The witness type
   * @param <A> The value type
   * @return The validated Kind
   * @throws NullPointerException if kind is null
   */
  public <F, A> Kind<F, A> requireNonNull(
      Kind<F, A> kind, Operation operation, @Nullable String descriptor) {

    String contextMessage =
        descriptor != null ? operation + " (" + descriptor + ")" : operation.toString();

    return Objects.requireNonNull(kind, "Kind for " + contextMessage + " cannot be null");
  }

  public record KindContext(Class<?> targetType, String operation) {

    public KindContext {
      Objects.requireNonNull(targetType, "targetType cannot be null");
      Objects.requireNonNull(operation, "operation cannot be null");
    }

    public static KindContext narrow(Class<?> targetType) {
      return new KindContext(targetType, "narrow");
    }

    public static KindContext widen(Class<?> targetType) {
      return new KindContext(targetType, "widen");
    }

    public String nullParameterMessage() {
      return "Cannot %s null Kind for %s".formatted(operation, targetType.getSimpleName());
    }

    public String nullInputMessage() {
      return "Input %s cannot be null for %s".formatted(targetType.getSimpleName(), operation);
    }

    public String invalidTypeMessage(String actualClassName) {
      return "Kind instance is not a %s: %s".formatted(targetType.getSimpleName(), actualClassName);
    }
  }
}
