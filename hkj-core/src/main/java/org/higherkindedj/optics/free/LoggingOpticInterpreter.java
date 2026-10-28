// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
  public <A> A run(Free<OpticOpKind.Witness, A> program) {
    // Natural transformation from OpticOp to Id monad (with logging)
    Function<Kind<OpticOpKind.Witness, ?>, Kind<IdKind.Witness, ?>> transform =
        kind -> {
          OpticOp<?, ?> op = OpticOpKindHelper.OP.narrow((Kind<OpticOpKind.Witness, Object>) kind);

          Object result =
              switch (op) {
                case OpticOp.Get<?, ?, ?> get -> executeGet(get);
                case OpticOp.Preview<?, ?, ?> preview -> executePreview(preview);
                case OpticOp.GetAll<?, ?, ?> getAll -> executeGetAll(getAll);
                case OpticOp.Set<?, ?, ?> set -> executeSet(set);
                case OpticOp.SetAll<?, ?, ?> setAll -> executeSetAll(setAll);
                case OpticOp.Modify<?, ?, ?> modify -> executeModify(modify);
                case OpticOp.ModifyAll<?, ?, ?> modifyAll -> executeModifyAll(modifyAll);
                case OpticOp.Exists<?, ?, ?> exists -> executeExists(exists);
                case OpticOp.All<?, ?, ?> all -> executeAll(all);
                case OpticOp.Count<?, ?, ?> count -> executeCount(count);
              };

          return Id.of(result);
        };

    // Interpret the program using the Id monad
    Kind<IdKind.Witness, A> resultKind = program.foldMap(transform, IdMonad.instance());
    return IdKindHelper.ID.narrow(resultKind).value();
  }

  // Note: These execute methods don't need @SuppressWarnings("unchecked") because
  // Java's sealed types + switch expressions correctly infer type parameters from
  // the pattern-matched OpticOp<?, ?, ?> cases. The type variables S, A and R are inferred
  // from capture variables, and the method bodies contain no explicit casts.
  // Each one hands what its operation produces to the operation's continuation.

  private <S, A, R> R executeGet(OpticOp.Get<S, A, R> op) {
    A value = Objects.requireNonNull(op.optic().get(op.source()), OpticPrograms.NULL_GET_FOCUS);
    log.add(String.format(Locale.ROOT, "GET: %s -> %s", opticName(op.optic()), value));
    return op.k().apply(value);
  }

  private <S, A, R> R executePreview(OpticOp.Preview<S, A, R> op) {
    Optional<A> value = op.optic().preview(op.source());
    log.add(
        String.format(
            Locale.ROOT,
            "PREVIEW: %s -> %s",
            opticName(op.optic()),
            value.map(Object::toString).orElse("empty")));
    return op.k().apply(value);
  }

  private <S, A, R> R executeGetAll(OpticOp.GetAll<S, A, R> op) {
    List<A> values = op.optic().getAll(op.source());
    log.add(
        String.format(
            Locale.ROOT, "GET_ALL: %s -> %d items", opticName(op.optic()), values.size()));
    return op.k().apply(values);
  }

  private <S, A, R> R executeSet(OpticOp.Set<S, A, R> op) {
    log.add(String.format(Locale.ROOT, "SET: %s <- %s", opticName(op.optic()), op.newValue()));
    return op.k().apply(op.optic().set(op.newValue(), op.source()));
  }

  private <S, A, R> R executeSetAll(OpticOp.SetAll<S, A, R> op) {
    log.add(String.format(Locale.ROOT, "SET_ALL: %s <- %s", opticName(op.optic()), op.newValue()));
    return op.k().apply(Traversals.modify(op.optic(), ignored -> op.newValue(), op.source()));
  }

  private <S, A, R> R executeModify(OpticOp.Modify<S, A, R> op) {
    A oldValue = op.optic().get(op.source());
    A newValue = op.modifier().apply(oldValue);
    log.add(
        String.format(
            Locale.ROOT, "MODIFY: %s from %s to %s", opticName(op.optic()), oldValue, newValue));
    return op.k().apply(op.optic().set(newValue, op.source()));
  }

  private <S, A, R> R executeModifyAll(OpticOp.ModifyAll<S, A, R> op) {
    log.add(String.format(Locale.ROOT, "MODIFY_ALL: %s", opticName(op.optic())));
    return op.k().apply(Traversals.modify(op.optic(), op.modifier(), op.source()));
  }

  private <S, A, R> R executeExists(OpticOp.Exists<S, A, R> op) {
    Boolean exists = op.optic().exists(op.predicate(), op.source());
    log.add(String.format(Locale.ROOT, "EXISTS: %s -> %s", opticName(op.optic()), exists));
    return op.k().apply(exists);
  }

  private <S, A, R> R executeAll(OpticOp.All<S, A, R> op) {
    Boolean all = op.optic().all(op.predicate(), op.source());
    log.add(String.format(Locale.ROOT, "ALL: %s -> %s", opticName(op.optic()), all));
    return op.k().apply(all);
  }

  private <S, A, R> R executeCount(OpticOp.Count<S, A, R> op) {
    Integer count = op.optic().length(op.source());
    log.add(String.format(Locale.ROOT, "COUNT: %s -> %d", opticName(op.optic()), count));
    return op.k().apply(count);
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
      // Anonymous class - extract the portion after the last package separator.
      // When lastDot is -1 (no dots), substring(0) returns the full string.
      className = optic.getClass().getName();
      className = className.substring(className.lastIndexOf('.') + 1);
    }
    return className;
  }
}
