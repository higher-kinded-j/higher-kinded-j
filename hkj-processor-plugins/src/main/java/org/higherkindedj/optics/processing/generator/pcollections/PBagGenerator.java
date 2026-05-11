// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.pcollections;

import io.avaje.spi.ServiceProvider;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields of type {@code
 * org.pcollections.PBag}. Reconstruction uses {@code HashTreePBag.from(Collection)}.
 */
@ServiceProvider(TraversableGenerator.class)
public final class PBagGenerator extends PCollectionsBaseSingleIterableTraversableGenerator {

  /** Creates a new generator for {@code PBag} fields. */
  public PBagGenerator() {
    super(PBAG, HASH_TREE_PBAG);
  }
}
