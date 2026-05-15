// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

/**
 * Entry point for the validation utilities.
 *
 * <p>Two equivalent access styles are supported. The static fields are preferred for new code
 * because they shave a pair of parentheses off the call site:
 *
 * <pre>{@code
 * Validation.KIND.requireNonNull(ma, FLAT_MAP);   // preferred
 * Validation.kind().requireNonNull(ma, FLAT_MAP); // also fine
 * }</pre>
 *
 * <p><b>Exception contract.</b> The validators are intentionally split between two exception types:
 *
 * <ul>
 *   <li>{@link NullPointerException} — thrown by precondition checks on parameters supplied by the
 *       caller (functions, monoids, error values, transformer components, outer monads, and {@link
 *       KindValidator#requireNonNull} on {@link org.higherkindedj.hkt.Kind} parameters).
 *   <li>{@link org.higherkindedj.hkt.exception.KindUnwrapException} — thrown by Kind unwrap
 *       operations such as {@link KindValidator#narrow}, {@link KindValidator#narrowWithTypeCheck},
 *       {@link KindValidator#narrowHolder}, {@link KindValidator#narrowWithPattern}, and {@link
 *       FunctionValidator#requireNonNullResult} (which signals a bind-style violation where a user
 *       function returned a null Kind).
 * </ul>
 */
public interface Validation {

  /** Validator for core sum-type values and errors (Either, Validated, ...). */
  CoreTypeValidator CORE = CoreTypeValidator.CORE_TYPE_VALIDATOR;

  /** Validator for function and other reference parameters in monad/functor operations. */
  FunctionValidator FUNCTION = FunctionValidator.FUNCTION_VALIDATOR;

  /** Validator for {@link org.higherkindedj.hkt.Kind} parameters and narrow/widen operations. */
  KindValidator KIND = KindValidator.KIND_VALIDATOR;

  /** Validator for monad transformer outer instances and inner components. */
  TransformerValidator TRANSFORMER = TransformerValidator.TRANSFORMER_VALIDATOR;

  static CoreTypeValidator coreType() {
    return CORE;
  }

  static FunctionValidator function() {
    return FUNCTION;
  }

  static KindValidator kind() {
    return KIND;
  }

  static TransformerValidator transformer() {
    return TRANSFORMER;
  }
}
