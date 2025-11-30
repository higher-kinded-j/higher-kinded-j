// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article4.demo;

import static org.higherkindedj.article4.ast.BinaryOp.*;

import org.higherkindedj.article4.ast.Expr;
import org.higherkindedj.article4.ast.Expr.Binary;
import org.higherkindedj.article4.ast.Expr.Conditional;
import org.higherkindedj.article4.ast.Expr.Literal;
import org.higherkindedj.article4.ast.Expr.Variable;
import org.higherkindedj.article4.transform.ExprOptimiser;

/**
 * Demonstrates expression optimisation using traversals from Article 4.
 *
 * <p>This demo shows:
 *
 * <ul>
 *   <li>Constant folding with traversal-based passes
 *   <li>Identity simplification patterns
 *   <li>Dead branch elimination
 *   <li>Cascading optimisations to fixed point
 * </ul>
 */
public final class OptimiserDemo {

  public static void main(String[] args) {
    System.out.println("=== Optimiser Demo (Article 4) ===\n");

    constantFoldingPass();
    identitySimplificationPass();
    deadBranchEliminationPass();
    cascadingOptimisation();
    complexOptimisation();
  }

  private static void constantFoldingPass() {
    System.out.println("--- Constant Folding Pass ---\n");

    // Simple arithmetic
    showPass("1 + 2", new Binary(new Literal(1), ADD, new Literal(2)));

    // Nested arithmetic: (2 * 3) + (4 * 5)
    showPass(
        "(2 * 3) + (4 * 5)",
        new Binary(
            new Binary(new Literal(2), MUL, new Literal(3)),
            ADD,
            new Binary(new Literal(4), MUL, new Literal(5))));

    // Boolean operations
    showPass("true && false", new Binary(new Literal(true), AND, new Literal(false)));

    // Comparisons
    showPass("10 > 5", new Binary(new Literal(10), GT, new Literal(5)));
    showPass("3 == 3", new Binary(new Literal(3), EQ, new Literal(3)));

    // String concatenation
    showPass(
        "\"hello\" + \" \" + \"world\"",
        new Binary(
            new Binary(new Literal("hello"), ADD, new Literal(" ")), ADD, new Literal("world")));

    // Mixed: constants and variables
    Expr mixed =
        new Binary(new Binary(new Literal(1), ADD, new Literal(2)), ADD, new Variable("x"));
    showPass("(1 + 2) + x", mixed);

    System.out.println();
  }

  private static void identitySimplificationPass() {
    System.out.println("--- Identity Simplification Pass ---\n");

    Expr x = new Variable("x");

    // Additive identity
    showPass("x + 0", new Binary(x, ADD, new Literal(0)));
    showPass("0 + x", new Binary(new Literal(0), ADD, x));

    // Multiplicative identity
    showPass("x * 1", new Binary(x, MUL, new Literal(1)));
    showPass("1 * x", new Binary(new Literal(1), MUL, x));

    // Multiplication by zero
    showPass("x * 0", new Binary(x, MUL, new Literal(0)));
    showPass("0 * x", new Binary(new Literal(0), MUL, x));

    // Boolean identities
    showPass("x && true", new Binary(x, AND, new Literal(true)));
    showPass("x || false", new Binary(x, OR, new Literal(false)));

    // Boolean absorption
    showPass("x && false", new Binary(x, AND, new Literal(false)));
    showPass("x || true", new Binary(x, OR, new Literal(true)));

    System.out.println();
  }

  private static void deadBranchEliminationPass() {
    System.out.println("--- Dead Branch Elimination Pass ---\n");

    Expr a = new Variable("a");
    Expr b = new Variable("b");

    // Constant true condition
    showPass("if true then a else b", new Conditional(new Literal(true), a, b));

    // Constant false condition
    showPass("if false then a else b", new Conditional(new Literal(false), a, b));

    // Condition that folds to constant: if (1 < 2) then a else b
    showPass(
        "if (1 < 2) then a else b",
        new Conditional(new Binary(new Literal(1), LT, new Literal(2)), a, b));

    // Nested conditionals with constant conditions
    showPass(
        "if true then (if false then 1 else 2) else 3",
        new Conditional(
            new Literal(true),
            new Conditional(new Literal(false), new Literal(1), new Literal(2)),
            new Literal(3)));

    System.out.println();
  }

  private static void cascadingOptimisation() {
    System.out.println("--- Cascading Optimisation ---\n");

    System.out.println("Multiple passes combine for deeper simplification:\n");

    // (0 * x) + (1 + 2) → 0 + 3 → 3
    Expr cascade1 =
        new Binary(
            new Binary(new Literal(0), MUL, new Variable("x")),
            ADD,
            new Binary(new Literal(1), ADD, new Literal(2)));
    showFullOptimisation("(0 * x) + (1 + 2)", cascade1);

    // (x + 0) * 1 → x * 1 → x
    Expr cascade2 =
        new Binary(new Binary(new Variable("x"), ADD, new Literal(0)), MUL, new Literal(1));
    showFullOptimisation("(x + 0) * 1", cascade2);

    // if (true && true) then (y * 1) else (z + 0) → if true then y else z → y
    Expr cascade3 =
        new Conditional(
            new Binary(new Literal(true), AND, new Literal(true)),
            new Binary(new Variable("y"), MUL, new Literal(1)),
            new Binary(new Variable("z"), ADD, new Literal(0)));
    showFullOptimisation("if (true && true) then (y * 1) else (z + 0)", cascade3);

    System.out.println();
  }

  private static void complexOptimisation() {
    System.out.println("--- Complex Optimisation ---\n");

    // Build a complex expression that benefits from multiple passes:
    // if (true && (1 < 2)) then ((x + 0) * 1 + (2 * 3)) else (y * 0 + z)
    Expr complex =
        new Conditional(
            new Binary(new Literal(true), AND, new Binary(new Literal(1), LT, new Literal(2))),
            new Binary(
                new Binary(new Binary(new Variable("x"), ADD, new Literal(0)), MUL, new Literal(1)),
                ADD,
                new Binary(new Literal(2), MUL, new Literal(3))),
            new Binary(new Binary(new Variable("y"), MUL, new Literal(0)), ADD, new Variable("z")));

    System.out.println("Complex expression:");
    System.out.println("  " + complex.format());
    System.out.println();

    System.out.println("Step by step:");
    System.out.println("1. Fold 1 < 2 → true");
    System.out.println("2. Fold true && true → true");
    System.out.println("3. Eliminate dead else branch");
    System.out.println("4. Simplify x + 0 → x");
    System.out.println("5. Simplify x * 1 → x");
    System.out.println("6. Fold 2 * 3 → 6");
    System.out.println("7. Final: x + 6");
    System.out.println();

    Expr optimised = ExprOptimiser.optimise(complex);
    System.out.println("Optimised: " + optimised.format());

    System.out.println();
  }

  private static void showPass(String description, Expr expr) {
    Expr optimised = ExprOptimiser.optimise(expr);
    System.out.printf("%-30s → %s%n", description, optimised.format());
  }

  private static void showFullOptimisation(String description, Expr expr) {
    System.out.println(description);
    System.out.println("  Before: " + expr.format());
    Expr optimised = ExprOptimiser.optimise(expr);
    System.out.println("  After:  " + optimised.format());
    System.out.println();
  }
}
