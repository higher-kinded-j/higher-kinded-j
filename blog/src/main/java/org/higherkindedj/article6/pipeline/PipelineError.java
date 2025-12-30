// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article6.pipeline;

import java.util.List;
import org.higherkindedj.article5.typecheck.TypeError;
import org.higherkindedj.article6.parse.ParseError;

/**
 * Unified error type for the expression language pipeline.
 *
 * <p>The pipeline can fail at different stages:
 *
 * <ul>
 *   <li>{@link Parse} - syntax errors during parsing
 *   <li>{@link Type} - type errors during type checking
 *   <li>{@link Runtime} - errors during interpretation
 * </ul>
 *
 * <p>Using a sealed interface allows exhaustive pattern matching on error types, following the
 * data-oriented programming style we've developed throughout this series.
 */
public sealed interface PipelineError {

  /** A parsing error. */
  record Parse(ParseError error) implements PipelineError {
    @Override
    public String toString() {
      return "Parsing failed: " + error;
    }
  }

  /** Type checking errors (may contain multiple errors due to error accumulation). */
  record Type(List<TypeError> errors) implements PipelineError {
    @Override
    public String toString() {
      if (errors.size() == 1) {
        return "Type error: " + errors.getFirst().message();
      }
      StringBuilder sb = new StringBuilder("Type errors:\n");
      for (int i = 0; i < errors.size(); i++) {
        sb.append("  ").append(i + 1).append(". ").append(errors.get(i).message());
        if (i < errors.size() - 1) {
          sb.append("\n");
        }
      }
      return sb.toString();
    }
  }

  /** A runtime error during interpretation. */
  record Runtime(String message) implements PipelineError {
    @Override
    public String toString() {
      return "Runtime error: " + message;
    }
  }

  /**
   * Create a pipeline error from a parse error.
   *
   * @param error the parse error
   * @return a PipelineError wrapping the parse error
   */
  static PipelineError fromParseError(ParseError error) {
    return new Parse(error);
  }

  /**
   * Create a pipeline error from type errors.
   *
   * @param errors the list of type errors
   * @return a PipelineError wrapping the type errors
   */
  static PipelineError fromTypeErrors(List<TypeError> errors) {
    return new Type(errors);
  }

  /**
   * Create a pipeline error from a runtime exception.
   *
   * @param message the error message
   * @return a PipelineError for runtime errors
   */
  static PipelineError runtime(String message) {
    return new Runtime(message);
  }
}
