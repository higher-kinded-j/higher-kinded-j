// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article6.parse;

import org.higherkindedj.article4.ast.BinaryOp;
import org.higherkindedj.article4.ast.Expr;
import org.higherkindedj.article4.ast.Expr.Binary;
import org.higherkindedj.article4.ast.Expr.Conditional;
import org.higherkindedj.article4.ast.Expr.Literal;
import org.higherkindedj.article4.ast.Expr.Variable;
import org.higherkindedj.hkt.either.Either;

/**
 * A simple recursive descent parser for the expression language.
 *
 * <p>This parser produces the {@link Expr} AST from source text. It's intentionally simple,
 * demonstrating the principles without the complexity of a production parser generator.
 *
 * <h2>Grammar (informal)</h2>
 *
 * <pre>
 * expr        → conditional | comparison
 * conditional → "if" expr "then" expr "else" expr
 * comparison  → addition (("==" | "!=" | "<" | "<=" | ">" | ">=") addition)*
 * addition    → multiplication (("+" | "-") multiplication)*
 * multiplication → unary (("*" | "/") unary)*
 * unary       → primary
 * primary     → INTEGER | "true" | "false" | STRING | IDENTIFIER | "(" expr ")"
 * </pre>
 *
 * <p>For production use, consider parser combinators or a generator like ANTLR. This hand-written
 * parser is suitable for small languages and learning purposes.
 *
 * @see Expr
 * @see ParseError
 */
public final class ExprParser {

  private final String input;
  private int pos = 0;

  /**
   * Create a parser for the given input.
   *
   * @param input the source text to parse
   */
  public ExprParser(String input) {
    this.input = input;
  }

  /**
   * Parse the input and return either the resulting expression or a parse error.
   *
   * @return Either.right(expr) on success, Either.left(error) on failure
   */
  public Either<ParseError, Expr> parse() {
    try {
      skipWhitespace();
      Expr result = parseExpr();
      skipWhitespace();
      if (pos < input.length()) {
        return Either.left(ParseError.at("Unexpected input after expression", pos));
      }
      return Either.right(result);
    } catch (ParseException e) {
      return Either.left(ParseError.at(e.getMessage(), e.position));
    }
  }

  // ========== Expression parsing ==========

  private Expr parseExpr() {
    return parseConditional();
  }

  private Expr parseConditional() {
    skipWhitespace();
    if (match("if")) {
      skipWhitespace();
      Expr condition = parseExpr();
      skipWhitespace();
      expect("then");
      skipWhitespace();
      Expr thenBranch = parseExpr();
      skipWhitespace();
      expect("else");
      skipWhitespace();
      Expr elseBranch = parseExpr();
      return new Conditional(condition, thenBranch, elseBranch);
    }
    return parseComparison();
  }

  private Expr parseComparison() {
    Expr left = parseAddition();
    skipWhitespace();

    while (true) {
      BinaryOp op = null;
      if (match("==")) {
        op = BinaryOp.EQ;
      } else if (match("!=")) {
        op = BinaryOp.NE;
      } else if (match("<=")) {
        op = BinaryOp.LE;
      } else if (match(">=")) {
        op = BinaryOp.GE;
      } else if (match("<")) {
        op = BinaryOp.LT;
      } else if (match(">")) {
        op = BinaryOp.GT;
      }

      if (op == null) {
        break;
      }

      skipWhitespace();
      Expr right = parseAddition();
      left = new Binary(left, op, right);
      skipWhitespace();
    }

    return left;
  }

  private Expr parseAddition() {
    Expr left = parseMultiplication();
    skipWhitespace();

    while (true) {
      BinaryOp op = null;
      if (match("+")) {
        op = BinaryOp.ADD;
      } else if (match("-")) {
        op = BinaryOp.SUB;
      }

      if (op == null) {
        break;
      }

      skipWhitespace();
      Expr right = parseMultiplication();
      left = new Binary(left, op, right);
      skipWhitespace();
    }

    return left;
  }

  private Expr parseMultiplication() {
    Expr left = parsePrimary();
    skipWhitespace();

    while (true) {
      BinaryOp op = null;
      if (match("*")) {
        op = BinaryOp.MUL;
      } else if (match("/")) {
        op = BinaryOp.DIV;
      }

      if (op == null) {
        break;
      }

      skipWhitespace();
      Expr right = parsePrimary();
      left = new Binary(left, op, right);
      skipWhitespace();
    }

    return left;
  }

  private Expr parsePrimary() {
    skipWhitespace();

    // Parenthesised expression
    if (match("(")) {
      skipWhitespace();
      Expr expr = parseExpr();
      skipWhitespace();
      expect(")");
      return expr;
    }

    // Boolean literals
    if (match("true")) {
      return new Literal(true);
    }
    if (match("false")) {
      return new Literal(false);
    }

    // String literals
    if (peek() == '"') {
      return parseStringLiteral();
    }

    // Integer literals
    if (Character.isDigit(peek())) {
      return parseIntegerLiteral();
    }

    // Identifiers (variables)
    if (Character.isLetter(peek()) || peek() == '_') {
      return parseVariable();
    }

    throw new ParseException("Expected expression", pos);
  }

  private Expr parseStringLiteral() {
    expect("\"");
    StringBuilder sb = new StringBuilder();
    while (pos < input.length() && peek() != '"') {
      if (peek() == '\\' && pos + 1 < input.length()) {
        pos++;
        char escaped = input.charAt(pos++);
        switch (escaped) {
          case 'n' -> sb.append('\n');
          case 't' -> sb.append('\t');
          case '"' -> sb.append('"');
          case '\\' -> sb.append('\\');
          default -> {
            sb.append('\\');
            sb.append(escaped);
          }
        }
      } else {
        sb.append(input.charAt(pos++));
      }
    }
    expect("\"");
    return new Literal(sb.toString());
  }

  private Expr parseIntegerLiteral() {
    int start = pos;
    while (pos < input.length() && Character.isDigit(peek())) {
      pos++;
    }
    String numStr = input.substring(start, pos);
    try {
      return new Literal(Integer.parseInt(numStr));
    } catch (NumberFormatException e) {
      throw new ParseException("Invalid integer: " + numStr, start);
    }
  }

  private Expr parseVariable() {
    int start = pos;
    while (pos < input.length() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
      pos++;
    }
    String name = input.substring(start, pos);
    return new Variable(name);
  }

  // ========== Utilities ==========

  private void skipWhitespace() {
    while (pos < input.length() && Character.isWhitespace(peek())) {
      pos++;
    }
  }

  private char peek() {
    return pos < input.length() ? input.charAt(pos) : '\0';
  }

  private boolean match(String expected) {
    if (input.startsWith(expected, pos)) {
      // For keywords, ensure we're not matching a prefix of an identifier
      if (Character.isLetter(expected.charAt(0))) {
        int endPos = pos + expected.length();
        if (endPos < input.length() && Character.isLetterOrDigit(input.charAt(endPos))) {
          return false;
        }
      }
      pos += expected.length();
      return true;
    }
    return false;
  }

  private void expect(String expected) {
    if (!match(expected)) {
      throw new ParseException("Expected '" + expected + "'", pos);
    }
  }

  /** Internal exception for parse errors (converted to Either at the boundary). */
  private static class ParseException extends RuntimeException {
    final int position;

    ParseException(String message, int position) {
      super(message);
      this.position = position;
    }
  }
}
