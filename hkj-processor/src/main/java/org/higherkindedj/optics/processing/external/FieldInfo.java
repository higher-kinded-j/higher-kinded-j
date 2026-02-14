// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.Optional;
import javax.lang.model.type.TypeMirror;

/**
 * Information about a field for which a lens or traversal can be generated.
 *
 * <p>This record captures the essential details needed to generate optics for a single field,
 * including its name, type, accessor method, and optional container type information.
 *
 * @param name the field name (also used as the generated method name)
 * @param type the field's type as a TypeMirror
 * @param accessorMethod the name of the method to access this field (e.g., "name" for records)
 * @param copyStrategy the strategy for creating modified copies
 * @param containerType if the field is a container, details about the container type
 */
public record FieldInfo(
    String name,
    TypeMirror type,
    String accessorMethod,
    CopyStrategy copyStrategy,
    Optional<ContainerType> containerType) {

  /**
   * Creates a FieldInfo for a record component.
   *
   * @param name the component name
   * @param type the component type
   * @return a new FieldInfo configured for a record component
   */
  public static FieldInfo forRecordComponent(String name, TypeMirror type) {
    return new FieldInfo(name, type, name, CopyStrategy.CANONICAL_CONSTRUCTOR, Optional.empty());
  }

  /**
   * Creates a FieldInfo for a record component with container type.
   *
   * @param name the component name
   * @param type the component type
   * @param containerType the detected container type
   * @return a new FieldInfo configured for a record component with container
   */
  public static FieldInfo forRecordComponent(
      String name, TypeMirror type, ContainerType containerType) {
    return new FieldInfo(
        name, type, name, CopyStrategy.CANONICAL_CONSTRUCTOR, Optional.of(containerType));
  }

  /**
   * Creates a FieldInfo for a field accessed via a getter method.
   *
   * @param name the field name
   * @param type the field type
   * @param getterName the name of the getter method
   * @param copyStrategy the strategy for copying
   * @return a new FieldInfo configured for a getter-based field
   */
  public static FieldInfo forGetter(
      String name, TypeMirror type, String getterName, CopyStrategy copyStrategy) {
    return new FieldInfo(name, type, getterName, copyStrategy, Optional.empty());
  }

  /**
   * Creates a FieldInfo for a field accessed via a getter method with container type.
   *
   * @param name the field name
   * @param type the field type
   * @param getterName the name of the getter method
   * @param copyStrategy the strategy for copying
   * @param containerType the detected container type
   * @return a new FieldInfo configured for a getter-based field with container
   */
  public static FieldInfo forGetter(
      String name,
      TypeMirror type,
      String getterName,
      CopyStrategy copyStrategy,
      ContainerType containerType) {
    return new FieldInfo(name, type, getterName, copyStrategy, Optional.of(containerType));
  }

  /**
   * Returns whether this field should also have a traversal generated.
   *
   * @return true if the field is a container type that supports traversal
   */
  public boolean hasTraversal() {
    return containerType.isPresent();
  }
}
