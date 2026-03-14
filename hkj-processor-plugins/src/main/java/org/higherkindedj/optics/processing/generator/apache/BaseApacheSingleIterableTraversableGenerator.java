// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.apache;

import com.palantir.javapoet.ClassName;
import org.higherkindedj.optics.processing.generator.BaseTraversableGenerator;

public abstract class BaseApacheSingleIterableTraversableGenerator
    extends BaseTraversableGenerator {
  protected static final String PACKAGE = "org.apache.commons.collections4";
  protected static final String BAG_PACKAGE = PACKAGE + ".bag";
  protected static final String LIST_PACKAGE = PACKAGE + ".list";
  protected static final String SET_PACKAGE = PACKAGE + ".set";

  public static final ClassName UNMODIFIABLE_BAG = ClassName.get(BAG_PACKAGE, "UnmodifiableBag");
  public static final ClassName HASH_BAG = ClassName.get(BAG_PACKAGE, "HashBag");
  public static final ClassName UNMODIFIABLE_LIST = ClassName.get(LIST_PACKAGE, "UnmodifiableList");
  public static final ClassName UNMODIFIABLE_SET = ClassName.get(SET_PACKAGE, "UnmodifiableSet");
}
