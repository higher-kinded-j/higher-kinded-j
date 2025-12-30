// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article6.parse;

/**
 * Represents a parsing error.
 *
 * <p>Used by {@link ExprParser} to report syntax errors when parsing fails.
 *
 * @param message the error message describing what went wrong
 * @param position the character position where the error occurred
 */
public record ParseError(String message, int position) {

  /**
   * Create a parse error with just a message (position unknown).
   *
   * @param message the error message
   * @return a new ParseError
   */
  public static ParseError of(String message) {
    return new ParseError(message, -1);
  }

  /**
   * Create a parse error with message and position.
   *
   * @param message the error message
   * @param position the character position
   * @return a new ParseError
   */
  public static ParseError at(String message, int position) {
    return new ParseError(message, position);
  }

  @Override
  public String toString() {
    if (position >= 0) {
      return "Parse error at position %d: %s".formatted(position, message);
    }
    return "Parse error: " + message;
  }
}
