// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import java.util.Objects;
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
 * Direct interpreter for optic programs that executes operations immediately.
 *
 * <p>This is the standard interpreter that performs actual optic operations. It evaluates each
 * instruction in the Free program and produces concrete results.
 *
 * <p>Example:
 *
 * <pre>{@code
 * Free<OpticOpKind.Witness, Person> program = ...;
 * DirectOpticInterpreter interpreter = new DirectOpticInterpreter();
 * Person result = interpreter.run(program);
 * }</pre>
 */
@NullMarked
public final class DirectOpticInterpreter {

  /**
   * Runs an optic program and returns the result.
   *
   * @param program The Free monad program to execute
   * @param <A> The result type
   * @return The result of executing the program
   */
  @SuppressWarnings("unchecked")
  public <A> A run(Free<OpticOpKind.Witness, A> program) {
    // Natural transformation from OpticOp to Id monad
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
    return op.k()
        .apply(Objects.requireNonNull(op.optic().get(op.source()), OpticPrograms.NULL_GET_FOCUS));
  }

  private <S, A, R> R executePreview(OpticOp.Preview<S, A, R> op) {
    return op.k().apply(op.optic().preview(op.source()));
  }

  private <S, A, R> R executeGetAll(OpticOp.GetAll<S, A, R> op) {
    return op.k().apply(op.optic().getAll(op.source()));
  }

  private <S, A, R> R executeSet(OpticOp.Set<S, A, R> op) {
    return op.k().apply(op.optic().set(op.newValue(), op.source()));
  }

  private <S, A, R> R executeSetAll(OpticOp.SetAll<S, A, R> op) {
    return op.k().apply(Traversals.modify(op.optic(), ignored -> op.newValue(), op.source()));
  }

  private <S, A, R> R executeModify(OpticOp.Modify<S, A, R> op) {
    return op.k().apply(op.optic().modify(op.modifier(), op.source()));
  }

  private <S, A, R> R executeModifyAll(OpticOp.ModifyAll<S, A, R> op) {
    return op.k().apply(Traversals.modify(op.optic(), op.modifier(), op.source()));
  }

  private <S, A, R> R executeExists(OpticOp.Exists<S, A, R> op) {
    return op.k().apply(op.optic().exists(op.predicate(), op.source()));
  }

  private <S, A, R> R executeAll(OpticOp.All<S, A, R> op) {
    return op.k().apply(op.optic().all(op.predicate(), op.source()));
  }

  private <S, A, R> R executeCount(OpticOp.Count<S, A, R> op) {
    return op.k().apply(op.optic().length(op.source()));
  }
}
