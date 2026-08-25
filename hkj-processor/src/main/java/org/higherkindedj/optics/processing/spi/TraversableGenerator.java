// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.spi;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * A Service Provider Interface (SPI) for generating Traversal implementations. Implement this
 * interface to add support for new traversable container types to the TraversalProcessor.
 *
 * <p>Implementations may also override the default methods {@link #getCardinality()}, {@link
 * #generateOpticExpression()}, and {@link #getRequiredImports()} to participate in Focus DSL path
 * widening. When these methods are overridden, the FocusProcessor and NavigatorClassGenerator will
 * automatically select the correct path type ({@code AffinePath} or {@code TraversalPath}) and
 * generate the appropriate composition call ({@code .some(affine)} or {@code .each(each)}).
 *
 * @since 0.3.8
 */
public interface TraversableGenerator {

  /** Priority for catch-all/fallback generators. */
  int PRIORITY_FALLBACK = -100;

  /** Default priority for standard generators. */
  int PRIORITY_DEFAULT = 0;

  /** Priority for explicit overrides of built-in generators. */
  int PRIORITY_OVERRIDE = 100;

  /**
   * Returns the priority of this generator. Higher values indicate higher priority. When multiple
   * generators support the same type, the highest-priority one wins. Equal-priority conflicts emit
   * a compile-time warning.
   *
   * <p>Recommended constants:
   *
   * <ul>
   *   <li>{@link #PRIORITY_FALLBACK} ({@value #PRIORITY_FALLBACK}) — catch-all generators
   *   <li>{@link #PRIORITY_DEFAULT} ({@value #PRIORITY_DEFAULT}) — standard generators
   *   <li>{@link #PRIORITY_OVERRIDE} ({@value #PRIORITY_OVERRIDE}) — explicit overrides of built-in
   *       generators
   * </ul>
   *
   * @return the priority of this generator (default: {@value #PRIORITY_DEFAULT})
   * @since 0.4.0
   */
  default int priority() {
    return PRIORITY_DEFAULT;
  }

  /**
   * Checks if this generator can handle the given type.
   *
   * @param type The type of the record component (e.g., {@code java.util.List<String>}).
   * @return true if this generator supports the type.
   */
  boolean supports(TypeMirror type);

  /**
   * Returns the cardinality of elements within this container type.
   *
   * <p>This determines the appropriate path type in the Focus DSL:
   *
   * <ul>
   *   <li>{@link Cardinality#ZERO_OR_ONE} → {@code AffinePath}, always (for types like Optional,
   *       Maybe, Either, Try, Validated)
   *   <li>{@link Cardinality#ZERO_OR_MORE} → {@code TraversalPath} when the container is widened,
   *       which it is under {@code widenCollections = true} or when its element is itself a
   *       navigable record (for types like List, Set, Map, arrays, and third-party collections)
   * </ul>
   *
   * <p>The default implementation returns {@link Cardinality#ZERO_OR_MORE}, which is correct for
   * most collection types. Generators for optional/either-like types should override this to return
   * {@link Cardinality#ZERO_OR_ONE}.
   *
   * @return the cardinality of elements in this container type
   */
  default Cardinality getCardinality() {
    return Cardinality.ZERO_OR_MORE;
  }

  /**
   * Returns the index of the type argument that this generator focuses on for traversal.
   *
   * <p>For most container types like {@code List<T>} or {@code Optional<T>}, this is 0 (the first
   * type argument). For types like {@code Either<L, R>}, {@code Validated<E, A>}, or {@code Map<K,
   * V>} where the traversal focuses on the second type argument, this should return 1.
   *
   * @return the zero-based index of the focused type argument
   */
  default int getFocusTypeArgumentIndex() {
    return 0;
  }

  /**
   * Returns a Java source expression that creates the optic instance for composing into a Focus
   * path chain.
   *
   * <p>For ZERO_OR_ONE types, this should return an expression producing an {@code Affine}. For
   * ZERO_OR_MORE types, this should return an expression producing an {@code Each}.
   *
   * @return a valid Java source expression, e.g. {@code "Affines.eitherRight()"}
   */
  default String generateOpticExpression() {
    return "";
  }

  /**
   * Returns the fully qualified class names that must be imported for the optic expression to
   * compile.
   *
   * @return set of fully qualified class names (defaults to empty)
   */
  default Set<String> getRequiredImports() {
    return Set.of();
  }

  /**
   * Resolves a type that may be a wildcard to its effective type for focus extraction. Delegates to
   * {@link ProcessorUtils#resolveWildcard(TypeMirror)}.
   *
   * <p>SPI implementors can use this to resolve wildcard bounds in type arguments:
   *
   * <ul>
   *   <li>{@code ? extends T} → {@code T}
   *   <li>{@code ? super T} → {@code null} (treat as Object)
   *   <li>{@code ?} → {@code null} (treat as Object)
   * </ul>
   *
   * @param type the type to resolve
   * @return the resolved type, or null if the wildcard should be treated as Object
   * @since 0.4.0
   */
  default TypeMirror resolveEffectiveType(TypeMirror type) {
    return ProcessorUtils.resolveWildcard(type);
  }

  /**
   * The record's type as it must be written where an instance of it is constructed: {@code
   * Holder<T>} for a generic record, and the class name itself for every other record.
   *
   * <p>A generated traversal method is declared with the record's own type variables, so naming the
   * record without them constructs a raw instance. That is an unchecked conversion, and it warns
   * wherever the generated source is compiled.
   *
   * @param component the record component the traversal is generated for
   * @param recordClassName the record's class name, as handed to {@link #generateModifyF}
   * @return the record's type name, carrying its type variables where it declares any
   * @since 0.4.10
   */
  default TypeName recordTypeName(
      final RecordComponentElement component, final ClassName recordClassName) {
    final List<? extends TypeParameterElement> typeParameters =
        ((TypeElement) component.getEnclosingElement()).getTypeParameters();
    if (typeParameters.isEmpty()) {
      return recordClassName;
    }
    return ParameterizedTypeName.get(
        recordClassName,
        typeParameters.stream().map(TypeVariableName::get).toArray(TypeName[]::new));
  }

  /**
   * The effect's type variable, as the generated {@code modifyF} declares it: {@code F}, unless the
   * record has claimed that name for a type parameter of its own.
   *
   * <p>Write uses of the effect through this rather than naming {@code F} directly. A traversal is
   * generated inside a method carrying the record's type variables, so a body that says {@code F}
   * where the record declares {@code F} is written in terms of the record's variable, not the
   * effect.
   *
   * @param component the record component the traversal is generated for
   * @return the type variable the generated method declares the effect under
   * @since 0.4.10
   */
  default TypeVariableName effectVariable(final RecordComponentElement component) {
    return TypeVariableName.get(
        ProcessorUtils.effectVariableName((TypeElement) component.getEnclosingElement()));
  }

  /**
   * Generates the body of the `modifyF` method for a Traversal.
   *
   * @param component The record component being processed (e.g., the 'items' field).
   * @param recordClassName The ClassName of the record containing the component.
   * @param allComponents A list of all components in the record, for reconstruction.
   * @return A CodeBlock from Javapoet representing the implementation of the traversal.
   */
  CodeBlock generateModifyF(
      RecordComponentElement component,
      ClassName recordClassName,
      List<? extends RecordComponentElement> allComponents);
}
