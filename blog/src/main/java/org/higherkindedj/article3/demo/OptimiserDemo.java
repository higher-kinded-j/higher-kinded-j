// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article3.demo;

import org.higherkindedj.article3.ast.BinaryOp;
import org.higherkindedj.article3.ast.Expr;
import org.higherkindedj.article3.ast.Expr.Binary;
import org.higherkindedj.article3.ast.Expr.Conditional;
import org.higherkindedj.article3.ast.Expr.Literal;
import org.higherkindedj.article3.ast.Expr.Variable;
import org.higherkindedj.article3.transform.ExprOptimiser;

/**
 * Demonstrates expression optimisation from Article 3.
 *
 * <p>This demo shows:
 *
 * <ul>
 *   <li>Constant folding: evaluating constant expressions at compile time
 *   <li>Identity simplification: removing redundant operations
 *   <li>Conditional simplification: eliminating branches with constant conditions
 *   <li>Bottom-up transformation: optimising from leaves to root
 * </ul>
 */
public final class OptimiserDemo {

  public static void main(String[] args) {
    System.out.println("=== Expression Optimiser Demo (Article 3) ===\n");

    constantFolding();
    identitySimplification();
    conditionalSimplification();
    complexOptimisation();
  }

  private static void constantFolding() {
    System.out.println("--- Constant Folding ---\n");

    // 1 + 2 → 3
    Expr simple = new Binary(new Literal(1), BinaryOp.ADD, new Literal(2));
    showOptimisation("1 + 2", simple);

    // (2 * 3) + (4 * 5) → 26
    Expr nested =
        new Binary(
            new Binary(new Literal(2), BinaryOp.MUL, new Literal(3)),
            BinaryOp.ADD,
            new Binary(new Literal(4), BinaryOp.MUL, new Literal(5)));
    showOptimisation("(2 * 3) + (4 * 5)", nested);

    // true && false → false
    Expr boolExpr = new Binary(new Literal(true), BinaryOp.AND, new Literal(false));
    showOptimisation("true && false", boolExpr);

    // 10 > 5 → true
    Expr comparison = new Binary(new Literal(10), BinaryOp.GT, new Literal(5));
    showOptimisation("10 > 5", comparison);

    // "hello" + " " + "world" → "hello world"
    Expr stringConcat =
        new Binary(
            new Binary(new Literal("hello"), BinaryOp.ADD, new Literal(" ")),
            BinaryOp.ADD,
            new Literal("world"));
    showOptimisation("\"hello\" + \" \" + \"world\"", stringConcat);

    System.out.println();
  }

  private static void identitySimplification() {
    System.out.println("--- Identity Simplification ---\n");

    Expr x = new Variable("x");

    // x + 0 → x
    Expr addZero = new Binary(x, BinaryOp.ADD, new Literal(0));
    showOptimisation("x + 0", addZero);

    // 0 + x → x
    Expr zeroAdd = new Binary(new Literal(0), BinaryOp.ADD, x);
    showOptimisation("0 + x", zeroAdd);

    // x * 1 → x
    Expr mulOne = new Binary(x, BinaryOp.MUL, new Literal(1));
    showOptimisation("x * 1", mulOne);

    // x * 0 → 0
    Expr mulZero = new Binary(x, BinaryOp.MUL, new Literal(0));
    showOptimisation("x * 0", mulZero);

    // x && true → x
    Expr andTrue = new Binary(x, BinaryOp.AND, new Literal(true));
    showOptimisation("x && true", andTrue);

    // x || false → x
    Expr orFalse = new Binary(x, BinaryOp.OR, new Literal(false));
    showOptimisation("x || false", orFalse);

    // x && false → false
    Expr andFalse = new Binary(x, BinaryOp.AND, new Literal(false));
    showOptimisation("x && false", andFalse);

    // true || x → true
    Expr trueOr = new Binary(new Literal(true), BinaryOp.OR, x);
    showOptimisation("true || x", trueOr);

    System.out.println();
  }

  private static void conditionalSimplification() {
    System.out.println("--- Conditional Simplification ---\n");

    Expr a = new Variable("a");
    Expr b = new Variable("b");

    // if true then a else b → a
    Expr ifTrue = new Conditional(new Literal(true), a, b);
    showOptimisation("if true then a else b", ifTrue);

    // if false then a else b → b
    Expr ifFalse = new Conditional(new Literal(false), a, b);
    showOptimisation("if false then a else b", ifFalse);

    // if (1 < 2) then a else b → a (after constant folding)
    Expr ifComputed =
        new Conditional(new Binary(new Literal(1), BinaryOp.LT, new Literal(2)), a, b);
    showOptimisation("if (1 < 2) then a else b", ifComputed);

    System.out.println();
  }

  private static void complexOptimisation() {
    System.out.println("--- Complex Optimisation ---\n");

    Expr x = new Variable("x");
    Expr y = new Variable("y");

    // (x + 0) * 1 + (2 * 3) → x + 6
    Expr complex1 =
        new Binary(
            new Binary(new Binary(x, BinaryOp.ADD, new Literal(0)), BinaryOp.MUL, new Literal(1)),
            BinaryOp.ADD,
            new Binary(new Literal(2), BinaryOp.MUL, new Literal(3)));
    showOptimisation("(x + 0) * 1 + (2 * 3)", complex1);

    // if (true && true) then (x * 1) else (y + 0) → x
    Expr complex2 =
        new Conditional(
            new Binary(new Literal(true), BinaryOp.AND, new Literal(true)),
            new Binary(x, BinaryOp.MUL, new Literal(1)),
            new Binary(y, BinaryOp.ADD, new Literal(0)));
    showOptimisation("if (true && true) then (x * 1) else (y + 0)", complex2);

    // Nested conditionals with constant conditions
    // if (1 > 0) then (if false then x else y) else z → y
    Expr complex3 =
        new Conditional(
            new Binary(new Literal(1), BinaryOp.GT, new Literal(0)),
            new Conditional(new Literal(false), x, y),
            new Variable("z"));
    showOptimisation("if (1 > 0) then (if false then x else y) else z", complex3);

    // (0 * x) + (y * 0) + 5 → 5
    Expr complex4 =
        new Binary(
            new Binary(
                new Binary(new Literal(0), BinaryOp.MUL, x),
                BinaryOp.ADD,
                new Binary(y, BinaryOp.MUL, new Literal(0))),
            BinaryOp.ADD,
            new Literal(5));
    showOptimisation("(0 * x) + (y * 0) + 5", complex4);

    System.out.println();
  }

  private static void showOptimisation(String description, Expr expr) {
    Expr optimised = ExprOptimiser.optimise(expr);
    System.out.println(description);
    System.out.println("  Before: " + expr.format());
    System.out.println("  After:  " + optimised.format());
  }
}
