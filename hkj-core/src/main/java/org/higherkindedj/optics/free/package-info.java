// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Free Monad DSL for advanced optic operations.
 *
 * <p>This package provides a Free monad-based DSL for building optic programs as composable data
 * structures that can be interpreted in different ways. This enables powerful capabilities beyond
 * simple optic operations.
 *
 * <h2>Quick Start</h2>
 *
 * <pre>{@code
 * // Build a program
 * Free<OpticOpKind.Witness, Person> program =
 *     OpticPrograms.get(person, PersonLenses.age())
 *         .flatMap(age -> {
 *             if (age < 18) {
 *                 return OpticPrograms.pure(person);  // No changes
 *             } else {
 *                 return OpticPrograms.modify(person, PersonLenses.age(), a -> a + 1)
 *                     .flatMap(p1 ->
 *                         OpticPrograms.set(p1, PersonLenses.status(), "ADULT"));
 *             }
 *         });
 *
 * // Execute it
 * Person result = OpticInterpreters.direct().run(program);
 * }</pre>
 *
 * <h2>Key Benefits</h2>
 *
 * <ol>
 *   <li><b>Multiple Interpreters</b> - Execute the same program different ways:
 *       <ul>
 *         <li>{@link DirectOpticInterpreter} - Standard execution
 *         <li>{@link LoggingOpticInterpreter} - Execution with audit trail
 *         <li>{@link ValidationOpticInterpreter} - Dry-run validation
 *       </ul>
 *   <li><b>Program Composition</b> - Build complex workflows from simple pieces using {@code
 *       flatMap}
 *   <li><b>Conditional Logic</b> - Programs can branch based on optic reads
 *   <li><b>Testability</b> - Mock interpreters for pure testing
 *   <li><b>Future Extensibility</b> - Add optimization, transaction support, etc.
 * </ol>
 *
 * <h2>Example: Logging Interpreter</h2>
 *
 * <pre>{@code
 * Free<OpticOpKind.Witness, Person> program =
 *     OpticPrograms.modify(person, ageLens, age -> age + 1)
 *         .flatMap(p -> OpticPrograms.set(p, statusLens, "ADULT"));
 *
 * LoggingOpticInterpreter logger = OpticInterpreters.logging();
 * Person result = logger.run(program);
 *
 * logger.getLog().forEach(System.out::println);
 * // Output:
 * // MODIFY: ... from 25 to 26
 * // SET: ... <- ADULT
 * }</pre>
 *
 * <h2>Example: Validation Before Execution</h2>
 *
 * <pre>{@code
 * Free<OpticOpKind.Witness, Person> program = ...;
 *
 * ValidationOpticInterpreter validator = OpticInterpreters.validating();
 * ValidationResult validation = validator.validate(program);
 *
 * if (validation.isValid()) {
 *     Person result = OpticInterpreters.direct().run(program);
 * } else {
 *     System.err.println("Validation failed: " + validation.errors());
 * }
 * }</pre>
 *
 * <h2>When to Use</h2>
 *
 * <p>Use the Free monad DSL when you need:
 *
 * <ul>
 *   <li>Complex multi-step optic workflows with conditional logic
 *   <li>Audit trails of what operations were performed
 *   <li>Validation before modification
 *   <li>Multiple execution strategies for the same program
 *   <li>Testing complex optic logic without real data
 * </ul>
 *
 * <p>For simple operations, use {@link org.higherkindedj.optics.fluent.OpticOps} instead.
 *
 * <h2>Architecture</h2>
 *
 * <ul>
 *   <li>{@link OpticOp} - Instruction set (sealed interface of all operations)
 *   <li>{@link OpticPrograms} - DSL for building programs
 *   <li>{@link OpticInterpreters} - Factory for creating interpreters
 *   <li>{@link DirectOpticInterpreter} - Standard execution
 *   <li>{@link LoggingOpticInterpreter} - Execution with logging
 *   <li>{@link ValidationOpticInterpreter} - Validation without execution
 * </ul>
 *
 * <h2>Future Enhancements</h2>
 *
 * <p>Potential future interpreters could include:
 *
 * <ul>
 *   <li><b>OptimizingInterpreter</b> - Fuses consecutive operations for efficiency
 *   <li><b>TransactionalInterpreter</b> - All-or-nothing execution with rollback
 *   <li><b>ParallelInterpreter</b> - Executes independent operations concurrently
 *   <li><b>ReplayInterpreter</b> - Records and replays operations for debugging
 * </ul>
 *
 * @see org.higherkindedj.optics.free.OpticPrograms
 * @see org.higherkindedj.optics.free.OpticInterpreters
 */
package org.higherkindedj.optics.free;
