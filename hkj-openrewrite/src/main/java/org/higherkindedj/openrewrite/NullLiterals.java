// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import java.util.function.Predicate;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.SearchResult;

/** Finds the {@code null} literals, and the values a lambda returns, that the null recipes mark. */
final class NullLiterals {

  private NullLiterals() {}

  /** Whether an expression is {@code null}, cast or in brackets, as in {@code (String) null}. */
  static boolean isNullLiteral(Expression expression) {
    return bare(expression) instanceof J.Literal literal
        && literal.getValue() == null
        && literal.getType() == JavaType.Primitive.Null;
  }

  /**
   * The expression inside any brackets and casts around it, as {@code value} is inside {@code
   * ((String) value)}; {@code null} for none, as a call's absent receiver is.
   */
  static Expression bare(Expression expression) {
    Expression inner = expression;
    while (true) {
      switch (inner) {
        case J.TypeCast cast -> inner = cast.getExpression();
        case J.Parentheses<?> brackets when brackets.getTree() instanceof Expression bracketed ->
            inner = bracketed;
        case null, default -> {
          return inner;
        }
      }
    }
  }

  /** Whether a lambda's body is {@code null}, or a block with a top-level {@code return null}. */
  static boolean returnsNull(J.Lambda lambda) {
    return switch (lambda.getBody()) {
      case Expression body -> isNullLiteral(body);
      case J.Block block ->
          block.getStatements().stream()
              .anyMatch(
                  statement ->
                      statement instanceof J.Return ret
                          && ret.getExpression() != null
                          && isNullLiteral(ret.getExpression()));
      default -> false;
    };
  }

  /** The lambda with the mark on each {@code null} it returns, as {@link #markReturned} puts it. */
  static J.Lambda markNullReturns(J.Lambda lambda, String message) {
    return markReturned(lambda, NullLiterals::isNullLiteral, message);
  }

  /**
   * The lambda with the mark on each value it returns that {@code marks} accepts: on its body, or
   * on what each top-level {@code return} of its block returns. A mark there sits on the line that
   * gives the value, where one on the call would print from the start of its chain.
   */
  static J.Lambda markReturned(J.Lambda lambda, Predicate<Expression> marks, String message) {
    return switch (lambda.getBody()) {
      case Expression body when marks.test(body) ->
          lambda.withBody(SearchResult.found(body, message));
      case J.Block block ->
          lambda.withBody(
              block.withStatements(
                  ListUtils.map(
                      block.getStatements(),
                      statement ->
                          statement instanceof J.Return ret
                                  && ret.getExpression() != null
                                  && marks.test(ret.getExpression())
                              ? ret.withExpression(SearchResult.found(ret.getExpression(), message))
                              : statement)));
      default -> lambda;
    };
  }
}
