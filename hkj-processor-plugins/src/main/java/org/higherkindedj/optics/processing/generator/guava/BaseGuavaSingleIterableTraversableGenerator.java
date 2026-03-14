// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.guava;

import com.palantir.javapoet.ClassName;
import org.higherkindedj.optics.processing.generator.BaseTraversableGenerator;

public abstract class BaseGuavaSingleIterableTraversableGenerator extends BaseTraversableGenerator {
  private static final String COLLECTIONS_PACKAGE = "com.google.common.collect";

  public static final ClassName IMMUTABLE_LIST =
      ClassName.get(COLLECTIONS_PACKAGE, "ImmutableList");
  public static final ClassName IMMUTABLE_SET = ClassName.get(COLLECTIONS_PACKAGE, "ImmutableSet");
}
