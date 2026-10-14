// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * A lens's focus as the copy strategies' checks read it: the type a check holds a read or a call
 * to, whether a value the lens sets unboxes to a given primitive, and a getter's read as a focus
 * declares it.
 *
 * <p>Split from {@link CopyStrategyChecks}, so that its checks, {@link SetterChecks} and {@link
 * ConstructorOrderChecks} read a focus alike.
 */
final class LensFocus {

  private LensFocus() {}

  /**
   * The focus as a check reads it, for the value a lens passes or the one its accessor reads: the
   * focus itself, or null for a focus written as a wildcard, whose type javac infers at the call
   * from what the accessor reads, rather than holding the accessor to it.
   */
  static TypeMirror focusRead(TypeMirror focusType) {
    return ProcessorUtils.unlessWildcard(focusType);
  }

  /**
   * Whether the value a lens sets unboxes to {@code read}: the focus itself, or for a focus written
   * as a wildcard, its lower bound. javac infers {@code ?} and {@code ? extends Number} as the
   * getter's own wrapper, which always does; {@code ? super Number} as no narrower than {@code
   * Number}, which does not.
   */
  static boolean unboxesTo(Types types, TypeMirror focusType, TypeMirror read) {
    TypeMirror passed =
        focusType.getKind() == TypeKind.WILDCARD
            ? ((WildcardType) focusType).getSuperBound()
            : focusType;
    return passed == null || types.isAssignable(passed, read);
  }

  /**
   * A type as a lens's focus declares it: a primitive as its wrapper, since a focus is a type
   * argument, and anything else as it is.
   */
  static TypeMirror asFocus(Types types, TypeMirror read) {
    return read.getKind().isPrimitive() ? types.boxedClass((PrimitiveType) read).asType() : read;
  }
}
