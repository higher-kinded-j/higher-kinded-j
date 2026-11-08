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
 * natural order is sorted as the source sorted it. PCollections' own sets in natural order report
 * that order as their comparator, so it is kept too. The {@code PSortedSet} interface allows an
 * implementation of its own to report a null comparator for natural order, which {@code TreePSet}
 * refuses.
 */
@ServiceProvider(TraversableGenerator.class)
public final class PSortedSetGenerator extends PCollectionsBaseSingleIterableTraversableGenerator {

  /** Creates a new generator for {@code PSortedSet} fields. */
  public PSortedSetGenerator() {
    super(PSORTED_SET, TREE_PSET);
  }

  @Override
  public String generateOpticExpression() {
    return "EachInstances.fromIterableRebuilding((source, list) -> "
        + constructedType.simpleName()
        + ".from(source.comparator(), list))";
  }

  /** A sorted set is ordered by its element. */
  @Override
  public OptionalInt getOrderedTypeArgumentIndex() {
    return OptionalInt.of(0);
  }

  @Override
  protected CodeBlock rebuild(final String componentName) {
    return CodeBlock.of(
        "$T.from(source.$L().comparator(), newList)", constructedType, componentName);
  }
}
