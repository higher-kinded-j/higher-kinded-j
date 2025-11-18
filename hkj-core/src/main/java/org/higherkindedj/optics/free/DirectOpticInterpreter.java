// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

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
  public <A> @Nullable A run(Free<OpticOpKind.Witness, A> program) {
    // Natural transformation from OpticOp to Id monad
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

  @SuppressWarnings("unchecked")
  private <S, A> A executeGet(OpticOp.Get<S, A> op) {
    return op.optic().get(op.source());
  }

  @SuppressWarnings("unchecked")
  private <S, A> java.util.Optional<A> executePreview(OpticOp.Preview<S, A> op) {
    return op.optic().preview(op.source());
  }

  @SuppressWarnings("unchecked")
  private <S, A> java.util.List<A> executeGetAll(OpticOp.GetAll<S, A> op) {
    return op.optic().getAll(op.source());
  }

  @SuppressWarnings("unchecked")
  private <S, A> S executeSet(OpticOp.Set<S, A> op) {
    return op.optic().set(op.newValue(), op.source());
  }

  @SuppressWarnings("unchecked")
  private <S, A> S executeSetAll(OpticOp.SetAll<S, A> op) {
    return Traversals.modify(op.optic(), ignored -> op.newValue(), op.source());
  }

  @SuppressWarnings("unchecked")
  private <S, A> S executeModify(OpticOp.Modify<S, A> op) {
    return op.optic().modify(op.modifier(), op.source());
  }

  @SuppressWarnings("unchecked")
  private <S, A> S executeModifyAll(OpticOp.ModifyAll<S, A> op) {
    return Traversals.modify(op.optic(), op.modifier(), op.source());
  }

  @SuppressWarnings("unchecked")
  private <S, A> Boolean executeExists(OpticOp.Exists<S, A> op) {
    return op.optic().exists(op.predicate(), op.source());
  }

  @SuppressWarnings("unchecked")
  private <S, A> Boolean executeAll(OpticOp.All<S, A> op) {
    return op.optic().all(op.predicate(), op.source());
  }

  @SuppressWarnings("unchecked")
  private <S, A> Integer executeCount(OpticOp.Count<S, A> op) {
    return op.optic().length(op.source());
  }
}
