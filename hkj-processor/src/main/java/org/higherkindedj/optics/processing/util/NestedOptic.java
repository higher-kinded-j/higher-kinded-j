// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;

/**
 * An optic a processor writes as two members of its holder: the private nested class that
 * implements it, and the public static factory that returns an instance of it.
 *
 * <p>The implementation is a named nested type rather than an anonymous class because a class file
 * is measured on its own annotations. An anonymous class has nowhere to carry {@code @Generated},
 * so a coverage tool counts it against the build that compiled it, while a named nested type
 * carries the marker like every other type the processors write.
 *
 * @param implementation the nested class implementing the optic
 * @param factory the static method returning a new instance of it
 */
public record NestedOptic(TypeSpec implementation, MethodSpec factory) {

  /**
   * Adds both members to the holder.
   *
   * @param holder the class the optic belongs to
   */
  public void addTo(TypeSpec.Builder holder) {
    holder.addType(implementation).addMethod(factory);
  }
}
