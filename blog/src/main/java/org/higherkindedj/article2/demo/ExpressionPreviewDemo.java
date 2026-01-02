// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.demo;

/**
 * Preview of the Expression Language domain from Article 2.
 *
 * <p>This demo introduces the AST structure that will be fully developed in Articles 3-5, where
 * Higher-Kinded-J's {@code @GenerateLenses} and {@code @GeneratePrisms} annotations will generate
 * all the optics automatically.
 *
 * @see <a href="../../docs/article-2-optics-fundamentals.md">Article 2: Optics Fundamentals</a>
 */
public final class ExpressionPreviewDemo {

  // ========== AST Definition ==========

  /** Binary operators. */
  public enum BinaryOp {
    ADD,
    SUB,
    MUL,
    DIV,
    EQ,
    LT,
    GT,
    AND,
    OR
  }

  /**
   * The expression AST - a sealed interface with record variants.
   *
   * <p>In Article 3, this will be annotated with {@code @GeneratePrisms} to generate prisms for
   * each variant, and each record will use {@code @GenerateLenses}.
   */
  public sealed interface Expr {
    record Literal(Object value) implements Expr {}

    record Variable(String name) implements Expr {}

    record Binary(Expr left, BinaryOp op, Expr right) implements Expr {}

    record Conditional(Expr cond, Expr then_, Expr else_) implements Expr {}
  }

  // ========== Demo ==========

  public static void main(String[] args) {
    System.out.println("=== Expression Language Preview (Article 2) ===\n");

    buildingExpressions();
    patternMatchingPreview();
    transformationPreview();
  }

  private static void buildingExpressions() {
    System.out.println("--- Building Expressions ---\n");

    // 1 + 2
    Expr simple = new Expr.Binary(new Expr.Literal(1), BinaryOp.ADD, new Expr.Literal(2));
    System.out.println("1 + 2: " + format(simple));

    // x * (y + 1)
    Expr nested =
        new Expr.Binary(
            new Expr.Variable("x"),
            BinaryOp.MUL,
            new Expr.Binary(new Expr.Variable("y"), BinaryOp.ADD, new Expr.Literal(1)));
    System.out.println("x * (y + 1): " + format(nested));

    // if (x > 0) then x else -x
    Expr conditional =
        new Expr.Conditional(
            new Expr.Binary(new Expr.Variable("x"), BinaryOp.GT, new Expr.Literal(0)),
            new Expr.Variable("x"),
            new Expr.Binary(new Expr.Literal(0), BinaryOp.SUB, new Expr.Variable("x")));
    System.out.println("if (x > 0) then x else -x: " + format(conditional));
    System.out.println();
  }

  private static void patternMatchingPreview() {
    System.out.println("--- Pattern Matching (Java 21+) ---\n");

    Expr literal = new Expr.Literal(42);
    Expr variable = new Expr.Variable("x");
    Expr binary = new Expr.Binary(new Expr.Literal(1), BinaryOp.ADD, new Expr.Literal(2));

    // Java's pattern matching excels at reading
    System.out.println("Pattern matching for reading:");
    System.out.println("  " + describe(literal));
    System.out.println("  " + describe(variable));
    System.out.println("  " + describe(binary));

    System.out.println("\nBut for writing/transforming, we need optics!");
    System.out.println("(Coming in Article 3 with @GenerateLenses and @GeneratePrisms)");
    System.out.println();
  }

  private static String describe(Expr expr) {
    return switch (expr) {
      case Expr.Literal(var v) -> "Literal with value: " + v;
      case Expr.Variable(var n) -> "Variable named: " + n;
      case Expr.Binary(var l, var op, var r) ->
          "Binary " + op + " with left=" + describe(l) + ", right=" + describe(r);
      case Expr.Conditional(var c, var t, var e) -> "Conditional expression";
    };
  }

  private static void transformationPreview() {
    System.out.println("--- Transformation Preview ---\n");

    // What we want to achieve in Article 3:
    System.out.println("In Article 3, with generated optics, we'll be able to:");
    System.out.println();
    System.out.println("  // Rename all variables from 'x' to 'a'");
    System.out.println("  Expr renamed = ExprTraversals.allVariables()");
    System.out.println("      .andThen(VariableLenses.name())");
    System.out.println("      .modify(n -> n.equals(\"x\") ? \"a\" : n, expr);");
    System.out.println();
    System.out.println("  // Constant folding: evaluate 1 + 2 to 3");
    System.out.println("  Expr folded = ExprTraversals.allBinaries()");
    System.out.println("      .modify(ConstantFolder::fold, expr);");
    System.out.println();
    System.out.println("  // Collect all variable names in the expression");
    System.out.println("  List<String> vars = Traversals.getAll(");
    System.out.println(
        "      ExprTraversals.allVariables().andThen(VariableLenses.name()), expr);");
    System.out.println();
    System.out.println("Stay tuned for the full implementation!");
    System.out.println();
  }

  /** Simple expression formatter for demo output. */
  private static String format(Expr expr) {
    return switch (expr) {
      case Expr.Literal(var v) -> v.toString();
      case Expr.Variable(var n) -> n;
      case Expr.Binary(var l, var op, var r) ->
          "(" + format(l) + " " + formatOp(op) + " " + format(r) + ")";
      case Expr.Conditional(var c, var t, var e) ->
          "(if " + format(c) + " then " + format(t) + " else " + format(e) + ")";
    };
  }

  private static String formatOp(BinaryOp op) {
    return switch (op) {
      case ADD -> "+";
      case SUB -> "-";
      case MUL -> "*";
      case DIV -> "/";
      case EQ -> "==";
      case LT -> "<";
      case GT -> ">";
      case AND -> "&&";
      case OR -> "||";
    };
  }
}
