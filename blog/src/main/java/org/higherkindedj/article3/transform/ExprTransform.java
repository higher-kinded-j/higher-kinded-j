// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article3.transform;

import java.util.function.Function;
import org.higherkindedj.article3.ast.Expr;
import org.higherkindedj.article3.ast.Expr.Binary;
import org.higherkindedj.article3.ast.Expr.Conditional;
import org.higherkindedj.article3.ast.Expr.Literal;
import org.higherkindedj.article3.ast.Expr.Variable;

/**
 * Utilities for transforming expression trees.
 *
 * <p>This class demonstrates the data-oriented programming approach to tree transformations.
 * Instead of embedding transformation logic inside each AST node (visitor pattern), we use pattern
 * matching over the sealed interface to handle each variant externally.
 *
 * <p>In Article 4, we'll replace this with proper traversals. For now, this provides a reusable
 * recursive transformation pattern.
 */
public final class ExprTransform {
  private ExprTransform() {}

  /**
   * Transform an expression tree bottom-up.
   *
   * <p>First recursively transforms all children, then applies the transformation to the result.
   *
   * @param expr the expression to transform
   * @param transform the transformation to apply at each node
   * @return the transformed expression
   */
  public static Expr transformBottomUp(Expr expr, Function<Expr, Expr> transform) {
    // First, recursively transform children
    // Java 22+: Multi-pattern case combining leaf nodes
    Expr transformed =
        switch (expr) {
          case Literal(_), Variable(_) -> expr;
          case Binary(var l, var op, var r) ->
              new Binary(transformBottomUp(l, transform), op, transformBottomUp(r, transform));
          case Conditional(var c, var thenBranch, var elseBranch) ->
              new Conditional(
                  transformBottomUp(c, transform),
                  transformBottomUp(thenBranch, transform),
                  transformBottomUp(elseBranch, transform));
        };

    // Then apply the transformation to this node
    return transform.apply(transformed);
  }

  /**
   * Transform an expression tree top-down.
   *
   * <p>First applies the transformation, then recursively transforms children.
   *
   * @param expr the expression to transform
   * @param transform the transformation to apply at each node
   * @return the transformed expression
   */
  public static Expr transformTopDown(Expr expr, Function<Expr, Expr> transform) {
    // First apply the transformation
    Expr transformed = transform.apply(expr);

    // Then recursively transform children
    // Java 22+: Multi-pattern case combining leaf nodes
    return switch (transformed) {
      case Literal(_), Variable(_) -> transformed;
      case Binary(var l, var op, var r) ->
          new Binary(transformTopDown(l, transform), op, transformTopDown(r, transform));
      case Conditional(var c, var thenBranch, var elseBranch) ->
          new Conditional(
              transformTopDown(c, transform),
              transformTopDown(thenBranch, transform),
              transformTopDown(elseBranch, transform));
    };
  }
}
