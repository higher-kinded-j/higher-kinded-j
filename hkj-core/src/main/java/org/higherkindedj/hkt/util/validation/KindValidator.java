// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.util.context.*;
import org.jspecify.annotations.Nullable;

/**
 * Handles Kind-specific validations with rich context.
 *
 * <p>This validator provides type-safe Kind operations that prevent common errors like passing
 * descriptions instead of type names, and ensures consistent error messaging across all
 * Kind-related operations.
 */
public final class KindValidator {

    private KindValidator() {
        throw new AssertionError("KindValidator is a utility class and should not be instantiated");
    }

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
    public static <F, A, T> T narrow(
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
            // Wrap other exceptions with context
            throw new KindUnwrapException(
                    context.customMessage(
                            "Failed to narrow Kind to %s: %s", targetType.getSimpleName(), e.getMessage()),
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
    public static <F, A, T> T narrowWithTypeCheck(@Nullable Kind<F, A> kind, Class<T> targetType) {

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
     * Advanced narrowing with multiple type matchers. Useful when a Kind might be one of several
     * valid types.
     */
    public static <F, A, T> T narrowWithMatchers(
            @Nullable Kind<F, A> kind, Class<T> resultType, TypeMatcher<Kind<F, A>, T>... matchers) {

        var context = KindContext.narrow(resultType);

        if (kind == null) {
            throw new KindUnwrapException(context.nullParameterMessage());
        }

        for (TypeMatcher<Kind<F, A>, T> matcher : matchers) {
            if (matcher.matches(kind)) {
                return matcher.extract(kind);
            }
        }

        throw new KindUnwrapException(context.invalidTypeMessage(kind.getClass().getName()));
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
    public static <T> T requireForWiden(T input, Class<T> inputType) {
        var context = KindContext.widen(inputType);
        return Objects.requireNonNull(input, context.nullInputMessage());
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
    public static <F, A> Kind<F, A> requireNonNull(Kind<F, A> kind, String operation) {
        return Objects.requireNonNull(kind, "Kind for " + operation + " cannot be null");
    }

    /**
     * Validates Kind parameter with optional descriptor for enhanced error messages.
     *
     * <p>Use descriptors to distinguish between multiple Kind parameters in the same operation.
     * For example, in an {@code ap} operation with both a function Kind and an argument Kind,
     * use descriptors like "function" and "argument" to make error messages clearer.
     *
     * @param kind The Kind to validate
     * @param operation The operation name for context
     * @param descriptor Optional descriptor for the parameter (e.g., "function", "argument", "source")
     * @param <F> The witness type
     * @param <A> The value type
     * @return The validated Kind
     * @throws NullPointerException if kind is null
     */
    public static <F, A> Kind<F, A> requireNonNull(
            Kind<F, A> kind, String operation, @Nullable String descriptor) {

        String contextMessage =
                descriptor != null ? operation + " (" + descriptor + ")" : operation;

        return Objects.requireNonNull(kind, "Kind for " + contextMessage + " cannot be null");
    }

    /** Interface for type matching in advanced narrowing scenarios. */
    public interface TypeMatcher<S, T> {
        boolean matches(S source);

        T extract(S source);

        static <S, T> TypeMatcher<S, T> forClass(Class<? extends T> clazz) {
            return new TypeMatcher<S, T>() {
                @Override
                public boolean matches(S source) {
                    return clazz.isInstance(source);
                }

                @Override
                @SuppressWarnings("unchecked")
                public T extract(S source) {
                    return (T) source;
                }
            };
        }
    }
}