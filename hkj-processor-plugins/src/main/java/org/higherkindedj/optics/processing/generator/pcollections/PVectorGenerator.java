// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.pcollections;

import io.avaje.spi.ServiceProvider;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields of type {@code
 * org.pcollections.PVector}. Reconstruction uses {@code TreePVector.from(Collection)}.
 */
@ServiceProvider(TraversableGenerator.class)
public final class PVectorGenerator extends PCollectionsBaseSingleIterableTraversableGenerator {

  /** Creates a new generator for {@code PVector} fields. */
  public PVectorGenerator() {
    super(PVECTOR, TREE_PVECTOR);
  }
}
