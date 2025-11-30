// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article3.transform;

import org.higherkindedj.article3.ast.BinaryOp;
import org.higherkindedj.article3.ast.Expr;
import org.higherkindedj.article3.ast.Expr.Binary;
import org.higherkindedj.article3.ast.Expr.Conditional;
import org.higherkindedj.article3.ast.Expr.Literal;

/**
 * Expression optimiser implementing constant folding and identity simplification.
 *
 * <p>This demonstrates how optics patterns apply to AST transformations.
 */
public final class ExprOptimiser {
  private ExprOptimiser() {}

  /**
   * Optimise an expression by repeatedly applying all optimisations until a fixed point.
   *
   * @param expr the expression to optimise
   * @return the optimised expression
   */
  public static Expr optimise(Expr expr) {
    Expr result = expr;
    Expr previous;

    // Run until fixed point (no more changes)
    do {
      previous = result;
      result = ExprTransform.transformBottomUp(result, ExprOptimiser::optimiseNode);
    } while (!result.equals(previous));

    return result;
  }

  /** Apply all optimisations to a single node. */
  private static Expr optimiseNode(Expr expr) {
    Expr result = expr;
    result = foldConstants(result);
    result = simplifyIdentities(result);
    result = simplifyConditionals(result);
    return result;
  }

  /**
   * Constant folding: evaluate constant expressions at compile time.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code 1 + 2} → {@code 3}
   *   <li>{@code true && false} → {@code false}
   * </ul>
   */
  public static Expr foldConstants(Expr expr) {
    if (expr instanceof Binary(Literal(Object lv), BinaryOp op, Literal(Object rv))) {
      Object result = evaluate(lv, op, rv);
      if (result != null) {
        return new Literal(result);
      }
    }
    return expr;
  }

  /**
   * Simplify identity operations.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code x + 0} → {@code x}
   *   <li>{@code x * 1} → {@code x}
   *   <li>{@code x * 0} → {@code 0}
   *   <li>{@code x && true} → {@code x}
   *   <li>{@code x || false} → {@code x}
   * </ul>
   */
  public static Expr simplifyIdentities(Expr expr) {
    if (expr instanceof Binary(var left, BinaryOp op, Literal(Object rv))) {
      // x + 0 = x, x - 0 = x
      if ((op == BinaryOp.ADD || op == BinaryOp.SUB) && rv.equals(0)) {
        return left;
      }
      // x * 1 = x, x / 1 = x
      if ((op == BinaryOp.MUL || op == BinaryOp.DIV) && rv.equals(1)) {
        return left;
      }
      // x * 0 = 0
      if (op == BinaryOp.MUL && rv.equals(0)) {
        return new Literal(0);
      }
      // x && true = x
      if (op == BinaryOp.AND && rv.equals(true)) {
        return left;
      }
      // x || false = x
      if (op == BinaryOp.OR && rv.equals(false)) {
        return left;
      }
      // x && false = false
      if (op == BinaryOp.AND && rv.equals(false)) {
        return new Literal(false);
      }
      // x || true = true
      if (op == BinaryOp.OR && rv.equals(true)) {
        return new Literal(true);
      }
    }

    if (expr instanceof Binary(Literal(Object lv), BinaryOp op, var right)) {
      // 0 + x = x
      if (op == BinaryOp.ADD && lv.equals(0)) {
        return right;
      }
      // 1 * x = x
      if (op == BinaryOp.MUL && lv.equals(1)) {
        return right;
      }
      // 0 * x = 0
      if (op == BinaryOp.MUL && lv.equals(0)) {
        return new Literal(0);
      }
      // true && x = x
      if (op == BinaryOp.AND && lv.equals(true)) {
        return right;
      }
      // false || x = x
      if (op == BinaryOp.OR && lv.equals(false)) {
        return right;
      }
      // false && x = false
      if (op == BinaryOp.AND && lv.equals(false)) {
        return new Literal(false);
      }
      // true || x = true
      if (op == BinaryOp.OR && lv.equals(true)) {
        return new Literal(true);
      }
    }

    return expr;
  }

  /**
   * Simplify conditional expressions with constant conditions.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code if true then a else b} → {@code a}
   *   <li>{@code if false then a else b} → {@code b}
   * </ul>
   */
  public static Expr simplifyConditionals(Expr expr) {
    if (expr instanceof Conditional(Literal(Object cv), var thenBranch, var elseBranch)) {
      if (cv.equals(true)) {
        return thenBranch;
      }
      if (cv.equals(false)) {
        return elseBranch;
      }
    }
    return expr;
  }

  /** Evaluate a binary operation on literal values. */
  private static Object evaluate(Object left, BinaryOp op, Object right) {
    // Integer arithmetic
    if (left instanceof Integer l && right instanceof Integer r) {
      return switch (op) {
        case ADD -> l + r;
        case SUB -> l - r;
        case MUL -> l * r;
        case DIV -> r != 0 ? l / r : null;
        case EQ -> l.equals(r);
        case NE -> !l.equals(r);
        case LT -> l < r;
        case LE -> l <= r;
        case GT -> l > r;
        case GE -> l >= r;
        default -> null;
      };
    }

    // Boolean logic
    if (left instanceof Boolean l && right instanceof Boolean r) {
      return switch (op) {
        case AND -> l && r;
        case OR -> l || r;
        case EQ -> l.equals(r);
        case NE -> !l.equals(r);
        default -> null;
      };
    }

    // String concatenation
    if (left instanceof String l && right instanceof String r && op == BinaryOp.ADD) {
      return l + r;
    }

    // Equality for any type
    if (op == BinaryOp.EQ) {
      return left.equals(right);
    }
    if (op == BinaryOp.NE) {
      return !left.equals(right);
    }

    return null;
  }
}
