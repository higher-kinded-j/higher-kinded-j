// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import javax.lang.model.type.TypeMirror;

/**
 * Represents a detected container type for traversal generation.
 *
 * <p>This record captures information about container fields (List, Set, Optional, arrays, Map)
 * that can have traversals generated for them.
 *
 * @param kind the kind of container
 * @param elementType the type of elements in the container
 * @param keyType for Map containers, the key type; null otherwise
 */
public record ContainerType(Kind kind, TypeMirror elementType, TypeMirror keyType) {

  /** The kind of container. */
  public enum Kind {
    /** A {@code java.util.List<E>} container. */
    LIST,

    /** A {@code java.util.Set<E>} container. */
    SET,

    /** A {@code java.util.Optional<E>} container. */
    OPTIONAL,

    /** A Java array type {@code E[]}. */
    ARRAY,

    /** A {@code java.util.Map<K, V>} container. Traversal is over values only. */
    MAP
  }

  /**
   * Creates a ContainerType for a single-element container (List, Set, Optional, array).
   *
   * @param kind the container kind
   * @param elementType the element type
   * @return a new ContainerType
   */
  public static ContainerType of(Kind kind, TypeMirror elementType) {
    return new ContainerType(kind, elementType, null);
  }

  /**
   * Creates a ContainerType for a Map container.
   *
   * @param keyType the key type
   * @param valueType the value type (used as element type for traversal)
   * @return a new ContainerType for Map
   */
  public static ContainerType forMap(TypeMirror keyType, TypeMirror valueType) {
    return new ContainerType(Kind.MAP, valueType, keyType);
  }

  /**
   * Returns whether this is a Map container.
   *
   * @return true if this is a Map container
   */
  public boolean isMap() {
    return kind == Kind.MAP;
  }
}
