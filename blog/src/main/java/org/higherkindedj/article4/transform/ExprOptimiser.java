// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article4.transform;

import static org.higherkindedj.article4.ast.BinaryOp.*;
import static org.higherkindedj.article4.traversal.ExprTraversal.transformBottomUp;

import java.util.Optional;
import org.higherkindedj.article4.ast.BinaryOp;
import org.higherkindedj.article4.ast.Expr;
import org.higherkindedj.article4.ast.Expr.Binary;
import org.higherkindedj.article4.ast.Expr.Conditional;
import org.higherkindedj.article4.ast.Expr.Literal;

/**
 * Expression optimiser using traversal-based transformations.
 *
 * <p>This optimiser applies multiple passes using bottom-up tree traversal:
 *
 * <ul>
 *   <li><b>Constant folding</b> — evaluate operations where both operands are literals
 *   <li><b>Identity simplification</b> — remove redundant operations (x + 0 → x)
 *   <li><b>Dead branch elimination</b> — remove conditionals with constant conditions
 * </ul>
 *
 * <p>The optimiser runs all passes repeatedly until the expression stops changing (fixed point).
 */
public final class ExprOptimiser {

  private ExprOptimiser() {}

  /**
   * Run all optimisation passes until the expression stops changing.
   *
   * @param expr the expression to optimise
   * @return the optimised expression
   */
  public static Expr optimise(Expr expr) {
    Expr current = expr;
    Expr previous;
    int iterations = 0;
    int maxIterations = 100; // Safety limit

    do {
      previous = current;
      current = runAllPasses(current);
      iterations++;
    } while (!current.equals(previous) && iterations < maxIterations);

    return current;
  }

  /**
   * Run a single round of all optimisation passes.
   *
   * @param expr the expression to optimise
   * @return the expression after one round of all passes
   */
  public static Expr runAllPasses(Expr expr) {
    Expr result = expr;
    result = foldConstants(result);
    result = simplifyIdentities(result);
    result = eliminateDeadBranches(result);
    return result;
  }

  // ========== Pass 1: Constant Folding ==========

  /**
   * Evaluate operations where both operands are constant literals.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code 1 + 2} → {@code 3}
   *   <li>{@code true && false} → {@code false}
   *   <li>{@code 10 > 5} → {@code true}
   *   <li>{@code "hello" + " world"} → {@code "hello world"}
   * </ul>
   */
  public static Expr foldConstants(Expr expr) {
    return transformBottomUp(expr, ExprOptimiser::foldConstant);
  }

  private static Expr foldConstant(Expr expr) {
    if (expr instanceof Binary(Literal(var l), var op, Literal(var r))) {
      return evaluateBinary(l, op, r).map(result -> (Expr) new Literal(result)).orElse(expr);
    }
    return expr;
  }

  private static Optional<Object> evaluateBinary(Object left, BinaryOp op, Object right) {
    return switch (op) {
      case ADD -> evaluateAdd(left, right);
      case SUB -> evaluateSub(left, right);
      case MUL -> evaluateMul(left, right);
      case DIV -> evaluateDiv(left, right);
      case AND -> evaluateAnd(left, right);
      case OR -> evaluateOr(left, right);
      case EQ -> Optional.of(left.equals(right));
      case NE -> Optional.of(!left.equals(right));
      case LT, LE, GT, GE -> evaluateComparison(left, op, right);
    };
  }

  private static Optional<Object> evaluateAdd(Object left, Object right) {
    if (left instanceof Integer l && right instanceof Integer r) {
      return Optional.of(l + r);
    }
    if (left instanceof String l && right instanceof String r) {
      return Optional.of(l + r);
    }
    // String concatenation with mixed types
    if (left instanceof String || right instanceof String) {
      return Optional.of(String.valueOf(left) + String.valueOf(right));
    }
    return Optional.empty();
  }

  private static Optional<Object> evaluateSub(Object left, Object right) {
    if (left instanceof Integer l && right instanceof Integer r) {
      return Optional.of(l - r);
    }
    return Optional.empty();
  }

  private static Optional<Object> evaluateMul(Object left, Object right) {
    if (left instanceof Integer l && right instanceof Integer r) {
      return Optional.of(l * r);
    }
    return Optional.empty();
  }

  private static Optional<Object> evaluateDiv(Object left, Object right) {
    if (left instanceof Integer l && right instanceof Integer r && r != 0) {
      return Optional.of(l / r);
    }
    return Optional.empty();
  }

  private static Optional<Object> evaluateAnd(Object left, Object right) {
    if (left instanceof Boolean l && right instanceof Boolean r) {
      return Optional.of(l && r);
    }
    return Optional.empty();
  }

  private static Optional<Object> evaluateOr(Object left, Object right) {
    if (left instanceof Boolean l && right instanceof Boolean r) {
      return Optional.of(l || r);
    }
    return Optional.empty();
  }

  @SuppressWarnings("unchecked")
  private static Optional<Object> evaluateComparison(Object left, BinaryOp op, Object right) {
    if (left instanceof Comparable l && right instanceof Comparable r) {
      try {
        int cmp = l.compareTo(r);
        return Optional.of(
            switch (op) {
              case LT -> cmp < 0;
              case LE -> cmp <= 0;
              case GT -> cmp > 0;
              case GE -> cmp >= 0;
              default -> throw new IllegalStateException("Not a comparison: " + op);
            });
      } catch (ClassCastException e) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  // ========== Pass 2: Identity Simplification ==========

  /**
   * Remove operations that don't change the result.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code x + 0} → {@code x}
   *   <li>{@code x * 1} → {@code x}
   *   <li>{@code x * 0} → {@code 0}
   *   <li>{@code x && true} → {@code x}
   *   <li>{@code x || false} → {@code x}
   *   <li>{@code x && false} → {@code false}
   *   <li>{@code x || true} → {@code true}
   * </ul>
   */
  public static Expr simplifyIdentities(Expr expr) {
    return transformBottomUp(expr, ExprOptimiser::simplifyIdentity);
  }

  private static Expr simplifyIdentity(Expr expr) {
    return switch (expr) {
      // x + 0 → x, 0 + x → x
      case Binary(var x, var op, Literal(Integer i)) when op == ADD && i == 0 -> x;
      case Binary(Literal(Integer i), var op, var x) when op == ADD && i == 0 -> x;

      // x - 0 → x
      case Binary(var x, var op, Literal(Integer i)) when op == SUB && i == 0 -> x;

      // x * 1 → x, 1 * x → x
      case Binary(var x, var op, Literal(Integer i)) when op == MUL && i == 1 -> x;
      case Binary(Literal(Integer i), var op, var x) when op == MUL && i == 1 -> x;

      // x * 0 → 0, 0 * x → 0
      case Binary(_, var op, Literal(Integer i)) when op == MUL && i == 0 -> new Literal(0);
      case Binary(Literal(Integer i), var op, _) when op == MUL && i == 0 -> new Literal(0);

      // x / 1 → x
      case Binary(var x, var op, Literal(Integer i)) when op == DIV && i == 1 -> x;

      // x && true → x, true && x → x
      case Binary(var x, var op, Literal(Boolean b)) when op == AND && b -> x;
      case Binary(Literal(Boolean b), var op, var x) when op == AND && b -> x;

      // x || false → x, false || x → x
      case Binary(var x, var op, Literal(Boolean b)) when op == OR && !b -> x;
      case Binary(Literal(Boolean b), var op, var x) when op == OR && !b -> x;

      // x && false → false
      case Binary(_, var op, Literal(Boolean b)) when op == AND && !b -> new Literal(false);
      case Binary(Literal(Boolean b), var op, _) when op == AND && !b -> new Literal(false);

      // x || true → true
      case Binary(_, var op, Literal(Boolean b)) when op == OR && b -> new Literal(true);
      case Binary(Literal(Boolean b), var op, _) when op == OR && b -> new Literal(true);

      default -> expr;
    };
  }

  // ========== Pass 3: Dead Branch Elimination ==========

  /**
   * Remove conditional branches with constant conditions.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code if true then a else b} → {@code a}
   *   <li>{@code if false then a else b} → {@code b}
   * </ul>
   */
  public static Expr eliminateDeadBranches(Expr expr) {
    return transformBottomUp(expr, ExprOptimiser::eliminateDeadBranch);
  }

  private static Expr eliminateDeadBranch(Expr expr) {
    return switch (expr) {
      case Conditional(Literal(Boolean b), var t, var e) -> b ? t : e;
      default -> expr;
    };
  }
}
