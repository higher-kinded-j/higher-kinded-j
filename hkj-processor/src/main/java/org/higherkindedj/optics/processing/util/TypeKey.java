// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * A type as a processor holds it across rounds: by module and canonical name, since two modules
 * compiled together may declare the same canonical name, and an element is looked up afresh by it
 * each round rather than kept from an earlier one.
 *
 * @param module the qualified name of the module declaring the type, empty for the unnamed module
 * @param name the type's canonical name
 * @since 0.4.11
 */
public record TypeKey(String module, String name) {

  /**
   * The key of a type element.
   *
   * @param elements the compilation's element utilities; must not be null
   * @param type the type; must not be null
   * @return its key (non-null)
   */
  public static TypeKey of(Elements elements, Element type) {
    return new TypeKey(
        elements.getModuleOf(type).getQualifiedName().toString(),
        ((TypeElement) type).getQualifiedName().toString());
  }

  /**
   * The type this key names, as the current round presents it.
   *
   * @param elements the compilation's element utilities; must not be null
   * @return the type (non-null)
   * @throws java.util.NoSuchElementException if the compilation no longer declares it
   */
  public TypeElement in(Elements elements) {
    return elements.getAllTypeElements(name).stream()
        .filter(type -> elements.getModuleOf(type).getQualifiedName().contentEquals(module))
        .findFirst()
        .orElseThrow();
  }
}
