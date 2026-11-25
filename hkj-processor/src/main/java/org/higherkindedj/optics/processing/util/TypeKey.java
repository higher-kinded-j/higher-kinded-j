// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * A type as a processor holds it across rounds: by module and canonical name, since two modules
 * compiled together may declare the same canonical name, and an element is looked up afresh by it
 * each round rather than kept from an earlier one. A compilation with no modules, such as one under
 * {@code -source 8}, places a type in none, and its key names the unnamed module's, which is empty.
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
        moduleOf(elements, type), ((TypeElement) type).getQualifiedName().toString());
  }

  /**
   * The type this key names, as the current round presents it.
   *
   * @param elements the compilation's element utilities; must not be null
   * @return the type (non-null)
   * @throws java.util.NoSuchElementException if the compilation no longer declares it
   */
  public TypeElement in(Elements elements) {
    // Asked of the module that declares it, the one lookup finds it; with no modules, the name
    // alone does.
    ModuleElement declaring = elements.getModuleElement(module);
    return Optional.ofNullable(
            declaring == null
                ? elements.getTypeElement(name)
                : elements.getTypeElement(declaring, name))
        .orElseThrow();
  }

  /**
   * The qualified name of the module an element is declared in: empty for the unnamed module, and
   * where the compilation has no modules, as under {@code -source 8}, where javac places an element
   * in none.
   *
   * @param elements the compilation's element utilities; must not be null
   * @param element the element; must not be null
   * @return the module's name (non-null)
   * @since 0.6.0
   */
  public static String moduleOf(Elements elements, Element element) {
    return Optional.ofNullable(elements.getModuleOf(element))
        .map(module -> module.getQualifiedName().toString())
        .orElse("");
  }
}
