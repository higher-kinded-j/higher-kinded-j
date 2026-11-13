// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.SearchResult;

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
    return switch (lambda.getBody()) {
      case Expression body -> isNullLiteral(body);
      case J.Block block -> block.getStatements().stream().anyMatch(NullLiterals::isReturnNull);
      default -> false;
    };
  }

  /**
   * The lambda with the mark on each {@code null} it returns, where {@link #returnsNull} found it:
   * on its body, or on each top-level {@code return null} of its block. A mark there sits on the
   * line that returns the null, where one on the call would print from the start of its chain.
   */
  static J.Lambda markNullReturns(J.Lambda lambda, String message) {
    return switch (lambda.getBody()) {
      case Expression body when isNullLiteral(body) ->
          lambda.withBody(SearchResult.found(body, message));
      case J.Block block ->
          lambda.withBody(
              block.withStatements(
                  ListUtils.map(
                      block.getStatements(),
                      statement ->
                          isReturnNull(statement)
                              ? SearchResult.found(statement, message)
                              : statement)));
      default -> lambda;
    };
  }

  private static boolean isReturnNull(Statement statement) {
    return statement instanceof J.Return ret
        && ret.getExpression() != null
        && isNullLiteral(ret.getExpression());
  }
}
