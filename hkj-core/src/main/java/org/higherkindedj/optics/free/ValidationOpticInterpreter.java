// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.jspecify.annotations.NullMarked;

/**
 * Validation interpreter for optic programs that checks operations without executing them.
 *
 * <p>This interpreter performs a dry-run of optic operations, collecting warnings and errors
 * without actually modifying data. This is useful for:
 *
 * <ul>
 *   <li>Validating operations before execution
 *   <li>Checking constraints and invariants
 *   <li>Testing program structure
 * </ul>
 *
 * <p>Example:
 *
 * <pre>{@code
 * Free<OpticOpKind.Witness, Person> program = ...;
 * ValidationOpticInterpreter interpreter = new ValidationOpticInterpreter();
 * ValidationResult result = interpreter.validate(program);
 *
 * if (result.isValid()) {
 *     // Safe to execute
 *     Person updated = new DirectOpticInterpreter().run(program);
 * } else {
 *     System.err.println("Validation failed:");
 *     result.errors().forEach(System.err::println);
 * }
 * }</pre>
 */
@NullMarked
public final class ValidationOpticInterpreter {
  private final List<String> errors = new ArrayList<>();
  private final List<String> warnings = new ArrayList<>();

  /**
   * Validates an optic program without executing it.
   *
   * @param program The Free monad program to validate
   * @param <A> The result type
   * @return A validation result containing any errors and warnings
   */
  @SuppressWarnings("unchecked")
  public <A> ValidationResult validate(Free<OpticOpKind.Witness, A> program) {
    errors.clear();
    warnings.clear();

    // Natural transformation from OpticOp to Id monad (validation only)
    Function<Kind<OpticOpKind.Witness, ?>, Kind<IdKind.Witness, ?>> transform =
        kind -> {
          OpticOp<?, ?> op = OpticOpKindHelper.OP.narrow((Kind<OpticOpKind.Witness, Object>) kind);

          // Validate the operation (customize this based on your needs)
          switch (op) {
            case OpticOp.Get<?, ?> ignored -> {
              // Read operations are generally safe
            }
            case OpticOp.Preview<?, ?> ignored -> {
              // Read operations are generally safe
            }
            case OpticOp.GetAll<?, ?> ignored -> {
              // Read operations are generally safe
            }
            case OpticOp.Set<?, ?> set -> validateSet(set);
            case OpticOp.SetAll<?, ?> setAll -> validateSetAll(setAll);
            case OpticOp.Modify<?, ?> modify -> validateModify(modify);
            case OpticOp.ModifyAll<?, ?> ignored -> {
              // Could validate modifier on sample data
            }
            case OpticOp.Exists<?, ?> ignored -> {
              // Query operations are safe
            }
            case OpticOp.All<?, ?> ignored -> {
              // Query operations are safe
            }
            case OpticOp.Count<?, ?> ignored -> {
              // Query operations are safe
            }
          }

          // Return a dummy result (we're not actually executing)
          return Id.of(Free.pure(null));
        };

    // "Execute" the program (but we only validated, didn't modify anything)
    program.foldMap(transform, IdMonad.instance());

    return new ValidationResult(
        Collections.unmodifiableList(new ArrayList<>(errors)),
        Collections.unmodifiableList(new ArrayList<>(warnings)));
  }

  @SuppressWarnings("unchecked")
  private <S, A> void validateSet(OpticOp.Set<S, A> op) {
    // Example validation: could check if value is null, out of range, etc.
    if (op.newValue() == null) {
      warnings.add("SET operation with null value: " + op.optic());
    }
  }

  @SuppressWarnings("unchecked")
  private <S, A> void validateSetAll(OpticOp.SetAll<S, A> op) {
    if (op.newValue() == null) {
      warnings.add("SET_ALL operation with null value: " + op.optic());
    }
  }

  @SuppressWarnings("unchecked")
  private <S, A> void validateModify(OpticOp.Modify<S, A> op) {
    // Could validate that modifier doesn't return null
    try {
      A currentValue = op.optic().get(op.source());
      A newValue = op.modifier().apply(currentValue);
      if (newValue == null) {
        warnings.add("MODIFY operation produced null value: " + op.optic());
      }
    } catch (Exception e) {
      errors.add("MODIFY operation failed: " + op.optic() + " - " + e.getMessage());
    }
  }

  /**
   * Result of validation containing errors and warnings.
   *
   * @param errors List of validation errors (program should not execute if non-empty)
   * @param warnings List of validation warnings (program can execute but be careful)
   */
  public record ValidationResult(List<String> errors, List<String> warnings) {
    /**
     * Checks if the program is valid (no errors).
     *
     * @return true if there are no errors
     */
    public boolean isValid() {
      return errors.isEmpty();
    }

    /**
     * Checks if the program has warnings.
     *
     * @return true if there are warnings
     */
    public boolean hasWarnings() {
      return !warnings.isEmpty();
    }
  }
}
