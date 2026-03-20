// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.spi;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.TypeMirror;

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
   *   <li>{@link Cardinality#ZERO_OR_ONE} → {@code AffinePath} (for types like Optional, Maybe,
   *       Either, Try, Validated)
   *   <li>{@link Cardinality#ZERO_OR_MORE} → {@code TraversalPath} (for types like List, Set, Map,
   *       arrays, and third-party collections)
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
