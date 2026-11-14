// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.openrewrite.java.JavaIsoVisitor;
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

  /**
   * Whether a lambda returns {@code null}: its body is {@code null}, or one of its own {@code
   * return}s gives it, as {@link #ownReturns} finds them.
   */
  static boolean returnsNull(J.Lambda lambda) {
    return switch (lambda.getBody()) {
      case Expression body -> isNullLiteral(body);
      case J.Block block -> ownReturns(block).stream().anyMatch(NullLiterals::isNullLiteral);
      default -> false;
    };
  }

  /** The lambda with the mark on each {@code null} it returns, as {@link #markReturned} puts it. */
  static J.Lambda markNullReturns(J.Lambda lambda, String message) {
    return markReturned(lambda, NullLiterals::isNullLiteral, message);
  }

  /**
   * The lambda with the mark on each value it returns that {@code marks} accepts: on its body, or
   * on what each of its own {@code return}s gives. A mark there sits on the line that gives the
   * value, where one on the call would print from the start of its chain.
   */
  static J.Lambda markReturned(J.Lambda lambda, Predicate<Expression> marks, String message) {
    return switch (lambda.getBody()) {
      case Expression body when marks.test(body) ->
          lambda.withBody(SearchResult.found(body, message));
      case J.Block block ->
          lambda.withBody(
              new OwnReturns<Integer>() {
                @Override
                public J.Return visitReturn(J.Return ret, Integer p) {
                  J.Return r = super.visitReturn(ret, p);
                  return r.getExpression() != null && marks.test(r.getExpression())
                      ? r.withExpression(SearchResult.found(r.getExpression(), message))
                      : r;
                }
              }.visit(block, 0));
      default -> lambda;
    };
  }

  /**
   * What each of a lambda block's own {@code return}s gives, at any depth: in a branch or a loop,
   * but not in a lambda or a class declared inside it, whose returns are their own.
   */
  private static List<Expression> ownReturns(J.Block block) {
    List<Expression> returned = new ArrayList<>();
    new OwnReturns<List<Expression>>() {
      @Override
      public J.Return visitReturn(J.Return ret, List<Expression> found) {
        if (ret.getExpression() != null) {
          found.add(ret.getExpression());
        }
        return super.visitReturn(ret, found);
      }
    }.visit(block, returned);
    return returned;
  }

  /** A visitor that keeps to a lambda's own body, leaving each lambda and class inside it alone. */
  private abstract static class OwnReturns<P> extends JavaIsoVisitor<P> {
    @Override
    public J.Lambda visitLambda(J.Lambda lambda, P p) {
      return lambda;
    }

    @Override
    public J.NewClass visitNewClass(J.NewClass newClass, P p) {
      return newClass.getBody() == null ? super.visitNewClass(newClass, p) : newClass;
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
      return classDecl;
    }
  }
}
