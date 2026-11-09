// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.pcollections;

import com.palantir.javapoet.CodeBlock;
import io.avaje.spi.ServiceProvider;
import java.util.OptionalInt;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields of type {@code
 * org.pcollections.PSortedSet}. Reconstruction uses {@code TreePSet.from(Comparator, Collection)}
 * with the source's comparator, so a set kept in a custom order stays in it, and an element with no
 * natural order is sorted as the source sorted it. The comparator is read through {@code
 * TreePSet.fromSortedSet}, which keeps a {@code TreePSet} as it is and reads a null comparator,
 * which {@code SortedSet} allows an implementation of its own to report, as natural order.
 */
@ServiceProvider(TraversableGenerator.class)
public final class PSortedSetGenerator extends PCollectionsBaseSingleIterableTraversableGenerator {

  /** Creates a new generator for {@code PSortedSet} fields. */
  public PSortedSetGenerator() {
    super(PSORTED_SET, TREE_PSET);
  }

  @Override
  public String generateOpticExpression() {
    final String factory = constructedType.simpleName();
    return "EachInstances.fromIterableRebuilding((source, list) -> "
        + factory
        + ".from("
        + factory
        + ".fromSortedSet(source).comparator(), list))";
  }

  /** A sorted set is ordered by its element. */
  @Override
  public OptionalInt getOrderedTypeArgumentIndex() {
    return OptionalInt.of(0);
  }

  @Override
  protected CodeBlock rebuild(final String componentName) {
    return CodeBlock.of(
        "$T.from($T.fromSortedSet(source.$L()).comparator(), newList)",
        constructedType,
        constructedType,
        componentName);
  }
}
