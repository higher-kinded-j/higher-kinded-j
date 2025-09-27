// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import java.util.Objects;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.util.context.DomainContext;

/**
 * Handles domain-specific validations for transformers, witnesses, etc.
 *
 * <p>This validator provides specialized validation for Higher-Kinded-J specific concepts like
 * monad transformers and witness types.
 */
public final class DomainValidator {

  private DomainValidator() {
    throw new AssertionError("DomainValidator is a utility class and should not be instantiated");
  }

  /**
   * Validates outer monad for transformer construction.
   *
   * @param monad The outer monad to validate
   * @param transformerName The name of the transformer for error messaging
   * @param <F> The monad witness type
   * @return The validated monad
   * @throws NullPointerException with transformer-specific message if monad is null
   */
  public static <F> Monad<F> requireOuterMonad(Monad<F> monad, String transformerName) {
    var context = DomainContext.transformer(transformerName);
    return Objects.requireNonNull(monad, context.nullParameterMessage());
  }

  /**
   * Validates that two witness types match for Kind operations.
   *
   * @param expected The expected witness type
   * @param actual The actual witness type
   * @param operation The operation name for error messaging
   * @param <F> The expected witness type
   * @param <G> The actual witness type
   * @throws IllegalArgumentException with detailed mismatch message if types don't match
   */
  public static <F, G> void requireMatchingWitness(
      Class<F> expected, Class<G> actual, String operation) {

    var context = DomainContext.witness(operation);

    Objects.requireNonNull(expected, "expected witness type cannot be null");
    Objects.requireNonNull(actual, "actual witness type cannot be null");

    if (!expected.equals(actual)) {
      throw new IllegalArgumentException(context.mismatchMessage(expected, actual));
    }
  }

  /**
   * Validates that a transformer has compatible inner and outer types.
   *
   * @param transformerName The transformer name
   * @param outerMonad The outer monad
   * @param innerMonad The inner monad
   * @param <F> The outer monad witness type
   * @param <G> The inner monad witness type
   * @return The validated outer monad
   * @throws NullPointerException if any parameter is null
   */
  public static <F, G> Monad<F> requireCompatibleTransformer(
      String transformerName, Monad<F> outerMonad, Monad<G> innerMonad) {

    var context = DomainContext.transformer(transformerName);

    Objects.requireNonNull(outerMonad, context.nullParameterMessage());
    Objects.requireNonNull(
        innerMonad, context.customMessage("Inner monad cannot be null for %s", transformerName));

    return outerMonad;
  }
}
