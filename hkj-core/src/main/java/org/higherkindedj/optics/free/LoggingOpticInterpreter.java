// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.optics.util.Traversals;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Logging interpreter for optic programs that records all operations.
 *
 * <p>This interpreter executes optic operations like {@link DirectOpticInterpreter}, but also
 * maintains a log of all operations performed. This is useful for:
 *
 * <ul>
 *   <li>Debugging - See exactly what operations were performed
 *   <li>Audit trails - Track changes for compliance
 *   <li>Testing - Verify expected operations occurred
 * </ul>
 *
 * <p>Example:
 *
 * <pre>{@code
 * Free<OpticOpKind.Witness, Person> program = ...;
 * LoggingOpticInterpreter interpreter = new LoggingOpticInterpreter();
 * Person result = interpreter.run(program);
 *
 * // View the log
 * interpreter.getLog().forEach(System.out::println);
 * // Output:
 * // GET: PersonLenses.age() -> 25
 * // MODIFY: PersonLenses.age() from 25 to 26
 * // SET: PersonLenses.status() <- ADULT
 * }</pre>
 */
@NullMarked
public final class LoggingOpticInterpreter {
  private final List<String> log = new ArrayList<>();

  /**
   * Runs an optic program, logging all operations.
   *
   * @param program The Free monad program to execute
   * @param <A> The result type
   * @return The result of executing the program
   */
  @SuppressWarnings("unchecked")
  public <A> @Nullable A run(Free<OpticOpKind.Witness, A> program) {
    // Natural transformation from OpticOp to Id monad (with logging)
    Function<Kind<OpticOpKind.Witness, ?>, Kind<IdKind.Witness, ?>> transform =
        kind -> {
          OpticOp<?, ?> op = OpticOpKindHelper.OP.narrow((Kind<OpticOpKind.Witness, Object>) kind);

          Object result =
              switch (op) {
                case OpticOp.Get<?, ?> get -> executeGet(get);
                case OpticOp.Preview<?, ?> preview -> executePreview(preview);
                case OpticOp.GetAll<?, ?> getAll -> executeGetAll(getAll);
                case OpticOp.Set<?, ?> set -> executeSet(set);
                case OpticOp.SetAll<?, ?> setAll -> executeSetAll(setAll);
                case OpticOp.Modify<?, ?> modify -> executeModify(modify);
                case OpticOp.ModifyAll<?, ?> modifyAll -> executeModifyAll(modifyAll);
                case OpticOp.Exists<?, ?> exists -> executeExists(exists);
                case OpticOp.All<?, ?> all -> executeAll(all);
                case OpticOp.Count<?, ?> count -> executeCount(count);
              };

          return Id.of(Free.pure(result));
        };

    // Interpret the program using the Id monad
    Kind<IdKind.Witness, A> resultKind = program.foldMap(transform, IdMonad.instance());
    return IdKindHelper.ID.narrow(resultKind).value();
  }

  // Note: These execute methods don't need @SuppressWarnings("unchecked") because
  // Java's sealed types + switch expressions correctly infer type parameters from
  // the pattern-matched OpticOp<?, ?> cases. The type variables S and A are inferred
  // from capture variables, and the method bodies contain no explicit casts.

  private <S, A> A executeGet(OpticOp.Get<S, A> op) {
    A value = op.optic().get(op.source());
    log.add(String.format("GET: %s -> %s", opticName(op.optic()), value));
    return value;
  }

  private <S, A> Optional<A> executePreview(OpticOp.Preview<S, A> op) {
    Optional<A> value = op.optic().preview(op.source());
    log.add(
        String.format(
            "PREVIEW: %s -> %s",
            opticName(op.optic()), value.map(Object::toString).orElse("empty")));
    return value;
  }

  private <S, A> List<A> executeGetAll(OpticOp.GetAll<S, A> op) {
    List<A> values = op.optic().getAll(op.source());
    log.add(String.format("GET_ALL: %s -> %d items", opticName(op.optic()), values.size()));
    return values;
  }

  private <S, A> S executeSet(OpticOp.Set<S, A> op) {
    log.add(String.format("SET: %s <- %s", opticName(op.optic()), op.newValue()));
    return op.optic().set(op.newValue(), op.source());
  }

  private <S, A> S executeSetAll(OpticOp.SetAll<S, A> op) {
    log.add(String.format("SET_ALL: %s <- %s", opticName(op.optic()), op.newValue()));
    return Traversals.modify(op.optic(), ignored -> op.newValue(), op.source());
  }

  private <S, A> S executeModify(OpticOp.Modify<S, A> op) {
    A oldValue = op.optic().get(op.source());
    A newValue = op.modifier().apply(oldValue);
    log.add(String.format("MODIFY: %s from %s to %s", opticName(op.optic()), oldValue, newValue));
    return op.optic().set(newValue, op.source());
  }

  private <S, A> S executeModifyAll(OpticOp.ModifyAll<S, A> op) {
    log.add(String.format("MODIFY_ALL: %s", opticName(op.optic())));
    return Traversals.modify(op.optic(), op.modifier(), op.source());
  }

  private <S, A> Boolean executeExists(OpticOp.Exists<S, A> op) {
    Boolean exists = op.optic().exists(op.predicate(), op.source());
    log.add(String.format("EXISTS: %s -> %s", opticName(op.optic()), exists));
    return exists;
  }

  private <S, A> Boolean executeAll(OpticOp.All<S, A> op) {
    Boolean all = op.optic().all(op.predicate(), op.source());
    log.add(String.format("ALL: %s -> %s", opticName(op.optic()), all));
    return all;
  }

  private <S, A> Integer executeCount(OpticOp.Count<S, A> op) {
    Integer count = op.optic().length(op.source());
    log.add(String.format("COUNT: %s -> %d", opticName(op.optic()), count));
    return count;
  }

  /**
   * Returns an unmodifiable view of the operation log.
   *
   * @return The list of logged operations
   */
  public List<String> getLog() {
    return Collections.unmodifiableList(log);
  }

  /** Clears the operation log. */
  public void clearLog() {
    log.clear();
  }

  /**
   * Extracts a readable name from an optic (best effort).
   *
   * @param optic The optic
   * @return A string representation
   */
  String opticName(Object optic) {
    String className = optic.getClass().getSimpleName();
    if (className.isEmpty()) {
      // Anonymous class - try to get a meaningful name
      className = optic.getClass().getName();
      int lastDot = className.lastIndexOf('.');
      if (lastDot >= 0) {
        className = className.substring(lastDot + 1);
      }
    }
    return className;
  }
}
