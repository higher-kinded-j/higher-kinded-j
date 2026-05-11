// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.pcollections;

import io.avaje.spi.ServiceProvider;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields of type {@code
 * org.pcollections.PSortedSet}. Reconstruction uses {@code TreePSet.from(Collection)} which applies
 * natural ordering. Custom comparators are not preserved through the traversal; users with bespoke
 * orderings should write their own optic.
 */
@ServiceProvider(TraversableGenerator.class)
public final class PSortedSetGenerator extends PCollectionsBaseSingleIterableTraversableGenerator {

  /** Creates a new generator for {@code PSortedSet} fields. */
  public PSortedSetGenerator() {
    super(PSORTED_SET, TREE_PSET);
  }
}
