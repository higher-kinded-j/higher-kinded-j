// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

/**
 * Internal helper that builds the exception a context's {@code via()} throws when its function
 * returns a different kind of context.
 *
 * <p>The message words the mismatch as the Effect Paths do. It is a single line, shown here one
 * sentence per line:
 *
 * <pre>{@code
 * Type mismatch in via(): expected ConfigContext but function returned ErrorContext.
 * Each context type can only chain with the same type.
 * Build the step with ConfigContext's factory methods, or chain with flatMap(), which javac checks.
 * }</pre>
 *
 * <p>Where a conversion turns the returned context into the expected one, the last sentence names
 * it instead of the factory methods, as in {@code Convert the step with toErrorContext}. Every
 * {@link EffectContext} is one of the final classes its sealed interface permits, so each is named
 * by its simple name.
 */
final class ContextTypeMismatch {

  private ContextTypeMismatch() {}

  /**
   * Builds the exception for a {@code via()} whose function returned another kind of context.
   *
   * @param expected the context type {@code via()} needs
   * @param actual the context the function returned instead
   * @return the exception to throw
   */
  static IllegalArgumentException via(Class<?> expected, EffectContext<?, ?> actual) {
    return new IllegalArgumentException(
        "Type mismatch in via(): expected "
            + expected.getSimpleName()
            + " but function returned "
            + actual.getClass().getSimpleName()
            + ". Each context type can only chain with the same type. "
            + remedy(expected, actual)
            + ", or chain with flatMap(), which javac checks.");
  }

  private static String remedy(Class<?> expected, EffectContext<?, ?> actual) {
    boolean optional = actual instanceof OptionalContext<?, ?>;
    boolean javaOptional = actual instanceof JavaOptionalContext<?, ?>;
    if (expected == ErrorContext.class && (optional || javaOptional)) {
      return "Convert the step with toErrorContext";
    }
    if (expected == OptionalContext.class && javaOptional) {
      return "Convert the step with toOptionalContext";
    }
    return "Build the step with " + expected.getSimpleName() + "'s factory methods";
  }
}
