// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article3.ast;

import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;

/**
 * The expression AST for the expression language.
 *
 * <p>This is a minimal 4-variant AST that covers:
 *
 * <ul>
 *   <li>{@link Literal} — constant values (integers, booleans, strings)
 *   <li>{@link Variable} — variable references
 *   <li>{@link Binary} — binary operations (arithmetic, comparison, logical)
 *   <li>{@link Conditional} — if-then-else expressions
 * </ul>
 *
 * <p>The {@code @GeneratePrisms} annotation on the sealed interface generates prism accessors for
 * each variant, enabling type-safe pattern matching without explicit instanceof checks.
 *
 * <p>The {@code @GenerateLenses} annotation on each record generates lens accessors for each field,
 * enabling composable, immutable updates to nested structures.
 *
 * <p>The {@code @GenerateFocus} annotation generates Focus DSL classes that wrap lenses in fluent
 * path types for elegant navigation chains.
 */
@GeneratePrisms
public sealed interface Expr {

  /** A literal value (integer, boolean, or string). */
  @GenerateLenses
  @GenerateFocus
  record Literal(Object value) implements Expr {}

  /** A variable reference. */
  @GenerateLenses
  @GenerateFocus
  record Variable(String name) implements Expr {}

  /** A binary operation. */
  @GenerateLenses
  @GenerateFocus
  record Binary(Expr left, BinaryOp op, Expr right) implements Expr {}

  /** A conditional (if-then-else) expression. */
  @GenerateLenses
  @GenerateFocus
  record Conditional(Expr cond, Expr thenBranch, Expr elseBranch) implements Expr {}

  // ========== Formatting ==========

  /** Format this expression as a string. */
  default String format() {
    return switch (this) {
      case Literal(var v) -> formatLiteral(v);
      case Variable(var n) -> n;
      case Binary(var l, var op, var r) ->
          "(" + l.format() + " " + op.symbol() + " " + r.format() + ")";
      case Conditional(var c, var t, var e) ->
          "(if " + c.format() + " then " + t.format() + " else " + e.format() + ")";
    };
  }

  private static String formatLiteral(Object value) {
    if (value instanceof String s) {
      return "\"" + s + "\"";
    }
    return String.valueOf(value);
  }
}
