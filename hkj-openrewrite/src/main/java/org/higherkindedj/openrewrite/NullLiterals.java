// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.SearchResult;

/**
 * Finds the {@code null} literals, and the values a lambda or a method returns, that the null
 * recipes mark or replace.
 */
final class NullLiterals {

  private NullLiterals() {}

  /** Whether an expression is {@code null}, cast or in brackets, as in {@code (String) null}. */
  static boolean isNullLiteral(Expression expression) {
    return bare(expression) instanceof J.Literal literal
        && literal.getValue() == null
        && literal.getType() == JavaType.Primitive.Null;
  }

  /**
   * Whether an expression can only be {@code null}: a {@code null} literal, as {@link
   * #isNullLiteral} finds it, or a value typed {@code Void}, such as a call to a method that
   * returns {@code Void}.
   */
  static boolean isAlwaysNull(Expression expression) {
    return isNullLiteral(expression) || isVoidType(expression.getType());
  }

  /** Whether a type is {@code Void} itself, not an array of it, as isOfClassType would accept. */
  static boolean isVoidType(JavaType type) {
    return type instanceof JavaType.FullyQualified named
        && !(type instanceof JavaType.Parameterized)
        && "java.lang.Void".equals(named.getFullyQualifiedName());
  }

  /**
   * The expression inside any brackets around it, but not a cast, as the lambda is inside {@code
   * (() -> null)}.
   */
  static Expression unbracketed(Expression expression) {
    return expression instanceof J.Parentheses<?> brackets
            && brackets.getTree() instanceof Expression inner
        ? unbracketed(inner)
        : expression;
  }

  /**
   * The expression with what is inside its brackets put through {@code map}, keeping the brackets;
   * the expression itself put through {@code map} where it has none.
   */
  static Expression inBrackets(Expression expression, UnaryOperator<Expression> map) {
    if (!(expression instanceof J.Parentheses<?> brackets)
        || !(brackets.getTree() instanceof Expression inner)) {
      return map.apply(expression);
    }
    Expression mapped = inBrackets(inner, map);
    if (mapped == inner) {
      return expression;
    }
    JRightPadded<?> padded = brackets.getPadding().getTree();
    return new J.Parentheses<>(
        brackets.getId(),
        brackets.getPrefix(),
        brackets.getMarkers(),
        new JRightPadded<>(mapped, padded.getAfter(), padded.getMarkers()));
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
    return mapReturned(
        lambda, value -> marks.test(value) ? SearchResult.found(value, message) : value);
  }

  /**
   * The lambda with each value it returns, as its body or in one of its own {@code return}s, put
   * through {@code map}.
   */
  static J.Lambda mapReturned(J.Lambda lambda, UnaryOperator<Expression> map) {
    return switch (lambda.getBody()) {
      case J.Block block -> lambda.withBody(mapOwnReturns(block, map));
      case Expression body -> lambda.withBody(map.apply(body));
      default -> lambda;
    };
  }

  /**
   * The block with what each of its own {@code return}s gives put through {@code map}, as {@link
   * #ownReturns} finds them: a method's body, or a lambda's.
   */
  static J.Block mapOwnReturns(J.Block block, UnaryOperator<Expression> map) {
    return (J.Block)
        new OwnReturns<Integer>() {
          @Override
          public J.Return visitReturn(J.Return ret, Integer p) {
            J.Return r = super.visitReturn(ret, p);
            return r.getExpression() == null ? r : r.withExpression(map.apply(r.getExpression()));
          }
        }.visitNonNull(block, 0);
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

  /**
   * A visitor that keeps to a lambda's or a method's own body, leaving each lambda and class inside
   * it alone.
   */
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
