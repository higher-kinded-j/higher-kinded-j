// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.spi;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.List;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.TypeMirror;

/**
 * A Service Provider Interface (SPI) for generating Traversal implementations. Implement this
 * interface to add support for new traversable container types to the TraversalProcessor.
 */
public interface TraversableGenerator {

  /**
   * Checks if this generator can handle the given type.
   *
   * @param type The type of the record component (e.g., java.util.List<String>).
   * @return true if this generator supports the type.
   */
  boolean supports(TypeMirror type);

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
