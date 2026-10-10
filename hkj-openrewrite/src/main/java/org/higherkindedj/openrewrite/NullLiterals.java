// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

/** Finds the {@code null} literals the null-detection recipes mark. */
final class NullLiterals {

  private NullLiterals() {}

  /** Whether an expression is {@code null}, cast or in brackets, as in {@code (String) null}. */
  static boolean isNullLiteral(Expression expression) {
    return switch (expression) {
      case J.TypeCast cast -> isNullLiteral(cast.getExpression());
      case J.Parentheses<?> brackets ->
          brackets.getTree() instanceof Expression inner && isNullLiteral(inner);
      case J.Literal literal ->
          literal.getValue() == null && literal.getType() == JavaType.Primitive.Null;
      default -> false;
    };
  }

  /** Whether a lambda's body is {@code null}, or a block with a top-level {@code return null}. */
  static boolean returnsNull(J.Lambda lambda) {
    if (lambda.getBody() instanceof Expression body) {
      return isNullLiteral(body);
    }
    if (lambda.getBody() instanceof J.Block block) {
      for (Statement statement : block.getStatements()) {
        if (statement instanceof J.Return ret
            && ret.getExpression() != null
            && isNullLiteral(ret.getExpression())) {
          return true;
        }
      }
    }
    return false;
  }
}
