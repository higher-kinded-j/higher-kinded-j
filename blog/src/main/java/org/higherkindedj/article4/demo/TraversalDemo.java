// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article4.demo;

import static org.higherkindedj.article4.ast.BinaryOp.*;

import java.util.List;
import java.util.Set;
import org.higherkindedj.article4.ast.BinaryOp;
import org.higherkindedj.article4.ast.Expr;
import org.higherkindedj.article4.ast.Expr.Binary;
import org.higherkindedj.article4.ast.Expr.Conditional;
import org.higherkindedj.article4.ast.Expr.Literal;
import org.higherkindedj.article4.ast.Expr.Variable;
import org.higherkindedj.article4.traversal.ExprFold;
import org.higherkindedj.article4.traversal.ExprTraversal;
import org.higherkindedj.article4.traversal.NodeCounts;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

/**
 * Demonstrates traversals for expression trees from Article 4.
 *
 * <p>This demo shows:
 *
 * <ul>
 *   <li>Using higher-kinded-j's {@link Traversal} interface for tree operations
 *   <li>Effect-polymorphic traversals via {@link Traversal#modifyF}
 *   <li>Bottom-up and top-down tree transformations
 *   <li>Collecting information with folds
 *   <li>Finding variables, operators, and common subexpressions
 * </ul>
 *
 * <p>The traversals use higher-kinded-j's optics library, demonstrating how functional optics
 * enable composable, type-safe tree manipulation.
 */
public final class TraversalDemo {

  public static void main(String[] args) {
    System.out.println("=== Traversal Demo (Article 4) ===\n");

    childrenTraversal();
    bottomUpTransformation();
    topDownTransformation();
    informationCollection();
    commonSubexpressions();
  }

  private static void childrenTraversal() {
    System.out.println("--- Children Traversal (higher-kinded-j) ---\n");

    // Get the children traversal from ExprTraversal - this implements higher-kinded-j's Traversal
    Traversal<Expr, Expr> children = ExprTraversal.children();

    // A literal has no children
    Expr lit = new Literal(42);
    System.out.println("Literal 42 children: " + Traversals.getAll(children, lit));

    // A variable has no children
    Expr var = new Variable("x");
    System.out.println("Variable x children: " + Traversals.getAll(children, var));

    // A binary has two children
    Expr bin = new Binary(new Variable("a"), ADD, new Literal(1));
    System.out.println("Binary (a + 1) children: " + formatExprs(Traversals.getAll(children, bin)));

    // A conditional has three children
    Expr cond = new Conditional(new Variable("flag"), new Literal(1), new Literal(0));
    System.out.println(
        "Conditional (if flag then 1 else 0) children: "
            + formatExprs(Traversals.getAll(children, cond)));

    // Modify children of a binary expression using Traversals.modify
    Expr modified = Traversals.modify(children, e -> new Binary(e, MUL, new Literal(2)), bin);
    System.out.println("\nModify children of (a + 1) by wrapping in (* 2):");
    System.out.println("  Before: " + bin.format());
    System.out.println("  After:  " + modified.format());

    System.out.println();
  }

  private static void bottomUpTransformation() {
    System.out.println("--- Bottom-Up Transformation ---\n");

    // Build: (x + 1) * (y + 2)
    Expr expr =
        new Binary(
            new Binary(new Variable("x"), ADD, new Literal(1)),
            MUL,
            new Binary(new Variable("y"), ADD, new Literal(2)));

    System.out.println("Original: " + expr.format());

    // Transform: negate all literals
    Expr negated =
        ExprTraversal.transformBottomUp(
            expr,
            e ->
                switch (e) {
                  case Literal(Integer i) -> new Literal(-i);
                  default -> e;
                });

    System.out.println("Negate literals: " + negated.format());

    // Transform: add suffix to all variable names
    Expr renamed =
        ExprTraversal.transformBottomUp(
            expr, e -> e instanceof Variable(var n) ? new Variable(n + "_new") : e);

    System.out.println("Rename variables: " + renamed.format());

    // Transform: wrap all variables in (var + 0)
    Expr wrapped =
        ExprTraversal.transformBottomUp(
            expr, e -> e instanceof Variable v ? new Binary(v, ADD, new Literal(0)) : e);

    System.out.println("Wrap variables: " + wrapped.format());

    System.out.println();
  }

  private static void topDownTransformation() {
    System.out.println("--- Top-Down Transformation ---\n");

    // Build: if (x > 0) then x else (0 - x)
    Expr absX =
        new Conditional(
            new Binary(new Variable("x"), GT, new Literal(0)),
            new Variable("x"),
            new Binary(new Literal(0), SUB, new Variable("x")));

    System.out.println("Original: " + absX.format());

    // Top-down: Replace conditional with custom message
    // (In real use, might do macro expansion or early rewriting)
    Expr transformed =
        ExprTraversal.transformTopDown(
            absX,
            e ->
                switch (e) {
                  case Conditional(var c, var t, var el) ->
                      new Conditional(c, new Binary(t, MUL, new Literal(1)), el);
                  default -> e;
                });

    System.out.println("Top-down modified: " + transformed.format());

    // Compare: bottom-up vs top-down order matters for some transforms
    System.out.println("\nBottom-up visits children before parent.");
    System.out.println("Top-down visits parent before children.");
    System.out.println("Choose based on whether you need to see original vs transformed children.");

    System.out.println();
  }

  private static void informationCollection() {
    System.out.println("--- Information Collection ---\n");

    // Build: if (x > y) then (x + 1) * z else (y - z) + w
    Expr expr =
        new Conditional(
            new Binary(new Variable("x"), GT, new Variable("y")),
            new Binary(new Binary(new Variable("x"), ADD, new Literal(1)), MUL, new Variable("z")),
            new Binary(
                new Binary(new Variable("y"), SUB, new Variable("z")), ADD, new Variable("w")));

    System.out.println("Expression: " + expr.format());
    System.out.println();

    // Find all variables
    Set<String> vars = ExprFold.findVariables(expr);
    System.out.println("Variables: " + vars);

    // Find all literals
    Set<Object> literals = ExprFold.findLiterals(expr);
    System.out.println("Literals: " + literals);

    // Find all operators
    Set<BinaryOp> ops = ExprFold.findOperators(expr);
    System.out.println("Operators: " + ops);

    // Count nodes
    NodeCounts counts = ExprFold.countNodes(expr);
    System.out.println("Node counts: " + counts);

    // Calculate depth
    int depth = ExprFold.depth(expr);
    System.out.println("Tree depth: " + depth);

    // Check if constant
    System.out.println("Is constant: " + ExprFold.isConstant(expr));

    // Try with a constant expression
    Expr constant =
        new Binary(new Literal(1), ADD, new Binary(new Literal(2), MUL, new Literal(3)));
    System.out.println("\nConstant expression: " + constant.format());
    System.out.println("Is constant: " + ExprFold.isConstant(constant));

    System.out.println();
  }

  private static void commonSubexpressions() {
    System.out.println("--- Common Subexpressions ---\n");

    // Build: (x + 1) + (x + 1) * (x + 1)
    Expr xPlusOne = new Binary(new Variable("x"), ADD, new Literal(1));
    Expr expr = new Binary(xPlusOne, ADD, new Binary(xPlusOne, MUL, xPlusOne));

    System.out.println("Expression: " + expr.format());
    System.out.println();

    // Find common subexpressions
    var common = ExprFold.findCommonSubexpressions(expr);
    System.out.println("Common subexpressions (appearing more than once):");
    for (var entry : common.entrySet()) {
      System.out.println(
          "  " + entry.getKey().format() + " appears " + entry.getValue() + " times");
    }

    System.out.println("\nThis information could be used for:");
    System.out.println("  - Let-binding common expressions");
    System.out.println("  - Caching repeated computations");
    System.out.println("  - Identifying optimisation opportunities");

    System.out.println();
  }

  private static String formatExprs(List<Expr> exprs) {
    return exprs.stream().map(Expr::format).toList().toString();
  }
}
