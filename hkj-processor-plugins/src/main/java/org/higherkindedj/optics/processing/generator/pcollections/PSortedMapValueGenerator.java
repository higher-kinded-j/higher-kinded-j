// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.pcollections;

import io.avaje.spi.ServiceProvider;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * A {@link TraversableGenerator} that adds support for traversing the values of fields of type
 * {@code org.pcollections.PSortedMap}. Reconstruction uses {@code TreePMap.from(Map)} which applies
 * natural key ordering. Custom comparators are not preserved through the traversal.
 */
@ServiceProvider(TraversableGenerator.class)
public final class PSortedMapValueGenerator extends PCollectionsBaseMapValueTraversableGenerator {

  /** Creates a new generator for {@code PSortedMap} fields. */
  public PSortedMapValueGenerator() {
    super(PSORTED_MAP, TREE_PMAP);
  }
}
