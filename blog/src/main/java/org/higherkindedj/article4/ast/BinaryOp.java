// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article4.ast;

/** Binary operators for the expression language. */
public enum BinaryOp {
  // Arithmetic
  ADD,
  SUB,
  MUL,
  DIV,

  // Comparison
  EQ,
  NE,
  LT,
  LE,
  GT,
  GE,

  // Logical
  AND,
  OR;

  /** Return a symbolic representation of this operator. */
  public String symbol() {
    return switch (this) {
      case ADD -> "+";
      case SUB -> "-";
      case MUL -> "*";
      case DIV -> "/";
      case EQ -> "==";
      case NE -> "!=";
      case LT -> "<";
      case LE -> "<=";
      case GT -> ">";
      case GE -> ">=";
      case AND -> "&&";
      case OR -> "||";
    };
  }
}
