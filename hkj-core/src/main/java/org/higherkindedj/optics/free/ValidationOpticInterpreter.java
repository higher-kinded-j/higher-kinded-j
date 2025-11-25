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
import org.higherkindedj.optics.util.Traversals;
import org.jspecify.annotations.NullMarked;

/**
 * Validation interpreter for optic programs that validates operations during execution.
 *
 * <p>This interpreter executes optic operations while collecting validation warnings and errors.
 * Note that operations are executed to enable proper flatMap chaining in Free programs, but the
 * validation results provide insight into potential issues. This is useful for:
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
   * Validates an optic program by executing it and collecting validation results.
   *
   * <p>The program is executed to enable proper flatMap chaining, while validation checks are
   * performed on SET and MODIFY operations.
   *
   * @param program The Free monad program to validate
   * @param <A> The result type
   * @return A validation result containing any errors and warnings
   */
  @SuppressWarnings("unchecked")
  public <A> ValidationResult validate(Free<OpticOpKind.Witness, A> program) {
    errors.clear();
    warnings.clear();

    // Natural transformation from OpticOp to Id monad
    // We must execute operations to get proper results for flatMap chaining,
    // while also performing validation checks
    Function<Kind<OpticOpKind.Witness, ?>, Kind<IdKind.Witness, ?>> transform =
        kind -> {
          OpticOp<?, ?> op = OpticOpKindHelper.OP.narrow((Kind<OpticOpKind.Witness, Object>) kind);

          // Validate and execute each operation
          // If execution fails, return the source unchanged to allow chaining to continue
          Object result =
              switch (op) {
                case OpticOp.Get<?, ?> get -> executeGet(get);
                case OpticOp.Preview<?, ?> preview -> executePreview(preview);
                case OpticOp.GetAll<?, ?> getAll -> executeGetAll(getAll);
                case OpticOp.Set<?, ?> set -> {
                  validateSet(set);
                  yield safeExecuteSet(set);
                }
                case OpticOp.SetAll<?, ?> setAll -> {
                  validateSetAll(setAll);
                  yield safeExecuteSetAll(setAll);
                }
                case OpticOp.Modify<?, ?> modify -> {
                  validateModify(modify);
                  yield safeExecuteModify(modify);
                }
                case OpticOp.ModifyAll<?, ?> modifyAll -> safeExecuteModifyAll(modifyAll);
                case OpticOp.Exists<?, ?> exists -> executeExists(exists);
                case OpticOp.All<?, ?> all -> executeAll(all);
                case OpticOp.Count<?, ?> count -> executeCount(count);
              };

          return Id.of(Free.pure(result));
        };

    // Execute the program with validation
    program.foldMap(transform, IdMonad.instance());

    return new ValidationResult(
        Collections.unmodifiableList(new ArrayList<>(errors)),
        Collections.unmodifiableList(new ArrayList<>(warnings)));
  }

  // Note: These validate methods don't need @SuppressWarnings("unchecked") because
  // Java's sealed types + switch expressions correctly infer type parameters from
  // the pattern-matched OpticOp<?, ?> cases.

  private <S, A> void validateSet(OpticOp.Set<S, A> op) {
    // Example validation: could check if value is null, out of range, etc.
    if (op.newValue() == null) {
      warnings.add("SET operation with null value: " + op.optic());
    }
  }

  private <S, A> void validateSetAll(OpticOp.SetAll<S, A> op) {
    if (op.newValue() == null) {
      warnings.add("SET_ALL operation with null value: " + op.optic());
    }
  }

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

  // Execute methods - these perform the actual operations to enable proper flatMap chaining

  private <S, A> A executeGet(OpticOp.Get<S, A> op) {
    return op.optic().get(op.source());
  }

  private <S, A> java.util.Optional<A> executePreview(OpticOp.Preview<S, A> op) {
    return op.optic().preview(op.source());
  }

  private <S, A> java.util.List<A> executeGetAll(OpticOp.GetAll<S, A> op) {
    return op.optic().getAll(op.source());
  }

  private <S, A> S safeExecuteSet(OpticOp.Set<S, A> op) {
    try {
      return op.optic().set(op.newValue(), op.source());
    } catch (Exception e) {
      errors.add("SET operation failed: " + op.optic() + " - " + e.getMessage());
      return op.source();
    }
  }

  private <S, A> S safeExecuteSetAll(OpticOp.SetAll<S, A> op) {
    try {
      return Traversals.modify(op.optic(), ignored -> op.newValue(), op.source());
    } catch (Exception e) {
      errors.add("SET_ALL operation failed: " + op.optic() + " - " + e.getMessage());
      return op.source();
    }
  }

  private <S, A> S safeExecuteModify(OpticOp.Modify<S, A> op) {
    try {
      return op.optic().modify(op.modifier(), op.source());
    } catch (Exception e) {
      errors.add("MODIFY operation failed: " + op.optic() + " - " + e.getMessage());
      return op.source();
    }
  }

  private <S, A> S safeExecuteModifyAll(OpticOp.ModifyAll<S, A> op) {
    try {
      return Traversals.modify(op.optic(), op.modifier(), op.source());
    } catch (Exception e) {
      errors.add("MODIFY_ALL operation failed: " + op.optic() + " - " + e.getMessage());
      return op.source();
    }
  }

  private <S, A> Boolean executeExists(OpticOp.Exists<S, A> op) {
    return op.optic().exists(op.predicate(), op.source());
  }

  private <S, A> Boolean executeAll(OpticOp.All<S, A> op) {
    return op.optic().all(op.predicate(), op.source());
  }

  private <S, A> Integer executeCount(OpticOp.Count<S, A> op) {
    return op.optic().length(op.source());
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
