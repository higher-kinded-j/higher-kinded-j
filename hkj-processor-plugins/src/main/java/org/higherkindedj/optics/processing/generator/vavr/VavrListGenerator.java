// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.vavr;

import io.avaje.spi.ServiceProvider;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields that are Vavr's {@code
 * List}.
 */
@ServiceProvider(TraversableGenerator.class)
public final class VavrListGenerator extends VavrBaseSingleIterableTraversableGenerator {

  public VavrListGenerator() {
    super(LIST, LIST);
  }
}
