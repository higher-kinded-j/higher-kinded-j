// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import org.jspecify.annotations.NullMarked;

/**
 * Facade providing convenient access to optic program interpreters.
 *
 * <p>This class provides factory methods for creating different interpreters for Free monad optic
 * programs. Each interpreter executes the same program in a different way.
 *
 * <h2>Available Interpreters</h2>
 *
 * <ul>
 *   <li><b>Direct</b> - Standard execution with actual optic operations
 *   <li><b>Logging</b> - Execution with operation logging for audit trails
 *   <li><b>Validation</b> - Dry-run validation without execution
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Free<OpticOpKind.Witness, Person> program = ...;
 *
 * // Production: direct execution
 * Person result = OpticInterpreters.direct().run(program);
 *
 * // Debugging: execute with logging
 * LoggingOpticInterpreter logger = OpticInterpreters.logging();
 * Person result = logger.run(program);
 * logger.getLog().forEach(System.out::println);
 *
 * // Validation: check before executing
 * ValidationOpticInterpreter.ValidationResult validation =
 *     OpticInterpreters.validating().validate(program);
 * if (validation.isValid()) {
 *     Person result = OpticInterpreters.direct().run(program);
 * }
 * }</pre>
 */
@NullMarked
public final class OpticInterpreters {

  private OpticInterpreters() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Creates a direct interpreter that executes operations immediately.
   *
   * <p>This is the standard interpreter for production use.
   *
   * @return A new {@link DirectOpticInterpreter}
   */
  public static DirectOpticInterpreter direct() {
    return new DirectOpticInterpreter();
  }

  /**
   * Creates a logging interpreter that records all operations.
   *
   * <p>Use this for debugging, audit trails, or testing.
   *
   * @return A new {@link LoggingOpticInterpreter}
   */
  public static LoggingOpticInterpreter logging() {
    return new LoggingOpticInterpreter();
  }

  /**
   * Creates a validation interpreter that checks operations without executing them.
   *
   * <p>Use this to validate programs before execution.
   *
   * @return A new {@link ValidationOpticInterpreter}
   */
  public static ValidationOpticInterpreter validating() {
    return new ValidationOpticInterpreter();
  }
}
