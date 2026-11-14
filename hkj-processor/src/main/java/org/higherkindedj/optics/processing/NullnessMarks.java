// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;

/**
 * The JSpecify annotations a generated helper declares what it does with {@code null} by, so that a
 * nullness checker run over the generated code holds each call to the nullness its argument has: a
 * guard takes a {@code @Nullable} value, and an element type that may be {@code null} is bounded
 * {@code extends @Nullable Object}.
 *
 * <p>They are written only where the code they are generated into can read them, which is decided
 * from the module of the element the code is generated for: a module that does not read {@code
 * org.jspecify}, or a classpath without it, gets each helper as it would be with the annotations
 * left out, which is the same Java.
 *
 * @param present whether the annotations are written
 */
record NullnessMarks(boolean present) {

  private static final ClassName NULLABLE = ClassName.get("org.jspecify.annotations", "Nullable");
  private static final ClassName NON_NULL = ClassName.get("org.jspecify.annotations", "NonNull");

  /**
   * The marks for code generated for {@code site}: written when JSpecify's {@code Nullable} can be
   * named from the module {@code site} is in, or from the classpath when it is in none.
   *
   * @param elements the round's element utilities
   * @param site the element the code is generated for, such as a spec
   * @return the marks to write
   */
  static NullnessMarks of(Elements elements, Element site) {
    return new NullnessMarks(
        elements.getTypeElement(elements.getModuleOf(site), NULLABLE.canonicalName()) != null);
  }

  /** {@code type} marked {@code @Nullable}, where the marks are written. */
  TypeName nullable(TypeName type) {
    return present ? type.annotated(AnnotationSpec.builder(NULLABLE).build()) : type;
  }

  /** {@code type} marked {@code @NonNull}, where the marks are written. */
  TypeName nonNull(TypeName type) {
    return present ? type.annotated(AnnotationSpec.builder(NON_NULL).build()) : type;
  }

  /**
   * A type variable whose argument may be a nullable type: {@code E extends @Nullable Object}, or a
   * plain {@code E} where the marks are not written.
   */
  TypeVariableName nullableBounded(String name) {
    return present
        ? TypeVariableName.get(name, nullable(ClassName.OBJECT))
        : TypeVariableName.get(name);
  }
}
