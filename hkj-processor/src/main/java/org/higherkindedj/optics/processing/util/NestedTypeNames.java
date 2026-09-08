// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import java.util.HashSet;
import java.util.Set;

/**
 * Names the nested classes one generated holder declares, each distinct from the rest.
 *
 * <p>A name is the component's, capitalised, followed by the kind of optic it implements ({@code
 * ItemsTraversal}). Two components can capitalise to the same word, since {@code items} and {@code
 * Items} are distinct identifiers, so a name already claimed takes the first free numeric suffix
 * instead ({@code ItemsTraversal2}). The holder's own simple name is claimed before any other, so a
 * nested class never repeats it, whatever kind a caller passes.
 */
public final class NestedTypeNames {

  private final Set<String> claimed = new HashSet<>();

  /**
   * Starts naming for one holder.
   *
   * @param holderSimpleName the simple name of the class the nested types are declared in
   */
  public NestedTypeNames(String holderSimpleName) {
    claimed.add(holderSimpleName);
  }

  /**
   * Claims a name for the nested class implementing one component's optic.
   *
   * @param componentName the record component's name
   * @param kind the optic's kind, used as the suffix ({@code "Traversal"}, {@code "Fold"})
   * @return the name, distinct from every name claimed before it
   */
  public String claim(String componentName, String kind) {
    String base = ProcessorUtils.capitalise(componentName) + kind;
    String name = base;
    for (int suffix = 2; !claimed.add(name); suffix++) {
      name = base + suffix;
    }
    return name;
  }
}
