// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.vavr;

import com.palantir.javapoet.ClassName;
import org.higherkindedj.optics.processing.generator.BaseTraversableGenerator;

/// Base of Traversable Generators for Vavr's collections that are both:
///  * A collection of 0...n, not 1
///  * Of a single type/column
public abstract class BaseVavrSingleIterableTraversableGenerator extends BaseTraversableGenerator {
  private static final String COLLECTION_PACKAGE = "io.vavr.collection";

  public static final ClassName SET = ClassName.get(COLLECTION_PACKAGE, "Set");
  public static final ClassName HASH_SET = ClassName.get(COLLECTION_PACKAGE, "HashSet");
  public static final ClassName LIST = ClassName.get(COLLECTION_PACKAGE, "List");
}
