// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * An abstract base class for {@link TraversableGenerator} implementations that provides common
 * helper methods to reduce code duplication.
 */
public abstract class BaseTraversableGenerator implements TraversableGenerator {

  /**
   * Extracts the primary generic type from a container-like record component.
   *
   * <p>For example, for a component of type {@code List<String>}, this returns "String".
   *
   * @param component The record component to inspect.
   * @return The {@link TypeName} of the first generic argument, or {@code Object} as a fallback.
   */
  protected TypeName getGenericTypeName(final RecordComponentElement component) {
    if (component.asType() instanceof DeclaredType containerType) {
      if (containerType.getTypeArguments().isEmpty()) {
        return ClassName.get(Object.class);
      }
      return TypeName.get(containerType.getTypeArguments().getFirst()).box();
    }
    return ClassName.get(Object.class); // Fallback for raw types or non-declared types
  }

  /**
   * Generates the comma-separated argument list needed to call the record's constructor.
   *
   * <p>It replaces the component being modified with the {@code newComponentValue} placeholder and
   * uses the accessor for all other components.
   *
   * @param changedComponent The name of the component being modified.
   * @param newComponentValue The string representing the new value for the modified component
   *     (e.g., "Optional.of(newValue)").
   * @param allComponents The list of all components in the record.
   * @return A string representing the complete argument list for the record's constructor.
   */
  protected String generateConstructorArgs(
      final String changedComponent,
      final String newComponentValue,
      final List<? extends RecordComponentElement> allComponents) {

    return allComponents.stream()
        .map(
            c -> {
              final String name = c.getSimpleName().toString();
              return name.equals(changedComponent) ? newComponentValue : "source." + name + "()";
            })
        .collect(Collectors.joining(", "));
  }
}
