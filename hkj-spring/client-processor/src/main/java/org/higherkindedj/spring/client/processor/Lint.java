// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * A lint category a generated declaration can draw where it is written: by a copied annotation, a
 * method it overrides or calls, a type it names, or the shape in which it restates a signature.
 *
 * <p>The constants are declared in the order of their categories, so an {@code EnumSet} of them
 * reads in name order; a new one goes where its category sorts. hkj-processor's {@code util.Lint}
 * names the categories the code it generates draws, under the same rule.
 */
enum Lint {
  /** Drawn by an element that is deprecated. */
  DEPRECATION("deprecation"),
  /**
   * Drawn by a method that is varargs where a method it overrides is not, or the other way round.
   * Where the method overridden is an interface's, javac reads its suppression from the class
   * alone.
   */
  OVERRIDES("overrides"),
  /** Drawn by a raw type written out. */
  RAWTYPES("rawtypes"),
  /** Drawn by an element that is deprecated for removal. */
  REMOVAL("removal"),
  /** Drawn by a varargs parameter whose elements' type is not reifiable. */
  UNCHECKED("unchecked");

  private final String category;

  Lint(String category) {
    this.category = category;
  }

  /** The category as {@code @SuppressWarnings} names it. */
  String category() {
    return category;
  }

  /** Collects categories into an unmodifiable set read in name order, each once. */
  static Collector<Lint, ?, Set<Lint>> inNameOrder() {
    return Collectors.collectingAndThen(
        Collectors.toCollection(() -> EnumSet.noneOf(Lint.class)), Collections::unmodifiableSet);
  }
}
