// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.spi;

import com.palantir.javapoet.TypeName;
import java.util.Objects;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Where a generator's code is written, and the round's element utilities that read its types: what
 * a {@code modifyF} body needs to name a type, handed over by the processor that calls {@link
 * TraversableGenerator#generateModifyF(javax.lang.model.element.RecordComponentElement,
 * com.palantir.javapoet.ClassName, java.util.List, GenerationContext)}.
 *
 * <p>Name each type the body writes out with {@link #typeNameOf}. A type-use annotation the author
 * wrote on it is kept where the file can write it cleanly, and left off where it cannot, such as
 * one the target package cannot name, one missing from the classpath, or one deprecated, by
 * {@code @Deprecated} or by a javadoc {@code @deprecated} tag alone, since it would draw a warning
 * in the generated file that no one can suppress.
 *
 * <p>A context is a handle on one compilation, not a value: it has no equality of its own. Keep it
 * no longer than the call that hands it over.
 *
 * @since 0.6.0
 */
public final class GenerationContext {

  private final String targetPackage;
  private final Elements elements;

  /**
   * A context for a file written into {@code targetPackage}.
   *
   * @param targetPackage the package the generated file is written into
   * @param elements the round's element utilities, which read a deprecation
   * @throws NullPointerException if either is null
   */
  public GenerationContext(String targetPackage, Elements elements) {
    this.targetPackage = Objects.requireNonNull(targetPackage, "targetPackage");
    this.elements = Objects.requireNonNull(elements, "elements");
  }

  /**
   * The package the generated file is written into.
   *
   * @return the package name (non-null)
   */
  public String targetPackage() {
    return targetPackage;
  }

  /**
   * The round's element utilities, which read a deprecation.
   *
   * @return the element utilities (non-null)
   */
  public Elements elements() {
    return elements;
  }

  /**
   * The name of a type as written, with the type-use annotations this file can write cleanly kept,
   * as {@link ProcessorUtils#typeNameOf(Elements, TypeMirror, String)} names it for {@link
   * #targetPackage}.
   *
   * @param type the type to name; must not be null
   * @return its name (non-null)
   */
  public TypeName typeNameOf(TypeMirror type) {
    return ProcessorUtils.typeNameOf(elements, type, targetPackage);
  }
}
