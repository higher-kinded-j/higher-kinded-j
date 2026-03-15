// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.apache;

import com.palantir.javapoet.ClassName;
import org.higherkindedj.optics.processing.generator.BaseTraversableGenerator;

/// Base of Traversable Generators for Apache Collections that are both:
///  * A collection of 0...n, not 1
///  * Of a single type/column
public abstract class BaseApacheSingleIterableTraversableGenerator
    extends BaseTraversableGenerator {
  protected static final String PACKAGE = "org.apache.commons.collections4";
  protected static final String BAG_PACKAGE = PACKAGE + ".bag";
  protected static final String LIST_PACKAGE = PACKAGE + ".list";

  public static final ClassName HASH_BAG = ClassName.get(BAG_PACKAGE, "HashBag");
  public static final ClassName UNMODIFIABLE_LIST = ClassName.get(LIST_PACKAGE, "UnmodifiableList");
  // Unfortunately, the return type of making both `UnmodifiableBag` and `UnmodifiableSet` is
  //   `Bag` and `Set` respectively, which means we would probably need to do an unsafe cast to
  //   properly convert them.
}
