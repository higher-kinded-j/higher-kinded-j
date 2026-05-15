// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import java.util.Objects;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Handles domain-specific validations for transformers, witnesses, etc.
 *
 * <p>This validator provides specialized validation for Higher-Kinded-J specific concepts like
 * monad transformers and witness types.
 */
public enum TransformerValidator {
  TRANSFORMER_VALIDATOR;

  /**
   * Validates outer monad for transformer construction with class-based context.
   *
   * <p>This is the recommended method that uses the transformer class to automatically derive the
   * type name, ensuring consistency and refactoring safety.
   *
   * @param monad The outer monad to validate
   * @param transformerClass The class of the transformer (e.g., StateT.class, OptionalT.class)
   * @param operation The {@link Operation} for error context (e.g., {@code CONSTRUCTION}, {@code
   *     LIFT_F}, {@code MAP})
   * @param <F> The monad witness type
   * @return The validated monad
   * @throws NullPointerException with context-specific message if monad is null
   *     <p>Example usage:
   *     <pre>
   * // In constructor
   * Validation.transformer().requireOuterMonad(monadF, StateT.class, CONSTRUCTION);
   * // Error: "Outer Monad cannot be null for StateT construction"
   *
   * // In factory method
   * Validation.transformer().requireOuterMonad(outerMonad, OptionalT.class, LIFT_F);
   * // Error: "Outer Monad cannot be null for OptionalT liftF"
   * </pre>
   */
  public <F extends WitnessArity<TypeArity.Unary>> Monad<F> requireOuterMonad(
      Monad<F> monad, Class<?> transformerClass, Operation operation) {

    Objects.requireNonNull(transformerClass, "transformerClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    String transformerName = transformerClass.getSimpleName();
    String context = transformerName + " " + operation;

    var domainContext = new DomainContext("Outer Monad", context);
    return Objects.requireNonNull(monad, domainContext.nullParameterMessage());
  }

  /**
   * Validates the outer applicative for transformer construction.
   *
   * @param applicative The outer applicative to validate
   * @param transformerClass The class of the transformer
   * @param operation The {@link Operation} for error context
   * @param <F> The applicative witness type
   * @return The validated applicative
   * @throws NullPointerException if applicative is null
   */
  public <F extends WitnessArity<TypeArity.Unary>> Applicative<F> requireOuterApplicative(
      Applicative<F> applicative, Class<?> transformerClass, Operation operation) {

    Objects.requireNonNull(transformerClass, "transformerClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    String context = transformerClass.getSimpleName() + " " + operation;
    var domainContext = new DomainContext("Outer Applicative", context);
    return Objects.requireNonNull(applicative, domainContext.nullParameterMessage());
  }

  /**
   * Validates the outer functor for transformer construction.
   *
   * @param functor The outer functor to validate
   * @param transformerClass The class of the transformer
   * @param operation The {@link Operation} for error context
   * @param <F> The functor witness type
   * @return The validated functor
   * @throws NullPointerException if functor is null
   */
  public <F extends WitnessArity<TypeArity.Unary>> Functor<F> requireOuterFunctor(
      Functor<F> functor, Class<?> transformerClass, Operation operation) {

    Objects.requireNonNull(transformerClass, "transformerClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    String context = transformerClass.getSimpleName() + " " + operation;
    var domainContext = new DomainContext("Outer Functor", context);
    return Objects.requireNonNull(functor, domainContext.nullParameterMessage());
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
   * @param operation The {@link Operation} for context (e.g., {@code FROM_OPTIONAL}, {@code
   *     CONSTRUCTION})
   * @param <T> The component type
   * @return The validated component
   * @throws NullPointerException with descriptive message if component is null
   *     <p>Example usage:
   *     <pre>
   * Validation.transformer().requireTransformerComponent(
   *     optional,
   *     "inner Optional",
   *     OptionalT.class,
   *     FROM_OPTIONAL
   * );
   * // Error: "inner Optional cannot be null for OptionalT.fromOptional"
   * </pre>
   */
  public <T> T requireTransformerComponent(
      T component, String componentName, Class<?> transformerClass, Operation operation) {

    Objects.requireNonNull(transformerClass, "transformerClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    String transformerName = transformerClass.getSimpleName();
    String fullContext = transformerName + "." + operation;

    var context = DomainContext.transformer(fullContext);

    return Objects.requireNonNull(
        component, "%s cannot be null for %s".formatted(componentName, fullContext));
  }

  /** Context for domain-specific validations (transformers, etc.) */
  public record DomainContext(String domainType, String objectName) {

    public DomainContext {
      Objects.requireNonNull(domainType, "domainType cannot be null");
      Objects.requireNonNull(objectName, "objectName cannot be null");
    }

    public static DomainContext transformer(String transformerName) {
      return new DomainContext("Transformer", transformerName);
    }

    public static DomainContext witness(String operation) {
      return new DomainContext("Witness", operation);
    }

    public String nullParameterMessage() {
      return "%s cannot be null for %s".formatted(domainType, objectName);
    }
  }
}
