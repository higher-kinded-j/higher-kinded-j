// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.demo;

import java.util.Optional;
import org.higherkindedj.article2.optics.Lens;
import org.higherkindedj.article2.optics.Prism;

/**
 * Preview of the Expression Language domain from Article 2.
 *
 * <p>This demo introduces the AST structure that will be fully developed in Articles 3-5.
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

  /** The expression AST - a sealed interface with record variants. */
  public sealed interface Expr {

    record Literal(Object value) implements Expr {}

    record Variable(String name) implements Expr {}

    record Binary(Expr left, BinaryOp op, Expr right) implements Expr {}

    record Conditional(Expr cond, Expr then_, Expr else_) implements Expr {}

    // ========== Prisms for Expr variants ==========

    final class Prisms {
      private Prisms() {}

      public static Prism<Expr, Literal> literal() {
        return Prism.of(e -> e instanceof Literal l ? Optional.of(l) : Optional.empty(), l -> l);
      }

      public static Prism<Expr, Variable> variable() {
        return Prism.of(e -> e instanceof Variable v ? Optional.of(v) : Optional.empty(), v -> v);
      }

      public static Prism<Expr, Binary> binary() {
        return Prism.of(e -> e instanceof Binary b ? Optional.of(b) : Optional.empty(), b -> b);
      }

      public static Prism<Expr, Conditional> conditional() {
        return Prism.of(
            e -> e instanceof Conditional c ? Optional.of(c) : Optional.empty(), c -> c);
      }
    }
  }

  // ========== Lenses for each record ==========

  public static final class LiteralLenses {
    private LiteralLenses() {}

    public static Lens<Expr.Literal, Object> value() {
      return Lens.of(Expr.Literal::value, (v, _) -> new Expr.Literal(v));
    }
  }

  public static final class VariableLenses {
    private VariableLenses() {}

    public static Lens<Expr.Variable, String> name() {
      return Lens.of(Expr.Variable::name, (n, _) -> new Expr.Variable(n));
    }
  }

  public static final class BinaryLenses {
    private BinaryLenses() {}

    public static Lens<Expr.Binary, Expr> left() {
      return Lens.of(Expr.Binary::left, (l, b) -> new Expr.Binary(l, b.op(), b.right()));
    }

    public static Lens<Expr.Binary, BinaryOp> op() {
      return Lens.of(Expr.Binary::op, (o, b) -> new Expr.Binary(b.left(), o, b.right()));
    }

    public static Lens<Expr.Binary, Expr> right() {
      return Lens.of(Expr.Binary::right, (r, b) -> new Expr.Binary(b.left(), b.op(), r));
    }
  }

  public static final class ConditionalLenses {
    private ConditionalLenses() {}

    public static Lens<Expr.Conditional, Expr> cond() {
      return Lens.of(
          Expr.Conditional::cond, (c, cnd) -> new Expr.Conditional(c, cnd.then_(), cnd.else_()));
    }

    public static Lens<Expr.Conditional, Expr> then_() {
      return Lens.of(
          Expr.Conditional::then_, (t, cnd) -> new Expr.Conditional(cnd.cond(), t, cnd.else_()));
    }

    public static Lens<Expr.Conditional, Expr> else_() {
      return Lens.of(
          Expr.Conditional::else_, (e, cnd) -> new Expr.Conditional(cnd.cond(), cnd.then_(), e));
    }
  }

  // ========== Demo ==========

  public static void main(String[] args) {
    System.out.println("=== Expression Language Preview (Article 2) ===\n");

    buildingExpressions();
    usingPrisms();
    basicTransformations();
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

  private static void usingPrisms() {
    System.out.println("--- Using Prisms on Expressions ---\n");

    Expr literal = new Expr.Literal(42);
    Expr variable = new Expr.Variable("x");
    Expr binary = new Expr.Binary(new Expr.Literal(1), BinaryOp.ADD, new Expr.Literal(2));

    Prism<Expr, Expr.Literal> literalPrism = Expr.Prisms.literal();
    Prism<Expr, Expr.Variable> variablePrism = Expr.Prisms.variable();
    Prism<Expr, Expr.Binary> binaryPrism = Expr.Prisms.binary();

    System.out.println("literalPrism.matches(Literal(42)): " + literalPrism.matches(literal));
    System.out.println("literalPrism.matches(Variable(x)): " + literalPrism.matches(variable));
    System.out.println("binaryPrism.matches(Binary(...)): " + binaryPrism.matches(binary));

    // Extract the value from a literal
    Optional<Object> value = literalPrism.andThen(LiteralLenses.value()).getOptional(literal);
    System.out.println("Literal value: " + value);

    // Extract the name from a variable
    Optional<String> name = variablePrism.andThen(VariableLenses.name()).getOptional(variable);
    System.out.println("Variable name: " + name);
    System.out.println();
  }

  private static void basicTransformations() {
    System.out.println("--- Basic Transformations ---\n");

    // x + y
    Expr expr = new Expr.Binary(new Expr.Variable("x"), BinaryOp.ADD, new Expr.Variable("y"));

    System.out.println("Original: " + format(expr));

    // Rename 'x' to 'a' using prism + lens composition
    Prism<Expr, Expr.Variable> variablePrism = Expr.Prisms.variable();
    Lens<Expr.Variable, String> nameLens = VariableLenses.name();

    // This only renames top-level variables (we'll do recursive traversal in Article 3)
    Expr renamed =
        variablePrism.modify(v -> v.name().equals("x") ? new Expr.Variable("a") : v, expr);
    System.out.println("After renaming x→a (top-level only): " + format(renamed));

    // Constant folding preview: 1 + 2 → 3
    Expr foldable = new Expr.Binary(new Expr.Literal(1), BinaryOp.ADD, new Expr.Literal(2));
    System.out.println("\nConstant folding preview:");
    System.out.println("Before: " + format(foldable));

    Prism<Expr, Expr.Binary> binaryPrism = Expr.Prisms.binary();
    Expr folded = binaryPrism.modify(ExpressionPreviewDemo::tryFold, foldable);
    System.out.println("After: " + format(folded));

    System.out.println("\n(Full recursive transformations coming in Article 3!)");
    System.out.println();
  }

  /** Try to fold a binary expression if both operands are integer literals. */
  private static Expr.Binary tryFold(Expr.Binary binary) {
    if (binary.left() instanceof Expr.Literal(Object lv)
        && binary.right() instanceof Expr.Literal(Object rv)
        && lv instanceof Integer li
        && rv instanceof Integer ri) {

      Integer result =
          switch (binary.op()) {
            case ADD -> li + ri;
            case SUB -> li - ri;
            case MUL -> li * ri;
            case DIV -> ri != 0 ? li / ri : null;
            default -> null;
          };

      if (result != null) {
        // Return a "folded" binary that will be recognized as constant
        // (In Article 3, we'll properly return a Literal instead)
        return new Expr.Binary(new Expr.Literal(result), binary.op(), new Expr.Literal(0));
      }
    }
    return binary;
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
