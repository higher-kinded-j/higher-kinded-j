// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.apache;

import io.avaje.spi.ServiceProvider;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields that are Apache
 * Collections 4 {@code UnmodifiableList}.
 */
@ServiceProvider(TraversableGenerator.class)
public final class ApacheUnmodifiableListGenerator
    extends ApacheBaseSingleIterableTraversableGenerator {

  /** Creates a new generator for Apache Commons Collections {@code UnmodifiableList} fields. */
  public ApacheUnmodifiableListGenerator() {
    super(UNMODIFIABLE_LIST);
  }
}
