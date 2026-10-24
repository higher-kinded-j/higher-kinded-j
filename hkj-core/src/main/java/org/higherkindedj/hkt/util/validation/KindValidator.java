// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static org.higherkindedj.hkt.util.validation.Operation.AP;
import static org.higherkindedj.hkt.util.validation.Operation.NARROW;
import static org.higherkindedj.hkt.util.validation.Operation.WIDEN;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.WitnessArity;
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
   * <p>This method uses modern switch expressions to handle null and type checking in a consistent
   * manner. The narrower function should use pattern matching where appropriate.
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
  public <F extends WitnessArity<?>, A, T> T narrow(
      @Nullable Kind<F, A> kind,
      Class<T> targetType,
      Function<? super Kind<F, A>, ? extends T> narrower) {

    var context = new KindContext(targetType, NARROW.toString());

    return switch (kind) {
      case null -> throw new KindUnwrapException(context.nullParameterMessage());
      default -> {
        try {
          yield narrower.apply(kind);
        } catch (Exception e) {
          throw new KindUnwrapException(context.invalidTypeMessage(kind), e);
        }
      }
    };
  }

  /**
   * Validates and narrows using instanceof type checking. This is the preferred method for types
   * that directly implement the Kind interface (e.g., transformers, Id, Validated).
   *
   * <p>This method uses modern switch expressions with pattern matching for cleaner code.
   *
   * @param kind The Kind to narrow, may be null
   * @param targetType The target type class
   * @param <F> The witness type of the Kind
   * @param <A> The value type of the Kind
   * @param <T> The target type to narrow to
   * @return The narrowed result
   * @throws KindUnwrapException if kind is null or not of the expected type
   */
  public <F extends WitnessArity<?>, A, T> T narrowWithTypeCheck(
      @Nullable Kind<F, A> kind, Class<T> targetType) {

    var context = new KindContext(targetType, NARROW.toString());

    return switch (kind) {
      case null -> throw new KindUnwrapException(context.nullParameterMessage());
      default -> {
        if (!targetType.isInstance(kind)) {
          throw new KindUnwrapException(context.invalidTypeMessage(kind));
        }
        yield targetType.cast(kind);
      }
    };
  }

  /**
   * Specialised narrowing for holder-based Kind implementations.
   *
   * <p>A KindHelper whose type does not implement {@link Kind} itself, such as {@code Optional} or
   * {@code Lazy}, wraps it in an internal record (e.g. {@code OptionalHolder}, {@code LazyHolder}).
   * Pass a method reference to the holder's accessor to extract the wrapped value with one line:
   *
   * <pre>{@code
   * public <A> Optional<A> narrow(@Nullable Kind<OptionalKind.Witness, A> kind) {
   *   return Validation.KIND.narrowHolder(kind, Optional.class, OptionalHolder.class,
   *                                       OptionalHolder::optional);
   * }
   * }</pre>
   *
   * @param kind The Kind to narrow, may be null
   * @param targetType The target type class for error messaging
   * @param holderType The holder type the Kind must be an instance of
   * @param accessor Function to extract the value from the holder (typically a method reference)
   * @param <F> The witness type of the Kind
   * @param <A> The value type of the Kind
   * @param <T> The target type to narrow to
   * @param <H> The holder type
   * @return The narrowed result
   * @throws KindUnwrapException if kind is null or not of the expected holder type
   */
  public <F extends WitnessArity<?>, A, T, H extends Kind<F, A>> T narrowHolder(
      @Nullable Kind<F, A> kind,
      Class<T> targetType,
      Class<H> holderType,
      Function<? super H, ? extends T> accessor) {

    var context = new KindContext(targetType, NARROW.toString());

    return switch (kind) {
      case null -> throw new KindUnwrapException(context.nullParameterMessage());
      default -> {
        if (!holderType.isInstance(kind)) {
          throw new KindUnwrapException(context.invalidTypeMessage(kind));
        }
        yield accessor.apply(holderType.cast(kind));
      }
    };
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
    if (input == null) {
      var context = new KindContext(inputType, WIDEN.toString());
      throw new NullPointerException(context.nullInputMessage());
    }
    return input;
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
  public <F extends WitnessArity<?>, A> Kind<F, A> requireNonNull(
      Kind<F, A> kind, Operation operation) {
    return requireNonNull(kind, operation, null);
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
  public <F extends WitnessArity<?>, A> Kind<F, A> requireNonNull(
      Kind<F, A> kind, Operation operation, @Nullable String descriptor) {

    if (kind == null) {
      String contextMessage =
          descriptor != null ? operation + " (" + descriptor + ")" : operation.toString();
      throw new NullPointerException("Kind for " + contextMessage + " cannot be null");
    }
    return kind;
  }

  // ==================== Bulk Validation Helpers ====================
  // These methods reduce boilerplate by combining multiple validations into single calls.

  /**
   * Validates all Kind parameters for an ap (applicative application) operation in a single call.
   *
   * <p>This combines validation of both the function Kind and argument Kind, reducing boilerplate
   * in Applicative implementations.
   *
   * @param ff the function Kind (must be non-null)
   * @param fa the argument Kind (must be non-null)
   * @param <F> the functor type constructor
   * @param <A> input type
   * @param <B> output type
   * @throws NullPointerException if ff or fa is null
   */
  public <F extends WitnessArity<?>, A, B> void validateAp(
      Kind<F, ? extends Function<A, B>> ff, Kind<F, A> fa) {
    requireNonNull(ff, AP, "function");
    requireNonNull(fa, AP, "argument");
  }

  /**
   * Context record for Kind validation operations. Provides consistent error message generation.
   */
  public record KindContext(Class<?> targetType, String operation) {

    public KindContext {
      Objects.requireNonNull(targetType, "targetType cannot be null");
      Objects.requireNonNull(operation, "operation cannot be null");
    }

    public String nullParameterMessage() {
      return String.format(
          Locale.ROOT, "Cannot %s null Kind for %s", operation, targetType.getSimpleName());
    }

    public String nullInputMessage() {
      return String.format(
          Locale.ROOT, "Input %s cannot be null for %s", targetType.getSimpleName(), operation);
    }

    /** Enhanced error message that includes the actual type received. */
    public String invalidTypeMessage(Kind<?, ?> actualKind) {
      return String.format(
          Locale.ROOT,
          "Kind instance cannot be narrowed to %s (received: %s)",
          targetType.getSimpleName(),
          actualKind.getClass().getSimpleName());
    }
  }
}
