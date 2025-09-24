// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.higherkindedj.hkt.exception.KindUnwrapException;

/**
 * Handles exception wrapping and chaining with rich context.
 *
 * <p>This validator provides utilities for wrapping exceptions with additional context while
 * preserving the original exception chain.
 */
public final class ExceptionValidator {

  private ExceptionValidator() {
    throw new AssertionError(
        "ExceptionValidator is a utility class and should not be instantiated");
  }

  /**
   * Wraps an exception with additional context while preserving the original stack trace.
   *
   * @param originalException The original exception
   * @param contextMessage Additional context message
   * @param wrapperConstructor Constructor for the wrapper exception
   * @param <T> The type of exception to wrap
   * @return The wrapped exception
   * @throws NullPointerException if any parameter is null
   */
  public static <T extends Exception> T wrapWithContext(
      Exception originalException,
      String contextMessage,
      BiFunction<String, Throwable, T> wrapperConstructor) {

    Objects.requireNonNull(originalException, "originalException cannot be null");
    Objects.requireNonNull(contextMessage, "contextMessage cannot be null");
    Objects.requireNonNull(wrapperConstructor, "wrapperConstructor cannot be null");

    String combinedMessage = contextMessage + ": " + originalException.getMessage();
    return wrapperConstructor.apply(combinedMessage, originalException);
  }

  /**
   * Creates a KindUnwrapException with additional context.
   *
   * @param originalException The original exception
   * @param contextMessage Additional context message
   * @return The wrapped KindUnwrapException
   * @throws NullPointerException if any parameter is null
   */
  public static KindUnwrapException wrapAsKindUnwrapException(
      Exception originalException, String contextMessage) {

    return wrapWithContext(originalException, contextMessage, KindUnwrapException::new);
  }

  /**
   * Throws a KindUnwrapException with a lazily-evaluated message.
   *
   * @param messageSupplier Supplier for the error message
   * @throws KindUnwrapException always
   */
  public static void throwKindUnwrapException(Supplier<String> messageSupplier) {
    Objects.requireNonNull(messageSupplier, "messageSupplier cannot be null");
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
    Objects.requireNonNull(messageSupplier, "messageSupplier cannot be null");
    Objects.requireNonNull(cause, "cause cannot be null");
    throw new KindUnwrapException(messageSupplier.get(), cause);
  }

  /**
   * Creates a lazy message supplier for expensive-to-compute error descriptions.
   *
   * @param template The message template with placeholders
   * @param args The arguments to substitute into the template
   * @return A supplier that produces the formatted message when called
   */
  public static Supplier<String> lazyMessage(String template, Object... args) {
    Objects.requireNonNull(template, "template cannot be null");
    return () -> String.format(template, args);
  }
}
