// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.eclipse;

import io.avaje.spi.ServiceProvider;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields that are Eclipse
 * Collections' {@code ImmutableBag}.
 */
@ServiceProvider(TraversableGenerator.class)
public final class EclipseImmutableBagGenerator
    extends EclipseBaseSingleIterableTraversableGenerator {

  public EclipseImmutableBagGenerator() {
    super(IMMUTABLE_BAG, true, BAGS_API);
  }
}
