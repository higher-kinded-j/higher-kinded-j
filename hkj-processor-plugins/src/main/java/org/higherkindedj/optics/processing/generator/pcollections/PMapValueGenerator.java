// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.pcollections;

import io.avaje.spi.ServiceProvider;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * A {@link TraversableGenerator} that adds support for traversing the values of fields of type
 * {@code org.pcollections.PMap}. Reconstruction uses {@code HashTreePMap.from(Map)}.
 */
@ServiceProvider(TraversableGenerator.class)
public final class PMapValueGenerator extends PCollectionsBaseMapValueTraversableGenerator {

  /** Creates a new generator for {@code PMap} fields. */
  public PMapValueGenerator() {
    super(PMAP, HASH_TREE_PMAP);
  }
}
