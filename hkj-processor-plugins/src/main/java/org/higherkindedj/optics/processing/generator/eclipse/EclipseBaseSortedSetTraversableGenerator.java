// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.eclipse;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.OptionalInt;

/**
 * A base for the Traversable Generation for Sorted Sets from the Eclipse Collections.
 *
 * <p>Both the traversal and the Focus path rebuild the set with the source's comparator, so a set
 * kept in a custom order stays in it, and an element with no natural order is sorted as the source
 * sorted it. A set in natural order has a null comparator, which the factory reads as natural order
 * again.
 */
public abstract class EclipseBaseSortedSetTraversableGenerator
    extends EclipseBaseSingleIterableTraversableGenerator {

  /** Package name for Eclipse Collections sorted set interfaces. */
  public static final String SORTED_SET_PACKAGE = SET_PACKAGE + ".sorted";

  /** Class name for the Eclipse Collections {@code SortedSets} factory. */
  public static final ClassName SORTED_SETS_API = ClassName.get(FACTORY_PACKAGE, "SortedSets");

  /** Class name for Eclipse Collections {@code ImmutableSortedSet}. */
  public static final ClassName IMMUTABLE_SORTED_SET =
      ClassName.get(SORTED_SET_PACKAGE, "ImmutableSortedSet");

  /** Class name for Eclipse Collections {@code MutableSortedSet}. */
  public static final ClassName MUTABLE_SORTED_SET =
      ClassName.get(SORTED_SET_PACKAGE, "MutableSortedSet");

  /**
   * Creates a new generator for an Eclipse Collections sorted set type.
   *
   * @param supportedElement the sorted set element type to support
   * @param immutable whether the target sorted set is immutable
   * @param api the factory class used to create sorted set instances
   */
  protected EclipseBaseSortedSetTraversableGenerator(
      final ClassName supportedElement, final boolean immutable, final ClassName api) {
    super(supportedElement, immutable, api);
  }

  @Override
  public String generateOpticExpression() {
    return "EachInstances.fromIterableRebuilding((source, list) -> "
        + api.simpleName()
        + "."
        + mutability()
        + ".ofAll(source.comparator(), list))";
  }

  /** A sorted set is ordered by its element. */
  @Override
  public OptionalInt getOrderedTypeArgumentIndex() {
    return OptionalInt.of(0);
  }

  @Override
  protected CodeBlock rebuild(final String componentName) {
    return CodeBlock.of(
        "$T.$L.ofAll(source.$L().comparator(), newList)", api, mutability(), componentName);
  }
}
