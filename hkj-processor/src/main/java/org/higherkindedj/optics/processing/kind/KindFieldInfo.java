// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.kind;

import com.palantir.javapoet.TypeName;
import org.higherkindedj.optics.annotations.KindSemantics;

/**
 * Contains the analysis results for a {@code Kind<F, A>} field.
 *
 * <p>This record is produced by {@link KindFieldAnalyser} and consumed by the Focus processor to
 * generate appropriate traversal code.
 *
 * @param witnessType the fully qualified name of the witness type (e.g., "ListKind.Witness")
 * @param elementType the TypeName of the element type within the Kind
 * @param traverseExpression the code expression to obtain the Traverse instance
 * @param semantics the cardinality semantics determining path type
 * @param isParameterised whether the witness type has type parameters (e.g., EitherKind.Witness)
 * @param witnessTypeArgs type arguments for parameterised witness types (may be empty)
 */
public record KindFieldInfo(
    String witnessType,
    TypeName elementType,
    String traverseExpression,
    KindSemantics semantics,
    boolean isParameterised,
    String witnessTypeArgs) {

  /**
   * Creates a KindFieldInfo for a non-parameterised witness type.
   *
   * @param witnessType the witness type name
   * @param elementType the element type
   * @param traverseExpression the traverse instance expression
   * @param semantics the cardinality semantics
   * @return a new KindFieldInfo
   */
  public static KindFieldInfo of(
      String witnessType,
      TypeName elementType,
      String traverseExpression,
      KindSemantics semantics) {
    return new KindFieldInfo(witnessType, elementType, traverseExpression, semantics, false, "");
  }

  /**
   * Creates a KindFieldInfo for a parameterised witness type.
   *
   * @param witnessType the witness type name
   * @param elementType the element type
   * @param traverseExpression the traverse instance expression
   * @param semantics the cardinality semantics
   * @param witnessTypeArgs the type arguments string
   * @return a new KindFieldInfo
   */
  public static KindFieldInfo parameterised(
      String witnessType,
      TypeName elementType,
      String traverseExpression,
      KindSemantics semantics,
      String witnessTypeArgs) {
    return new KindFieldInfo(
        witnessType, elementType, traverseExpression, semantics, true, witnessTypeArgs);
  }
}
