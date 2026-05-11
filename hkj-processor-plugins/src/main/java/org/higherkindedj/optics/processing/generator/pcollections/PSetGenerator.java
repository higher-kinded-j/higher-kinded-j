// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.pcollections;

import io.avaje.spi.ServiceProvider;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields of type {@code
 * org.pcollections.PSet}. Reconstruction uses {@code HashTreePSet.from(Collection)}.
 */
@ServiceProvider(TraversableGenerator.class)
public final class PSetGenerator extends PCollectionsBaseSingleIterableTraversableGenerator {

  /** Creates a new generator for {@code PSet} fields. */
  public PSetGenerator() {
    super(PSET, HASH_TREE_PSET);
  }
}
