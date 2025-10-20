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
public final class TransformerValidator {

  private TransformerValidator() {
    throw new AssertionError("DomainValidator is a utility class and should not be instantiated");
  }

  /**
   * Validates outer monad for transformer construction with class-based context.
   *
   * <p>This is the recommended method that uses the transformer class to automatically derive the
   * type name, ensuring consistency and refactoring safety.
   *
   * @param monad The outer monad to validate
   * @param transformerClass The class of the transformer (e.g., StateT.class, OptionalT.class)
   * @param methodName The method name for error context (e.g., "construction", "lift", "map")
   * @param <F> The monad witness type
   * @return The validated monad
   * @throws NullPointerException with context-specific message if monad is null
   * @example
   *     <pre>
   * // In constructor
   * DomainValidator.requireOuterMonad(monadF, StateT.class, "construction");
   * // Error: "Outer Monad cannot be null for StateT construction"
   *
   * // In factory method
   * DomainValidator.requireOuterMonad(outerMonad, OptionalT.class, "lift");
   * // Error: "Outer Monad cannot be null for OptionalT.lift"
   * </pre>
   */
  public static <F> Monad<F> requireOuterMonad(
      Monad<F> monad, Class<?> transformerClass, Operation methodName) {

    Objects.requireNonNull(transformerClass, "transformerClass cannot be null");
    Objects.requireNonNull(methodName, "methodName cannot be null");

    String transformerName = transformerClass.getSimpleName();
    String context = transformerName + " " + methodName;

    var domainContext = DomainContext.transformer(context);
    return Objects.requireNonNull(monad, domainContext.nullParameterMessage());
  }

  /**
   * Validates a transformer component using class-based context.
   *
   * <p>This is the recommended method that automatically derives the transformer name from the
   * class.
   *
   * @param component The component to validate
   * @param componentName The name of the component (e.g., "inner Optional", "state function")
   * @param transformerClass The class of the transformer (e.g., OptionalT.class, StateT.class)
   * @param methodName The method name for context (e.g., "fromOptional", "create")
   * @param <T> The component type
   * @return The validated component
   * @throws NullPointerException with descriptive message if component is null
   * @example
   *     <pre>
   * DomainValidator.requireTransformerComponent(
   *     optional,
   *     "inner Optional",
   *     OptionalT.class,
   *     "fromOptional"
   * );
   * // Error: "inner Optional cannot be null for OptionalT.fromOptional"
   * </pre>
   */
  public static <T> T requireTransformerComponent(
      T component, String componentName, Class<?> transformerClass, Operation methodName) {

    Objects.requireNonNull(transformerClass, "transformerClass cannot be null");
    Objects.requireNonNull(methodName, "methodName cannot be null");

    String transformerName = transformerClass.getSimpleName();
    String fullContext = transformerName + "." + methodName;

    var context = DomainContext.transformer(fullContext);

    return Objects.requireNonNull(
        component, context.customMessage("%s cannot be null for %s", componentName, fullContext));
  }
}
